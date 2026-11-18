# FAQ Edge Case 평가 실행

평가 데이터 `faq-edge-cases.json`은 개발 DB의 활성 FAQ 25개를 바탕으로 작성했다.
유효 질문은 90개, 모의 HTTP 시나리오는 10개다. 일부 질문에는 정답으로 인정되는 FAQ가 여러 개 있다.
`SINGLE_FAQ`는 허용 ID 중 하나로 충분하며 `MULTI_FAQ`는 명시한 근거 모두가 필요하다.
부분·복합·모호한 질문은 단일 FAQ Selector 지표에서 제외한다.

PowerShell에서 `FAQ_EDGE_LIVE_TEST=true`로 실행한다. 먼저 15문항을 실행하고 결과를 확인한 후 이어서 실행한다.
결과 파일이 있는 ID는 건너뛴다. DB 원문·벡터와 테스트 데이터가 이전 실행과 다르면 실행을 멈춘다.
별도 평가를 시작하려면 기존 보고서 디렉터리를 보관한 뒤 새 디렉터리에서 시작한다.

```powershell
$env:FAQ_EDGE_LIVE_TEST = 'true'
try {
    $env:FAQ_EDGE_LIMIT = '15'
    .\gradlew.bat test --rerun --tests 'com.irummate.chatbot.FaqEdgeCaseLiveTest' --console=plain
    if ($LASTEXITCODE -ne 0) { throw '예비 평가 실패' }
    # build/reports/faq-edge-eval/live 의 예비 결과를 확인한 뒤 이어서 실행
    $env:FAQ_EDGE_LIMIT = '90'
    .\gradlew.bat test --rerun --tests 'com.irummate.chatbot.FaqEdgeCaseLiveTest' --console=plain
    if ($LASTEXITCODE -ne 0) { throw '전체 평가 실패' }
} finally {
    Remove-Item Env:FAQ_EDGE_LIVE_TEST,Env:FAQ_EDGE_LIMIT -ErrorAction SilentlyContinue
}
```

원문 확인만 하려면 `FAQ_EDGE_LIMIT=0`으로 실행한다. 각 유효 질문에서 실제 `FaqChatService`를
한 번 호출하고 테스트 spy가 실제 메서드 반환값을 관찰한다. 최대 임베딩 90회, Selector 90회,
생성 90회이며, SDK 내부 재시도는 호출 집계에 포함하지 않는다. 정상 NONE은 생성 0회다.
각 문항이 끝나면 결과가 저장된다. 문항 처리 중 프로세스가 종료되면 그 문항의 호출은 재실행될 수 있다.

HTTP 증적은 수동 플래그를 해제한 뒤 아래처럼 수집한다.

```powershell
.\gradlew.bat test --tests 'com.irummate.chatbot.FaqChatControllerTest' --console=plain
Copy-Item build/test-results/test/TEST-com.irummate.chatbot.FaqChatControllerTest.xml build/reports/faq-edge-eval/mock-api.xml
```

선택된 FAQ 원문과 답변을 대조하고 `build/reports/faq-edge-eval/reviews.json`에
`{"EDGE-003":["CORRECT","판정 근거"]}` 형식으로 판정한다. 자동 판단이 곤란하면
`REVIEW`를 사용한다. 사전 라벨을 수정해야 하는 사례는 결과에 별도로 적는다.
Python 3으로 `src/test/resources/chatbot/faq-edge-report.py`를 실행하면 같은 디렉터리에
`retrieval-results.csv`, `selector-results.csv`, `answer-results.json`, `failures.md`,
`summary.md`가 생성된다. `analysis.md`를 추가하면 다음 리포트 생성 시 요약에 포함된다.
