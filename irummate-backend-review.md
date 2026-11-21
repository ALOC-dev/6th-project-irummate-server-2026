# 이룸매이트 설문·매칭·모집 게시판 수정 체크리스트

2026-11-20 기준 · `feature/recruitment-api`

이전 코드 리뷰의 후속 작업은 **Blocking 5건, Non-blocking 16건**이다. 운영 DB 실행 계획과 동시 부하 수치는 검증 전이다. 아래 완료 조건은 수정 후 확인할 사항이다.

**확정 후 탈퇴 정책(B1)** — 양쪽이 최종 확정한 뒤 한쪽이 탈퇴해도 남은 사용자의 매칭은 종료 상태로 두며, 해당 회차에서 재매칭을 허용하지 않는다.

## 🚨 Blocking 이슈

우선 탈퇴·차단으로 바뀐 사용자 상태를 매칭과 모집에 반영한다. 인증 오류 분류와 설문 동시 제출은 각각 독립적으로 수정할 수 있다. 다음 학기 매칭을 열기 전에 기존 DB의 매칭 데이터를 초기화한다.

### B2. 작성자·참여자 탈퇴 및 작성자 제재를 모집글에 반영

- **문제점** — 작성자가 탈퇴해도 공개된 모집글에 다른 사용자가 참여할 수 있다. 참여자가 탈퇴하면 `JOINED`와 인원 수가 남아 만석 상태와 공동구매 예상 금액이 실제 인원과 달라진다.
- **수정 방향** — 진행 중인 작성 글에는 취소·숨김 정책을 적용한다. 일반 참여자는 모집글을 잠근 뒤 `JOINED` 기록을 종료하고 인원을 한 번만 줄인다. 작성자 제재에도 공개·참여 가능 여부를 반영한다.
- [ ] **완료 조건** — 작성자 탈퇴·제재 후 신규 참여가 거절된다. 일반 참여자 탈퇴 후 인원과 예상 금액이 맞고, 정원 마감 글은 기존 재개 조건을 충족할 때만 다시 열린다.
- **위치** — [UserDetailsService.java 194행](src/main/java/com/irummate/domain/user/service/UserDetailsService.java#L194) · [AdminUserService.java 99행](src/main/java/com/irummate/domain/admin/service/AdminUserService.java#L99) · [RecruitmentParticipationService.java 62행](src/main/java/com/irummate/domain/recruitment/service/RecruitmentParticipationService.java#L62) · [Recruitments.java 302행](src/main/java/com/irummate/domain/recruitment/entity/Recruitments.java#L302)

### B3. 차단 관계를 추천·카드·하트·확정에 적용

- **문제점** — 차단 등록과 양방향 검증 함수는 있지만 매칭에 연결되지 않았다. 차단 전에 저장한 후보나 카드가 남고, 상호 하트로 채팅방까지 생성될 수 있다.
- **수정 방향** — 신규 후보, 저장된 후보, 카드 조회에 양방향 차단 제외 조건을 적용한다. 하트·확정 시점에도 `validateNotBlocked(userId, receiverId)`를 호출한다. 확정 후 차단 시 연락처 공개 정책도 결정한다.
- [ ] **완료 조건** — 어느 쪽이 차단해도 신규 추천과 기존 카드에서 제외된다. 차단 전 발급된 카드로 하트·확정을 요청해도 상태가 바뀌지 않으며 연락처 조회는 합의한 정책을 따른다.
- **위치** — [UserBlockService.java 76행](src/main/java/com/irummate/domain/block/service/UserBlockService.java#L76) · [UserPreferencesRepository.java 54행](src/main/java/com/irummate/domain/survey/repository/UserPreferencesRepository.java#L54) · [MatchRepository.java 203행](src/main/java/com/irummate/domain/matching/repository/MatchRepository.java#L203) · [MatchingService.java 138행](src/main/java/com/irummate/domain/matching/service/MatchingService.java#L138) · [MatchingService.java 163행](src/main/java/com/irummate/domain/matching/service/MatchingService.java#L163)

### B4. 인증 필터에서 DB 장애와 인증 만료를 구분

- **문제점** — 인증 필터가 사용자 조회 중 발생한 런타임 예외까지 `401`로 응답한다. DB 연결 풀이 포화되면 정상 사용자의 클라이언트가 토큰을 갱신하고 재요청한 끝에 인증 만료로 처리할 수 있다.
- **수정 방향** — 토큰 검증과 사용자 조회의 예외 처리 범위를 분리한다. 일시적인 DB 연결 실패는 `503`과 `Retry-After`로 응답한다.
- [ ] **완료 조건** — DB 연결 실패를 주입하면 인증된 요청이 503을 받고 클라이언트는 토큰을 보존한다. 만료 토큰은 기존 401 갱신 흐름을 따른다.
- **위치** — [JwtAuthenticationFilter.java 54행](src/main/java/com/irummate/global/jwt/JwtAuthenticationFilter.java#L54) · [JwtAuthenticationFilter.java 68행](src/main/java/com/irummate/global/jwt/JwtAuthenticationFilter.java#L68) · [application.properties 20행](src/main/resources/application.properties#L20) · [client-api.js 101행](../client/src/api/client-api.js#L101)

### B5. 설문 최초 동시 제출에서 중복 응답 보장

- **상태** — 잠금 조회를 적용했다. 동시 제출 완료 조건은 PostgreSQL에서 확인한다.
- **문제점** — 두 요청이 존재 확인을 동시에 통과하면 한 INSERT가 사용자 PK 제약에 걸린다. 데이터는 한 건만 남지만 두 번째 요청이 명세의 409 대신 500으로 끝날 수 있다.
- **수정 방향** — `saveSurveyStatus()`의 첫 사용자 조회를 기존 `usersRepository.findByIdForUpdate(userId)`로 교체한다. 같은 트랜잭션에서 잠금을 얻은 뒤 설문 존재 여부를 검사한다. 이미 제출했다면 기존 `BusinessException(ErrorCode.SURVEY_ALREADY_SUBMITTED)`가 `409 Conflict`로 응답한다.
- [ ] **완료 조건** — 동일 사용자의 최초 제출 두 건을 별도 트랜잭션에서 동시에 처리했을 때 201 한 건과 409 한 건을 반환하고 설문은 한 건만 남는다.
- **위치** — [SurveyService.java 45행](src/main/java/com/irummate/domain/survey/service/SurveyService.java#L45) · [UserPreferences.java 27행](src/main/java/com/irummate/domain/survey/entity/UserPreferences.java#L27) · [GlobalExceptionController.java 76행](src/main/java/com/irummate/global/exception/GlobalExceptionController.java#L76)

### B6. 다음 학기 오픈 전 매칭 데이터 초기화

- **문제점** — 같은 DB에서 다음 학기를 열면 지난 학기 확정자는 `isMatched` 때문에 추천을 받을 수 없다. 과거에 거절된 사용자 쌍도 이미 존재하는 관계로 분류된다. 다음 회차에 이전 상태가 영향을 주는 경우의 배포 차단 항목이다.
- **처리 방향** — 학기 전환 시 매칭과 채팅을 닫고 백업을 확보한 뒤 `match_requests`, `chat_rooms`, `chat_messages`를 초기화한다. 설문 답변과 완료 여부는 보존하고 `is_matched`와 `rerolled_at`만 초기화한다. 다음 학기 일정은 관리자 기능에서 설정한다.
- [ ] **완료 조건** — 계정·기숙사 인증·게시판·설문을 보존한다. 지난 학기 추천·채팅 기록이 비워지고, 새 학기 인증을 마친 사용자가 다시 매칭에 참여할 수 있다.
- **위치** — [MatchingConfigService.java 42행](src/main/java/com/irummate/domain/matching/service/MatchingConfigService.java#L42) · [MatchRequests.java 16행](src/main/java/com/irummate/domain/matching/entity/MatchRequests.java#L16) · [UserPreferencesRepository.java 68행](src/main/java/com/irummate/domain/survey/repository/UserPreferencesRepository.java#L68) · [MatchingService.java 344행](src/main/java/com/irummate/domain/matching/service/MatchingService.java#L344)

## ⚠️ Non-blocking 이슈

### 트래픽과 DB 처리량

- [ ] **N1. 공용 IP 요청 제한** — 기숙사 공유망에서는 여러 사용자가 IP당 초당 10요청과 burst 30을 공유한다. 초과 시 기본 503이 DB 장애와 겹친다.
  - **개선·검증** — 공유망 피크 부하로 한도를 검증한다. `limit_req_status 429`를 적용하고 추천 실행처럼 비용이 큰 작업은 사용자별로 제한한다.
  - **위치** — [app.conf 1행](nginx/conf.d/app.conf#L1) · [app.conf 56행](nginx/conf.d/app.conf#L56)

- [ ] **N2. 마감·종료 배치 적체** — 한 인스턴스에서 마감과 종료를 각각 분당 최대 50건 처리한다. 동시 마감 500건에는 최소 10회의 마감 작업이 필요하다.
  - **개선·검증** — 실행 시간 예산 안에서 50건 단위로 반복하고 묶음마다 커밋한다. 500건 만료 시 처리 시간과 가장 오래 대기한 글의 지연을 확인한다.
  - **위치** — [RecruitmentScheduler.java 17행](src/main/java/com/irummate/domain/recruitment/scheduler/RecruitmentScheduler.java#L17) · [RecruitmentLifecycleService.java 17행](src/main/java/com/irummate/domain/recruitment/service/RecruitmentLifecycleService.java#L17)

- [ ] **N3. L2 후보 검색 비용** — 한 요청에 엄격한 조건과 흡연 조건을 완화한 검색이 연달아 수행될 수 있다. `LIMIT 10`은 거리 계산 대상 수까지 제한하지 않는다.
  - **개선·검증** — 운영과 비슷한 학기 초·후반 데이터로 두 쿼리의 `EXPLAIN (ANALYZE, BUFFERS)`를 비교한다. 운영 DB 인덱스를 확인한 후 필터 인덱스나 벡터 인덱스의 비용·추천 품질을 평가한다.
  - **위치** — [UserPreferencesRepository.java 44행](src/main/java/com/irummate/domain/survey/repository/UserPreferencesRepository.java#L44) · [UserPreferencesRepository.java 88행](src/main/java/com/irummate/domain/survey/repository/UserPreferencesRepository.java#L88) · [MatchingService.java 393행](src/main/java/com/irummate/domain/matching/service/MatchingService.java#L393)

- [ ] **N4. 후보 없음과 일시 경합 구분** — 적합한 후보가 없는 경우와 조회한 후보 모두를 경합으로 건너뛴 경우가 같은 오류다. 즉시 재시도하면 후보 탐색을 다시 수행한다.
  - **개선·검증** — 경합과 후보 고갈을 구분하고 재시도 지연을 적용한다. 짧은 시간의 연속 요청 비용과 신규 후보 유입 뒤 재추천까지의 시간을 확인한다.
  - **위치** — [MatchingService.java 481행](src/main/java/com/irummate/domain/matching/service/MatchingService.java#L481) · [MatchingService.java 513행](src/main/java/com/irummate/domain/matching/service/MatchingService.java#L513)

- [ ] **N5. 매칭 카드와 확정 시 채팅방 전체 조회** — 카드 목록에 상한이 없고 받은 하트는 일일 추천 수와 별개로 누적된다. 최종 확정은 두 사용자의 채팅방 이력을 각각 읽어 처리한다.
  - **개선·검증** — 카드는 커서와 응답에 필요한 컬럼만 조회한다. 채팅방은 확정 방을 제외한 열린 방만 일괄 갱신한다. 이력이 많은 사용자의 응답 크기와 확정 잠금 시간을 측정한다.
  - **위치** — [MatchRepository.java 365행](src/main/java/com/irummate/domain/matching/repository/MatchRepository.java#L365) · [ChatService.java 262행](src/main/java/com/irummate/domain/chat/service/ChatService.java#L262)

- [ ] **N6. 모집 목록·배치 인덱스** — 최신 목록, 마감 임박 목록, 일괄 종료는 필터와 정렬 기준이 다르다. 종료 글이 누적되면 반환 건수에 비해 많은 행을 읽을 수 있다.
  - **적용** — 최신순 목록에 `(visibility, created_at DESC, id DESC)` 복합 인덱스를 엔티티에 추가했다.
  - **개선·검증** — 카테고리 필터 유무에 따른 실행 계획을 확인한다. 마감 임박 목록과 배치는 데이터가 쌓이면 실행 계획을 보고 인덱스 추가 여부를 판단한다.
  - **위치** — [RecruitmentsRepository.java 53행](src/main/java/com/irummate/domain/recruitment/repository/RecruitmentsRepository.java#L53) · [RecruitmentsRepository.java 87행](src/main/java/com/irummate/domain/recruitment/repository/RecruitmentsRepository.java#L87) · [RecruitmentsRepository.java 119행](src/main/java/com/irummate/domain/recruitment/repository/RecruitmentsRepository.java#L119)

- [ ] **N7. 참여 이력과 참여자 명단의 페이지 범위** — 내 이력은 `Page`의 본문만 사용해 불필요한 전체 건수 조회와 깊은 OFFSET 비용이 생길 수 있다. 글별 참여자 명단에는 페이지 제한이 없다.
  - **개선·검증** — 내 이력은 `(joinedAt, id)` 커서나 `Slice`로 조회한다. 명단에는 정원 상한 또는 페이지 제한을 둔다. 페이지 경계와 큰 명단의 응답 크기를 검증한다.
  - **위치** — [RecruitmentParticipantsRepository.java 35행](src/main/java/com/irummate/domain/recruitment/repository/RecruitmentParticipantsRepository.java#L35) · [RecruitmentParticipationService.java 177행](src/main/java/com/irummate/domain/recruitment/service/RecruitmentParticipationService.java#L177) · [RecruitmentParticipationService.java 298행](src/main/java/com/irummate/domain/recruitment/service/RecruitmentParticipationService.java#L298)

### 상태 정합성과 API 계약

- [ ] **N8. 여러 서버 간 일정 캐시 동기화** — 매칭 일정은 각 프로세스의 Caffeine에 30분 동안 남는다. 두 인스턴스에서 일정 변경 직후 서로 다른 기준으로 설문·추천을 허용할 수 있다.
  - **개선·검증** — 수평 확장 시 설정을 DB에서 직접 읽거나 커밋 후 모든 인스턴스의 캐시를 무효화한다. A에서 일정을 바꾼 직후 B가 같은 일정을 적용하는지 확인한다.
  - **위치** — [MatchingPeriodProvider.java 22행](src/main/java/com/irummate/domain/matching/service/MatchingPeriodProvider.java#L22) · [MatchingConfigService.java 25행](src/main/java/com/irummate/domain/matching/service/MatchingConfigService.java#L25) · [application.properties 68행](src/main/resources/application.properties#L68)

- [ ] **N9. 모집 인원 수와 명단의 조회 시점** — 인원 수와 명단을 별도 SQL로 읽어 중간에 가입이 커밋되면 한 응답의 숫자와 명단 길이가 달라질 수 있다.
  - **개선·검증** — 전체 명단을 반환하는 동안은 같은 결과의 `participants.size()`를 사용한다. 페이지 도입 후에는 같은 스냅샷의 전체 수를 사용한다. 가입·탈퇴가 겹친 조회로 확인한다.
  - **위치** — [RecruitmentParticipationService.java 289행](src/main/java/com/irummate/domain/recruitment/service/RecruitmentParticipationService.java#L289) · [RecruitmentParticipationService.java 298행](src/main/java/com/irummate/domain/recruitment/service/RecruitmentParticipationService.java#L298) · [RecruitmentParticipationService.java 313행](src/main/java/com/irummate/domain/recruitment/service/RecruitmentParticipationService.java#L313)

- [ ] **N10. 모집글 작성 재시도 시 중복 생성** — 모바일에서 성공 응답을 잃은 뒤 같은 요청을 재전송하면 모집글과 호스트 참여 기록이 다시 생긴다.
  - **개선·검증** — 작성 요청에 `Idempotency-Key`를 도입하고 `(author_id, key)`를 유일하게 관리한다. 같은 요청의 재전송은 같은 글 ID를, 별도 작성 요청은 새 ID를 반환해야 한다.
  - **위치** — [RecruitmentService.java 153행](src/main/java/com/irummate/domain/recruitment/service/RecruitmentService.java#L153) · [RecruitmentService.java 172행](src/main/java/com/irummate/domain/recruitment/service/RecruitmentService.java#L172)

- [ ] **N11. 모집 정원의 요청·응답 의미 통일** — `maxParticipants`는 요청에서 호스트 제외, 응답에서 호스트 포함이다. 3명을 모집한 글의 응답 값 4를 수정 폼이 그대로 전송하면 정원이 5로 늘어난다.
  - **개선·검증** — `additionalParticipants`처럼 의미를 구분하거나 포함 기준을 통일한다. 기존 클라이언트 호환을 고려하고, 상세 조회 후 다른 필드만 수정해도 정원이 보존되는지 확인한다.
  - **위치** — [RecruitmentService.java 190행](src/main/java/com/irummate/domain/recruitment/service/RecruitmentService.java#L190) · [RecruitmentService.java 261행](src/main/java/com/irummate/domain/recruitment/service/RecruitmentService.java#L261) · [RecruitmentDetailResponseDto.java 98행](src/main/java/com/irummate/domain/recruitment/dto/RecruitmentDetailResponseDto.java#L98)

- [ ] **N12. 최종 추천 점수와 후보 추출 순서** — 기존 후보 10명 이상이면 신규 후보 탐색을 생략한다. 신규 검색도 L2 상위 10명을 먼저 뽑고 중요 항목 가중치를 더한다. 전체 사용자 중 최종 점수가 높은 3명과 결과가 달라질 수 있다.
  - **개선·검증** — 후보 풀 내 상위 3명이 목표면 명세에 적는다. 전체 순위가 목표면 후보 추출 범위와 가중치 적용 순서를 검토한다. 신규 고득점 후보와 동점 후보로 정책을 검증한다.
  - **위치** — [MatchingService.java 373행](src/main/java/com/irummate/domain/matching/service/MatchingService.java#L373) · [MatchingService.java 383행](src/main/java/com/irummate/domain/matching/service/MatchingService.java#L383) · [MatchingService.java 616행](src/main/java/com/irummate/domain/matching/service/MatchingService.java#L616) · [UserPreferencesRepository.java 74행](src/main/java/com/irummate/domain/survey/repository/UserPreferencesRepository.java#L74)

- [ ] **N13. 인증 만료 후 카드와 액션 일치** — 상대 인증이 만료된 뒤에도 카드가 노출되지만 하트·확정은 실패한다.
  - **개선·검증** — 카드 조회에 유효성 조건을 적용하거나 카드에 액션 불가 상태를 표시한다. 추천 직후 상대 인증을 만료시킨 뒤 조회와 액션의 결과를 대조한다.
  - **위치** — [MatchRepository.java 375행](src/main/java/com/irummate/domain/matching/repository/MatchRepository.java#L375) · [MatchingService.java 169행](src/main/java/com/irummate/domain/matching/service/MatchingService.java#L169) · [MatchingService.java 703행](src/main/java/com/irummate/domain/matching/service/MatchingService.java#L703)

- [ ] **N14. 마감 시각 경과 후 수동 마감** — 배치 실행 전 `OPEN`인 글을 작성자가 닫으면 이미 마감·활동 시각이 지나도 사유가 `MANUAL`로 기록될 수 있다.
  - **개선·검증** — 수동 마감에 시각 조건을 적용한다. 마감 직전·정각·활동 시작 이후에 배치 실행 순서를 바꿔도 합의한 상태와 사유가 나오는지 확인한다.
  - **위치** — [Recruitments.java 324행](src/main/java/com/irummate/domain/recruitment/entity/Recruitments.java#L324) · [Recruitments.java 337행](src/main/java/com/irummate/domain/recruitment/entity/Recruitments.java#L337)

- [ ] **N15. 오래 열린 수정 화면의 덮어쓰기** — 행 잠금은 동시 쓰기를 직렬화하지만 오래된 화면에서 나중에 저장하는 수정까지 감지하지 못한다. 설문은 답변 전체를 교체한다.
  - **개선·검증** — 클라이언트가 읽은 버전이나 `If-Match`를 받는다. 서로 다른 탭에서 같은 버전을 편집하면 두 번째 저장이 409 또는 412를 반환하고 이전 수정이 보존되는지 확인한다.
  - **위치** — [SurveyService.java 71행](src/main/java/com/irummate/domain/survey/service/SurveyService.java#L71) · [SurveyService.java 79행](src/main/java/com/irummate/domain/survey/service/SurveyService.java#L79) · [RecruitmentService.java 233행](src/main/java/com/irummate/domain/recruitment/service/RecruitmentService.java#L233)

- [ ] **N16. 기존 완료 설문의 데이터 점검** — 설문 완료 여부는 `is_completed`에 저장된다. 정상 제출은 선호 항목을 검증하고 벡터를 생성한다. 과거·이관 데이터에서는 `is_completed = true`인 행의 `visible_profile_fields` 또는 `lifestyle_vector`에 NULL이 있는지 확인한다. 실제 결측 건수는 미확인이다.
  - **개선·검증** — 결측이 있는 경우에 한해 답변에서 벡터를 재생성하거나 재설문 대상으로 분류한다. 정리 후 조회·추천을 검증하고 DB 제약 적용 여부를 판단한다.
  - **위치** — [UserPreferences.java 52행](src/main/java/com/irummate/domain/survey/entity/UserPreferences.java#L52) · [UserPreferences.java 59행](src/main/java/com/irummate/domain/survey/entity/UserPreferences.java#L59) · [MatchingService.java 298행](src/main/java/com/irummate/domain/matching/service/MatchingService.java#L298) · [MatchingService.java 616행](src/main/java/com/irummate/domain/matching/service/MatchingService.java#L616)
