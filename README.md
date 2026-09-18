# irummate (이룸매이트)

> 설문 기반 라이프스타일 유사도 매칭으로 기숙사 룸메이트를 찾아주는 서비스

irummate는 학생들이 생활 습관 설문에 응답하면, 응답을 벡터로 변환해 가장 잘 맞는 룸메이트 후보를 추천하는 서비스입니다. 기숙사 인증을 통과한 재학생만 참여할 수 있어 신뢰할 수 있는 매칭 환경을 제공합니다.

본 저장소는 **백엔드 서버**입니다.

---

## 목차

- [주요 기능](#주요-기능)
- [기술 스택](#기술-스택)
- [시스템 아키텍처](#시스템-아키텍처)
- [매칭 알고리즘](#매칭-알고리즘)
- [인증 및 인가 구조](#인증-및-인가-구조)
- [프로젝트 구조](#프로젝트-구조)
- [시작하기](#시작하기)
- [API 개요](#api-개요)
- [배포](#배포)

---

## 주요 기능

| 기능 | 설명 |
| --- | --- |
| 카카오 소셜 로그인 | 카카오 OAuth 인증 후 JWT 액세스·리프레시 토큰 발급 |
| 기숙사 인증 | S3 presigned URL로 인증 사진을 업로드하고 관리자가 승인·반려 |
| 생활 습관 설문 | 9개 문항 응답을 정규화된 9차원 라이프스타일 벡터로 변환 |
| 룸메이트 매칭 | pgvector 기반 유사도 검색으로 후보 3명 추천, 하루 1회 제한 |
| 상호 수락 및 채팅 | 양측이 수락하면 채팅방이 개설되고 WebSocket으로 실시간 대화 |
| 관리자 기능 | 인증 심사, 회원 조회 및 정지, 매칭 기간 설정 |

---

## 기술 스택

**언어 및 프레임워크**

- Java 21
- Spring Boot 3.5.11
- Spring Security, Spring Data JPA, Spring WebSocket(STOMP)
- Spring AOP, Bean Validation

**데이터베이스**

- PostgreSQL + [pgvector](https://github.com/pgvector/pgvector) (벡터 유사도 검색)
- Hibernate Vector, QueryDSL

**인증 및 보안**

- 카카오 OAuth 2.0
- JWT (jjwt)
- Hashids (외부 노출용 ID 난독화)

**인프라**

- AWS EC2, RDS(PostgreSQL), S3
- Docker, Docker Compose, Nginx
- GitHub Actions + GHCR + AWS SSM

**문서화**

- SpringDoc OpenAPI (Swagger UI)

---

## 시스템 아키텍처

```
                  ┌──────────────┐     ┌──────────────────┐     ┌──────────┐
                  │ GitHub main  │ ──▶ │  GitHub Actions  │ ──▶ │   GHCR   │
                  └──────────────┘     └──────────────────┘     └────┬─────┘
                                                                     │
                                                    SSM 배포 · 이미지 pull
                                                                     │
   ┌──────────┐    ╔═══════════════ AWS Cloud (ap-northeast-2) ══════╪═════════╗
   │          │    ║  ┌────────────── VPC ─────────────────────────┐ │         ║
   │  사용자   │ ──▶║  │  ┌─── EC2 (t3.medium) ───────┐            │ ▼         ║
   │  앱 · 웹  │    ║  │  │  ┌─────────────────────┐  │   ┌──────────────────┐ ║
   │          │    ║  │  │  │ Nginx (프록시·TLS)   │  │   │  RDS PostgreSQL  │ ║
   └──────────┘    ║  │  │  └──────────┬──────────┘  │   │ pgvector·t4g.small│ ║
                   ║  │  │  ┌──────────▼──────────┐  │   └──────────────────┘ ║
                   ║  │  │  │ Spring Boot (Docker)│──┼──▶          ▲          ║
                   ║  │  │  └──────────┬──────────┘  │             │          ║
                   ║  │  └─────────────┼─────────────┘             │          ║
                   ║  └────────────────┼───────────────────────────┼──────────┘
                   ║      ┌────────────▼────────────┐                          ║
                   ║      │  S3 (기숙사 인증 사진)    │                          ║
                   ║      └─────────────────────────┘                          ║
                   ╚═══════════════════════════════════════════════════════════╝
```

- EC2 단일 인스턴스에서 Nginx와 Spring Boot 컨테이너가 Docker Compose로 실행됩니다.
- 애플리케이션 포트(8080)는 `127.0.0.1`에만 바인딩되어 외부에서 직접 접근할 수 없습니다.
- 배포는 SSH가 아닌 **AWS SSM Run Command**로 이루어져 22번 포트를 열지 않습니다.
- S3는 VPC 밖의 리전 단위 서비스로, presigned URL을 통해 클라이언트가 직접 업로드합니다.

---

## 매칭 알고리즘

### 1. 설문 응답을 벡터로 변환

사용자가 9개 문항(취침 시간, 코골이, 잠꼬대, 정리 습관, 실내 취식, 온도 선호, 샤워 빈도, 스피커 사용, 통화 습관)에 응답하면, 각 값을 `[0, 1]` 구간으로 정규화하여 9차원 벡터로 저장합니다. 변환은 엔티티 저장 시점에 자동으로 수행됩니다.

```
answers (jsonb) ──▶ normalize ──▶ lifestyle_vector (vector(9))
```

### 2. 후보 검색

매칭 요청이 들어오면 다음 순서로 후보를 확보합니다.

1. **기존 추천 재활용** — 이미 발급했지만 아직 응답하지 않은 후보를 우선 조회
2. **신규 추천** — 부족한 인원만큼 pgvector 유사도 검색으로 보충

신규 추천 쿼리는 동일 성별, 설문 완료, 미매칭 상태의 사용자 중 이미 매칭 이력이 있는 상대를 제외한 뒤, L2 거리(`<->`) 기준으로 가장 가까운 3명을 선택합니다. 흡연 여부가 일치하는 후보를 우선 검색하고, 인원이 부족하면 흡연 조건을 완화해 재검색합니다.

### 3. 매칭 요청 생성 및 확정

선택된 후보에 대해 `match_requests` 레코드를 생성하며, 이때 계산된 **매칭률을 컬럼에 스냅샷으로 저장**합니다. 양측이 모두 수락하면 채팅방이 개설되고 연락처가 공개됩니다.

> **참고:** 설문 수정은 매칭 시작일 이전까지만 허용됩니다. 매칭 시작 이후에는 이미 발급된 매칭률과 실제 벡터가 어긋나지 않도록 수정이 제한됩니다.

### 4. 리롤 제한

`rerolled_at` 컬럼을 통해 하루 1회만 새로운 추천을 받을 수 있도록 제한합니다.

---

## 인증 및 인가 구조

요청은 두 단계로 검증됩니다.

**1단계 — Spring Security 필터 체인**

`JwtAuthenticationFilter`가 `UsernamePasswordAuthenticationFilter` 앞에서 동작하며, Bearer 토큰을 검증하고 `userId`를 SecurityContext에 저장합니다. 세션을 사용하지 않는 stateless 구성입니다.

**2단계 — AOP 기반 메서드 단위 가드**

`ValidationAspect`가 아래 어노테이션을 감지해 비즈니스 조건을 사전 검증합니다.

| 어노테이션 | 검증 내용 |
| --- | --- |
| `@RequiresAuth(roles = ...)` | 계정 상태 및 권한(GUEST / USER / ADMIN) 확인 |
| `@RequiresCertification` | 기숙사 인증 완료(ACTIVE) 여부 확인 |
| `@RequiresSurvey` | 설문 작성 완료 여부 확인 |
| `@RequiresMatchDate` | 현재 날짜가 매칭 기간 내인지 확인 |

이 구조 덕분에 컨트롤러 메서드에 어노테이션을 선언하는 것만으로 접근 조건을 조합할 수 있습니다.

---

## 프로젝트 구조

도메인 중심(Domain-Driven) 패키지 구조를 따릅니다.

```
src/main/java/com/irummate/
├── domain/
│   ├── auth/            # 카카오 OAuth, 토큰 발급 및 재발급
│   ├── user/            # 회원 프로필, 상세 정보, 탈퇴
│   ├── certification/   # 기숙사 인증 제출 및 조회
│   ├── survey/          # 설문 등록·조회·수정, 벡터 변환
│   ├── matching/        # 매칭 알고리즘, 매칭 요청, 매칭 기간 설정
│   ├── chat/            # 채팅방, 메시지, WebSocket
│   └── admin/           # 인증 심사, 회원 관리
│       └── (각 도메인) controller / service / repository / entity / dto / util
└── global/
    ├── aop/             # 권한·상태 검증 어노테이션 및 Aspect
    ├── jwt/             # 토큰 생성·검증 필터
    ├── s3/              # presigned URL 발급, 객체 삭제
    ├── config/          # Security, WebSocket, Swagger 설정
    ├── exception/       # 공통 예외 및 전역 핸들러
    ├── response/        # 표준 응답 포맷
    └── util/            # Hashids 등 공통 유틸
```

---

## 시작하기

### 사전 요구사항

- JDK 21
- Docker / Docker Compose
- PostgreSQL 16+ (pgvector 확장 필요)

### 1. 저장소 클론

```bash
git clone https://github.com/ALOC-dev/5th-project-yulgok-server-2026.git
cd 5th-project-yulgok-server-2026
```

### 2. 환경 변수 설정

프로젝트 루트에 `.env` 파일을 생성합니다. (`spring.config.import`로 자동 로드됩니다.)

```dotenv
# Database
DB_URL=jdbc:postgresql://localhost:5432/irummate
DB_USERNAME=your_username
DB_PASSWORD=your_password

# Kakao OAuth
KAKAO_CLIENT_ID=your_client_id
KAKAO_CLIENT_SECRET=your_client_secret
KAKAO_REDIRECT_URI=http://localhost:8080/api/auth/kakao/callback
KAKAO_ADMIN_KEY=your_admin_key

# JWT
JWT_SECRET=your_jwt_secret_key
JWT_ACCESS_TOKEN_EXPIRATION=3600000
JWT_REFRESH_TOKEN_EXPIRATION=1209600000

# Hashids
HASHIDS_SALT=your_salt
HASHIDS_MIN_LENGTH=8
```

> AWS 자격 증명은 EC2 인스턴스 프로파일 또는 로컬 AWS CLI 설정을 통해 주입됩니다.
> S3 버킷과 리전은 `application.properties`에 정의되어 있습니다.

### 3. 데이터베이스 준비

pgvector 확장을 활성화합니다.

```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

`spring.jpa.hibernate.ddl-auto=validate` 설정이므로 애플리케이션이 테이블을 생성하지 않습니다. 스키마가 미리 준비되어 있어야 합니다.

### 4. 실행

```bash
# 로컬 실행
./gradlew bootRun

# 또는 Docker로 실행
docker compose -f docker-compose.prod.yml up -d --build
```

### 5. API 문서 확인

애플리케이션 실행 후 Swagger UI에서 전체 API를 확인할 수 있습니다.

```
http://localhost:8080/swagger-ui/index.html
```

---

## API 개요

모든 응답은 `GlobalApiResponse` 포맷으로 통일되어 있으며, 예외는 `GlobalExceptionController`에서 일괄 처리됩니다.

### 인증 `/api/auth`

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| GET | `/kakao/callback` | 카카오 로그인 콜백, 토큰 발급 |
| POST | `/refresh` | 액세스 토큰 재발급 |
| POST | `/logout` | 로그아웃 |
| GET | `/status` | 로그인 상태 조회 |

### 회원 `/api/users`

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| GET | `/me` | 내 프로필 조회 |
| PATCH | `/me` | 프로필 수정 |
| POST | `/details` | 상세 정보 등록 |
| PATCH | `/details` | 상세 정보 수정 |
| DELETE | `/me` | 회원 탈퇴 |

### 기숙사 인증 `/api/certifications`

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| GET | `/me` | 최근 인증 상태 조회 |
| POST | `/presigned-url` | 업로드용 presigned URL 발급 |
| POST | `/` | 인증 제출 |

### 설문 `/api/surveys`

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| POST | `/` | 설문 제출 |
| GET | `/me` | 내 설문 조회 |
| PATCH | `/me` | 설문 수정 (매칭 시작 전까지) |

### 매칭 `/api/matching`

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| GET | `/status` | 매칭 현황 조회 |
| POST | `/match` | 매칭 후보 추천 (하루 1회) |
| PATCH | `/requests` | 하트 전송 또는 거절 |
| PATCH | `/requests/confirm` | 매칭 확정 |
| GET | `/requests/{receiverId}/contact` | 확정된 상대 연락처 조회 |

### 채팅 `/api/chat`

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| GET | `/rooms` | 채팅방 목록 |
| GET | `/rooms/{roomId}/messages` | 메시지 목록 |
| PATCH | `/rooms/{roomId}/read` | 읽음 처리 |
| GET | `/unread-count` | 안 읽은 메시지 수 |

실시간 메시지 전송은 WebSocket(STOMP)의 `/chat/send` 목적지를 사용합니다.

### 관리자 `/api/admin`

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| GET | `/certifications` | 인증 목록 조회 (페이지당 15건) |
| GET | `/certifications/{id}` | 인증 상세 조회 (이미지 URL 포함) |
| PATCH | `/certifications/{id}/approve` | 인증 승인 |
| PATCH | `/certifications/{id}/reject` | 인증 반려 |
| GET | `/users` | 회원 목록 조회 |
| GET | `/users/{userId}` | 회원 상세 조회 |
| PATCH | `/users/{userId}/ban` | 회원 정지 |
| PATCH | `/users/{userId}/unban` | 정지 해제 |
| GET | `/match/config` | 매칭 기간 조회 |
| PATCH | `/match/config` | 매칭 기간 설정 |

---

## 배포

`main` 브랜치에 푸시되면 GitHub Actions가 자동으로 배포를 수행합니다.

```
push (main)
   └─▶ Gradle 빌드
        └─▶ Docker 이미지 빌드 및 GHCR 푸시 (latest, commit SHA)
             └─▶ AWS SSM Run Command로 EC2에 배포
                  └─▶ 이미지 pull 및 컨테이너 재시작
```

**필요한 GitHub Secrets**

| 이름 | 설명 |
| --- | --- |
| `AWS_ACCESS_KEY_ID` | 배포용 IAM 사용자 액세스 키 |
| `AWS_SECRET_ACCESS_KEY` | 배포용 IAM 사용자 시크릿 키 |
| `EC2_INSTANCE_ID` | 대상 EC2 인스턴스 ID |

SSH 접속 없이 SSM을 통해 배포하므로 보안 그룹에 22번 포트를 열어둘 필요가 없습니다.

---

## 라이선스

<!-- 라이선스 정책을 결정한 뒤 작성해 주세요. -->

## 팀

<!-- 팀원 및 역할을 작성해 주세요. -->
