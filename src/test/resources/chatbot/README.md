# FAQ 검색 평가

`faq-search-evaluation.tsv`는 FAQ ID 1~25별 원문과 평가 질문 세 개를 저장한다.
각 행의 질문 유형은 `paraphrase`, `colloquial`, `different`이며 총 75문항이다.
`faq-search-oos.txt`는 FAQ로 답할 수 없는 질문 20개이다.
모두 사람이 작성한 초기 평가용 질문이며 실제 학생 로그에서 추출한 데이터는 아니다.

일반 `gradlew.bat test`에서는 live/evaluation 테스트를 skip한다.
전체 애플리케이션을 로드하는 기존 테스트도 Seeder와 OpenAIClient를 mock 처리한다.
데이터 구성 검증과 pgvector 통합 테스트는 OpenAI를 사용하지 않는다.

## 수동 평가

루트 `.env`의 DB 접속 정보와 `OPENAI_API_KEY`, 기존 FAQ 25개가 필요하다.
API 호출 전에 DB의 ID·원문·활성 여부·1536차원 embedding을 확인한다.
평가 자체는 기존 `FaqSearchService.search`를 질문마다 호출하므로 정상 실행 시 총 95개의 embedding 요청이 발생한다.
Seeder는 실행하지 않고 DB 스키마와 데이터는 수정하지 않는다.

```powershell
$env:FAQ_SEARCH_EVALUATION_TEST = 'true'
try {
    .\gradlew.bat test --tests 'com.irummate.chatbot.FaqSearchEvaluationTest' --console=plain --info
} finally {
    Remove-Item Env:FAQ_SEARCH_EVALUATION_TEST
}
```

다시 실행할 때 Gradle이 `UP-TO-DATE`로 판단하면 `--rerun-tasks`를 명시한다.
재실행은 API 비용을 다시 발생시키므로 필요할 때만 한다.

## 결과 파일

`build/reports/faq-search-evaluation/`에 저장되며 다음 수동 평가 시 덮어쓴다.
일반 테스트 실행은 이 파일들을 지우지 않는다. `gradlew clean`은 build 폴더를 지운다.

- `evaluation.log`: 모든 질문의 Top-3, 거리, 정답 여부와 요약. OOS는 Top-1을 기록한다.
- `in-domain.tsv`: 질문·유형·정답 ID·Top-1 ID·Top-3 ID 목록·Top-1 거리·정답 거리·Top-1 정답 여부·Top-3 적중 여부.
- `oos.tsv`: 질문·Top-1 ID·Top-1 FAQ 질문·Top-1 거리.
- `summary.txt`: Top-1 Accuracy, Recall@3, 거리 평균·최소·최대·중앙값, Top-1 오답 목록.

정답 거리는 정답 FAQ가 Top-3에 있는 경우만 기록하며 없으면 `null`이다.
`Recall@3`는 질문마다 정답 FAQ가 하나인 이 데이터에서 Top-3 적중률이다.
OOS 결과에는 정답/오답 판정을 붙이지 않으며 거리 분포만 수집한다.
정확도 하한이나 거리 threshold에 따른 통과/실패 조건은 없다.

이 데이터로 threshold를 조정한 뒤에는 별도 질문으로 재평가해야 한다.

운영 TODO: 서버 시작 시 `DormitoryFaqSeeder` 자동 실행을 제거하고 one-time ingestion으로 분리한다.
이번 평가 작업에서는 운영 Seeder를 변경하지 않는다.

## Q+A / Question-only A/B 실험

기존 75개 in-domain 및 20개 OOS 질문을 그대로 사용한다.
A는 현재 DB의 Q+A embedding을 읽고, B는 FAQ `question` 원문만 embedding한다.
두 방식에 동일한 cached query 벡터를 전달하고 같은 Java L2 계산으로 Top-3를 구한다.
동점은 FAQ ID 오름차순으로 정렬한다. 실제 pgvector `<->`와 두 query의 Top-3 및 거리를
비교하며 허용 오차는 `0.000001`이다. FAQ embedding은 실행 전후 DB 지문으로도 비교한다.

```powershell
$env:FAQ_SEARCH_AB_EVALUATION_TEST = 'true'
try {
    .\gradlew.bat test --tests 'com.irummate.chatbot.FaqEmbeddingAbEvaluationTest' --console=plain --info
} finally {
    Remove-Item Env:FAQ_SEARCH_AB_EVALUATION_TEST
}
```

캐시는 이미 Git에서 제외된 `.gradle/faq-search-evaluation-cache/` 아래에 저장한다.
`gradlew clean`은 이 디렉터리를 지우지 않는다. `.gradle`을 직접 지우면 캐시도 없어진다.

- `queries/`: 95개 평가 질문 embedding
- `faq-question-only/`: FAQ question-only embedding 25개
- 파일명: `SHA-256(model + NUL + 질문 원문).bin`
- 형식: Java DataOutputStream의 버전·모델명·질문 원문(각 writeUTF), 차원(writeInt), 1536개의 float32(writeFloat)

모델명·입력 문자열·차원·유한한 숫자 여부를 읽을 때 검증한다.
정상 캐시는 그대로 재사용하고, 없거나 손상된 항목만 재생성한다.
각 API 결과는 즉시 파일에 저장하여 중간에 실패해도 완료된 요청은 재사용할 수 있다.
질문 변경 시 새 키가 생기며 기존 캐시를 모두 삭제할 필요는 없다.

빈 캐시의 예상 API 호출은 query 95 + FAQ 25 = 120회이다.
캐시가 전부 유효하면 호출은 0회이다. 로그에 두 그룹의 hit·호출·무효 항목 수를 출력한다.
호출 수는 EmbeddingService 호출 기준이며 SDK 내부 HTTP 재시도는 별도로 세지 않는다.
평가 마지막에는 새 캐시 인스턴스로 120개 벡터를 다시 읽고, 이때 cache miss가 나면
API 대신 실패하도록 하여 디스크 재사용과 API 호출 0회를 검증한다.
일반 테스트는 A/B 테스트도 skip한다. 캐시/L2 단위 테스트는 API를 호출하지 않는다.

결과는 `build/reports/faq-search-ab-evaluation/`에 별도로 저장한다.
기존 `build/reports/faq-search-evaluation/` 보고서는 변경하지 않는다.

- `summary.txt`: A/B 지표·거리 통계·캐시 사용량·개선/악화/공통 오답 목록
- `in-domain.tsv`: 같은 질문의 A/B Top-1·Top-3 ID·거리·정답 여부·정답 FAQ 거리
- `oos.tsv`: 같은 질문의 A/B Top-1 FAQ·거리
- `evaluation.log`: 질문별 A/B Top-3 상세와 전체 요약

이 실험은 production embedding을 바꾸지 않는다. 기존 baseline 숫자를 하드코딩하지 않고
A도 같은 query cache로 다시 계산한다. threshold와 LLM 답변 생성은 적용하지 않는다.

## Small / Large 모델 비교

두 모델 모두 FAQ 입력은 정확히 `"질문: " + question + "\n답변: " + answer`이다.
Query도 동일한 원문을 각 모델로 embedding한다. Small query와 large FAQ를 섞지 않는다.
SDK 4.101.0의 `EmbeddingCreateParams.Builder.dimensions(long)`을 사용하여
small과 large 요청 모두 `.dimensions(1536L)`을 지정한다. 응답도 1536차원인지 검증한다.
기존 small cache는 기본 출력 차원이 이미 1536이므로 재사용한다.

```powershell
$env:FAQ_EMBEDDING_MODEL_EVALUATION_TEST = 'true'
try {
    .\gradlew.bat test --tests 'com.irummate.chatbot.FaqEmbeddingModelEvaluationTest' --console=plain --info
} finally {
    Remove-Item Env:FAQ_EMBEDDING_MODEL_EVALUATION_TEST
}
```

캐시는 같은 `.gradle/faq-search-evaluation-cache/`를 사용한다.
키는 **model + NUL + 입력 원문**이고 차원은 파일 메타데이터로 검증한다.
현재 평가 도구는 1536차원만 허용하며, 다른 차원의 벡터를 저장하거나 계산하지 않는다.
모델명이 다른 small/large 항목은 서로 다른 파일이 된다.

- `queries/`: 모델별 query 95개. 기존 small 파일은 유지하고 large 파일만 추가한다.
- `faq-question-answer/`: 모델별 FAQ Q+A 25개.
- `faq-question-only/`: 이전 실험의 캐시를 그대로 보존하며 이번 모델 비교에는 사용하지 않는다.

Small FAQ는 현재 DB 벡터가 baseline이다. API를 호출하지 않고 Q+A 문자열을 키로 캐시를
준비한 뒤 DB 값과 동일한지 확인한다. 최초에는 로컬 파일 25개를 생성하므로 로그의
`seeded/refreshed from DB`도 확인한다. 이후 같은 DB 벡터이면 파일을 재작성하지 않는다.

기존 small query cache가 유효한 현재 상태에서는 small API 호출은 0회,
large 최초 호출은 query 95 + FAQ Q+A 25 = 120회가 예상된다.
모든 캐시가 유효한 재실행은 두 모델 모두 0회이다.
평가 마지막에는 API를 차단한 채 두 모델의 캐시를 새 인스턴스로 다시 읽어 재사용을 검증한다.

모델 비교는 기존 비교 테스트의 평가 루프·L2 계산·지표·보고서 출력을 재사용한다.
Java L2와 pgvector의 비교 및 DB embedding 지문 검증도 유지한다.
결과는 `build/reports/faq-embedding-model-evaluation/`의 `summary.txt`,
`in-domain.tsv`, `oos.tsv`, `evaluation.log`에 저장한다. A는 small, B는 large이다.
기존 평가 보고서 두 디렉터리는 변경하지 않는다.

일반 테스트에서는 이 실험도 skip한다. 운영 모델, DB embedding, Seeder 및 threshold는 변경하지 않는다.

## 현재 검색 baseline 및 FAQ selector

현재 운영 검색 구현은 `text-embedding-3-large`, 명시적 `dimensions=1536`, FAQ Q+A, L2 Top-3이다.
위 small/large 비교 설명은 과거 실험에 해당한다. 기존 small FAQ Q+A 캐시는 DB가 large로
바뀐 뒤에도 덮어쓰지 않는다. 과거 실험에서 cache miss가 나면 지정된 small 모델로만
생성하며 production `EmbeddingService`를 호출해 small/large가 섞이지 않도록 한다.
현재 DB 벡터와 같은 모델일 때 pgvector 교차 검증을 수행한다.

`FaqSelectionService.select(question, candidates)`는 Responses API의 typed Structured Output으로
`NONE`, `CANDIDATE_1`, `CANDIDATE_2`, `CANDIDATE_3` 중 하나를 받는다.
후보의 질문·답변만 전달하고 DB ID와 L2 거리는 전달하지 않는다.
정상 NONE은 Optional.empty이며 API 실패, 거절, 불완전/비정상 출력, 없는 후보 선택은 예외다.
기본 selector 모델은 `gpt-6-luna`이며 `.env`의 `OPENAI_FAQ_SELECTOR_MODEL`로 변경할 수 있다.
선택한 FAQ로 최종 답변을 생성하는 단계는 구현하지 않는다.

### 개발 DB Large reindex — 실제 DB 갱신

기존 FAQ를 잠그고 large Q+A 캐시를 우선 사용한다. 캐시가 없는 FAQ만 embedding API를 호출한다.
기존 embedding은 `.gradle/faq-search-evaluation-cache/reindex-backups/`에 TSV로 백업한다.
FAQ 25개, null 없음, 1536차원, 메타데이터 유지, 실제 pgvector wifi FAQ 9를 검증하고
모두 성공한 경우에만 TransactionTemplate이 commit한다. 예외/검증 실패는 rollback한다.
서버 startup이나 일반 테스트에서는 실행하지 않는다.

```powershell
$env:FAQ_EMBEDDING_REINDEX_TEST = 'true'
try {
    .\gradlew.bat test --tests 'com.irummate.chatbot.FaqEmbeddingReindexTest' --console=plain --info
} finally {
    Remove-Item Env:FAQ_EMBEDDING_REINDEX_TEST
}
```

요약은 `build/reports/faq-embedding-reindex/summary.txt`에 저장한다.

### 실제 검색 + Selector live 확인

실제 embedding 2회와 selector 2회를 사용한다. Wifi는 FAQ 9, 등록금은 NONE을 기대한다.

```powershell
$env:FAQ_SELECTOR_LIVE_TEST = 'true'
try {
    .\gradlew.bat test --tests 'com.irummate.chatbot.FaqSelectorLiveTest' --console=plain --info
} finally {
    Remove-Item Env:FAQ_SELECTOR_LIVE_TEST
}
```

### Selector 95문항 평가

기존 large query cache 95개와 FAQ Q+A cache 25개를 사용한다.
DB FAQ embedding이 large cache와 동일한지 먼저 확인한다.
Embedding API는 차단되어 cache가 없으면 요청을 보내지 않고 실패한다.
정상 실행 시 selector만 95회 호출한다. SDK 내부 재시도는 별도로 세지 않는다.

```powershell
$env:FAQ_SELECTOR_EVALUATION_TEST = 'true'
try {
    .\gradlew.bat test --tests 'com.irummate.chatbot.FaqSelectorEvaluationTest' --console=plain --info
} finally {
    Remove-Item Env:FAQ_SELECTOR_EVALUATION_TEST
}
```

`build/reports/faq-selector-evaluation/`에 summary.txt, in-domain.tsv, oos.tsv, evaluation.log를 저장한다.
raw Top-1, selector 정확도, NONE/오답 수, 개선/악화, OOS 거절률 및 전체 정확도를 출력한다.
API 오류를 NONE으로 집계하지 않으며 해당 실행을 실패시킨다. Threshold는 사용하지 않는다.

모든 manual 환경변수가 없는 일반 테스트에서는 재색인·live·평가를 모두 skip하고 API를 호출하지 않는다.
운영 전 Seeder 자동 실행을 one-time ingestion으로 분리하는 TODO는 유지한다.

### FAQ REST API

`POST /api/chatbot/ask`에 `Authorization: Bearer <accessToken>`과
`Content-Type: application/json`을 보낸다. 요청 본문은 `{"question":"기숙사 통금 있어?"}`이며,
정상 응답과 FAQ 범위 밖 응답은 모두 HTTP 200의 `data.answer`에 들어 있다.
빈 질문과 500자 초과 질문은 HTTP 400, 인증 실패는 기존 JWT 정책에 따라 HTTP 401이다.

운영 전에는 사용자별 요청 횟수 제한을 설계한다. FAQ 일회성 적재와 변경 항목만 재임베딩하는 흐름을 마련하고,
행정실 승인 후 신규 FAQ를 추가해 검색과 Selector를 다시 평가한다. 프론트엔드 연동과 E2E 검증도 남아 있다.
