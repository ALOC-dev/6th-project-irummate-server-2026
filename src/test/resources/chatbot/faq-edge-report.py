"""Render FAQ edge evaluation artifacts using saved live traces and manual reviews."""
import csv
import json
import statistics
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path


REPORT = Path("build/reports/faq-edge-eval")


def read(name, default=None):
    path = REPORT / name
    return json.loads(path.read_text(encoding="utf-8-sig")) if path.exists() else default


def save_csv(name, header, rows):
    with (REPORT / name).open("w", encoding="utf-8-sig", newline="") as file:
        writer = csv.writer(file)
        writer.writerow(header)
        writer.writerows(rows)


def rate(n, d):
    return f"{n}/{d} ({100 * n / d:.2f}%)" if d else f"{n}/{d} (N/A)"


def distribution(rows):
    values = [row["candidates"][0]["distance"] for row in rows if row.get("candidates")]
    if not values:
        return "n=0"
    return (f"n={len(values)}, 최소 {min(values):.5f}, 최대 {max(values):.5f}, "
            f"평균 {statistics.mean(values):.5f}, 중앙값 {statistics.median(values):.5f}")


API_TESTS = [
    "[2] body =",
    "[3] body =",
    "[4] body =",
    "acceptsExactly500CharactersWithoutExternalCalls()",
    "[5] body =",
    "[1] body =",
    "[6] body =",
    "rejectsAnonymousAndInvalidTokenBeforeChat()",
    "openAiFailureReturnsGenericServerError()",
    "databaseAndMalformedModelFailuresKeepTheirHttpContracts()",
]


CAUSES = {
    "RETRIEVAL_FAILURE": ("Top-3 후보에 근거 FAQ가 없었음", "검색 후보 폭과 구어체 표현 검토"),
    "SELECTOR_WRONG_FAQ": ("근거 FAQ가 후보에 있었으나 다른 FAQ를 선택함", "질문의 대상과 조건 구분 검토"),
    "SELECTOR_FALSE_ACCEPT": ("요청을 뒷받침하는 FAQ가 없지만 후보를 수락함", "부분 근거와 직접 근거를 구분"),
    "SELECTOR_FALSE_REJECT": ("근거 FAQ가 후보에 있었으나 NONE을 선택함", "명시된 조건으로 직접 판단 가능한 경우 검토"),
    "ANSWER_INCORRECT": ("답변의 사실 또는 조건이 원문과 다름", "수치 및 조건 보존 평가 강화"),
    "ANSWER_INCOMPLETE": ("질문에 필요한 조건이나 절차가 빠짐", "FAQ별 필수 조건 검증"),
    "ANSWER_HALLUCINATION": ("선택된 FAQ로 확인할 수 없는 정보가 답변에 포함됨", "근거 범위 및 미확인 안내 강화"),
    "ANSWER_OVERANSWER": ("정확한 내용이지만 요청 범위를 넘는 설명이 있음", "질문에 필요한 범위로 응답 제한"),
    "MULTI_FAQ_LIMITATION": ("요청을 충족하려면 두 개 이상의 FAQ가 필요함", "복합 요청 분리 또는 다중 FAQ 지원 정책 검토"),
    "PARTIAL_COVERAGE": ("지원되는 하위 질문과 미지원 하위 질문이 섞여 있음", "답변 가능한 부분만 표시하는 정책 검토"),
    "AMBIGUOUS_INPUT": ("질문의 대상을 특정할 정보가 부족함", "확인 질문 정책 검토"),
    "SYSTEM_FAILURE": ("호출 또는 응답 처리 중 예외가 발생함", "예외 종류와 실패 단계를 조사"),
    "API_TEST_FAILURE": ("mock HTTP 테스트 결과 확인 필요", "JUnit 실패 원인 조사"),
}


def main():
    cases = read("test-cases.json")
    sources = {faq["id"]: faq for faq in read("faq-source.json")}
    reviews = read("reviews.json", {})
    api_path = REPORT / "mock-api.xml"
    api = {} if not api_path.exists() else {
        test.get("name"): "FAIL" if test.find("failure") is not None or test.find("error") is not None
        else "REVIEW" if test.find("skipped") is not None else "PASS"
        for test in ET.parse(api_path).findall("testcase")
    }
    rows = []
    for index, case in enumerate(cases):
        row = dict(case)
        rows.append(row)
        if index >= 90:
            name = API_TESTS[index - 90]
            matches = [(test, verdict) for test, verdict in api.items() if test.startswith(name)]
            test, verdict = matches[0] if len(matches) == 1 else (name, "REVIEW")
            row.update(verdict=verdict, execution=test, executed=verdict != "REVIEW",
                       causes=["API_TEST_FAILURE"] if verdict == "FAIL" else [])
            continue
        trace = read(f"live/{case['id']}.json")
        row.update(executed=trace is not None, verdict="REVIEW", causes=[])
        if trace is None:
            continue
        row.update(trace)
        expected = set(case["expectedFaqIds"])
        ids = [candidate["id"] for candidate in trace["candidates"]]
        selected = trace["selected"]
        kind = case["expectedType"]
        row.update(top1=ids[0] if ids else None, hit=bool(expected.intersection(ids)),
                   allExpectedInTop3=bool(expected) and expected.issubset(ids),
                   selectedSource=sources.get(selected))
        causes = row["causes"]
        if trace["error"]:
            row.update(quality="ERROR", verdict="FAIL")
            causes.append("SYSTEM_FAILURE")
            continue
        if selected is None and trace["answerCalls"] != 0:
            raise ValueError(f"{case['id']} generated an answer after NONE")
        if kind == "SINGLE_FAQ":
            if not row["hit"]:
                causes.append("RETRIEVAL_FAILURE")
            elif selected is None:
                causes.append("SELECTOR_FALSE_REJECT")
            elif selected not in expected:
                causes.append("SELECTOR_WRONG_FAQ")
        if kind == "NONE" and selected is not None:
            causes.append("SELECTOR_FALSE_ACCEPT")
        if kind == "AMBIGUOUS":
            causes.append("AMBIGUOUS_INPUT")
        if kind == "MULTI_FAQ":
            causes.append("MULTI_FAQ_LIMITATION")
        if kind == "PARTIAL":
            causes.append("PARTIAL_COVERAGE")
        if selected is None:
            row.update(quality="REFUSED", verdict="PASS" if kind in ("NONE", "AMBIGUOUS") else "FAIL")
        elif case["id"] in reviews:
            quality, note = reviews[case["id"]]
            row.update(quality=quality, reviewNote=note,
                       verdict="REVIEW" if quality == "REVIEW" else
                       "PASS" if quality == "CORRECT" and (kind == "PARTIAL" or
                           (kind == "SINGLE_FAQ" and selected in expected)) else "FAIL")
            answer_cause = {"INCORRECT": "ANSWER_INCORRECT", "INCOMPLETE": "ANSWER_INCOMPLETE",
                            "UNSUPPORTED": "ANSWER_HALLUCINATION", "OVERANSWER": "ANSWER_OVERANSWER"}.get(quality)
            if answer_cause:
                causes.append(answer_cause)
        else:
            row["quality"] = "REVIEW"
    live = [row for row in rows[:90] if row["executed"]]
    save_csv("retrieval-results.csv",
             ["id", "question", "expected", "top1", "l2_1", "top2", "l2_2", "top3", "l2_3", "any_hit", "all_hit", "error"],
             ([r["id"], r["question"], r["expectedFaqIds"],
               *([v for c in r["candidates"] for v in (c["id"], c["distance"])] +
                 [None] * (6 - 2 * len(r["candidates"]))), r["hit"], r["allExpectedInTop3"], r["error"]] for r in live))
    save_csv("selector-results.csv", ["id", "question", "type", "expected", "top3", "selected", "none", "correct", "error"],
             ([r["id"], r["question"], r["expectedType"], r["expectedFaqIds"], [c["id"] for c in r["candidates"]],
               r["selected"], r["selected"] is None if not r["error"] else None,
               ((r["selected"] in r["expectedFaqIds"]) if r["expectedType"] == "SINGLE_FAQ"
                else r["selected"] is None if r["expectedType"] == "NONE" else None) if not r["error"] else None,
               r["error"]] for r in live))
    (REPORT / "answer-results.json").write_text(json.dumps(rows, ensure_ascii=False, indent=2), encoding="utf-8")
    failures = ["# 실패 및 추가 검토"]
    for r in rows:
        if r["verdict"] == "PASS":
            continue
        failures += [
            f"## {r['id']} · {r['verdict']} · {r['question']}",
            f"- 기대 {r['expectedType']} / FAQ {r['expectedFaqIds']} / {r['expectedFacts']}",
            f"- 금지 주장 {r['forbiddenFacts']}",
            f"- Top-3 {r.get('candidates', [])} / 선택 {r.get('selected')} / 품질 {r.get('quality', 'N/A')}",
            f"- 답변 {r.get('answer') or r.get('error') or '없음'}",
            f"- 판정 근거 {r.get('reviewNote', '단계 결과 및 기대 라벨 기준')}",
        ]
        failures += [f"- {cause} · {CAUSES[cause][0]} · 개선 후보 {CAUSES[cause][1]}" for cause in r["causes"]]
        for faq_id in sorted(set(r["expectedFaqIds"]) | ({r["selected"]} if r.get("selected") is not None else set())):
            failures += [f"**FAQ {faq_id} · {sources[faq_id]['question']}**", "", sources[faq_id]["answer"]]
        failures.append("")
    (REPORT / "failures.md").write_text("\n".join(failures), encoding="utf-8")
    summarize(rows, live)


def summarize(rows, live):
    counts = Counter(r["verdict"] for r in rows)
    single = [r for r in live if r["expectedType"] == "SINGLE_FAQ"]
    none = [r for r in live if r["expectedType"] == "NONE"]
    retrieved = [r for r in single if r["hit"]]
    generated = [r for r in live if r.get("answerCalls")]
    qualities = Counter(r.get("quality") for r in generated)
    reviewed = len(generated) - qualities["REVIEW"]
    causes = Counter(cause for r in live for cause in r["causes"])
    correct = sum(r["selected"] in r["expectedFaqIds"] and not r["error"] for r in single)
    summary = [
        "# FAQ Edge Case 평가", "",
        f"- 실행 {sum(r['executed'] for r in rows)}/100 · PASS {counts['PASS']}/100 · FAIL {counts['FAIL']}/100 · 검토 필요 {counts['REVIEW']}/100",
        f"- 단일 FAQ Top-1 {rate(sum(r['top1'] in r['expectedFaqIds'] for r in single), len(single))}",
        f"- 단일 FAQ Recall@3 {rate(len(retrieved), len(single))} · 검색 누락 {len(single)-len(retrieved)}/{len(single)}",
        f"- 단일 FAQ Selector 정확도 {rate(correct, len(single))} · 검색 적중 이후 {rate(correct, len(retrieved))}",
        f"- 단일 FAQ 오거절률 {rate(sum(r['selected'] is None and not r['error'] for r in single), len(single))}",
        f"- OOS 거절률 {rate(sum(r['selected'] is None and not r['error'] for r in none), len(none))} · 오수락률 {rate(causes['SELECTOR_FALSE_ACCEPT'], len(none))}",
        f"- 판정 확정된 생성 답변 정확도 {rate(qualities['CORRECT'], reviewed)} · 불완전 {rate(qualities['INCOMPLETE'], reviewed)} · 근거 외 주장 {qualities['UNSUPPORTED']}/{reviewed} · 미확정 {qualities['REVIEW']}/{len(generated)}",
        f"- 생성 품질 분포 {dict(qualities)} · 정상 NONE 중 추가 생성 호출 {sum(r['answerCalls'] for r in live if r.get('quality') == 'REFUSED')}",
        f"- API 서비스 호출 임베딩 {sum(r['embeddingCalls'] for r in live)} · Selector {sum(r['selectorCalls'] for r in live)} · 생성 {sum(r['answerCalls'] for r in live)} (SDK 재시도 제외)",
        f"- 실패 분류 {dict(causes)}",
        "", "## 카테고리", "", "| 유형 | 실행 | PASS | FAIL | 검토 |", "|---|---:|---:|---:|---:|",
    ]
    for category in dict.fromkeys(r["category"] for r in rows):
        group = [r for r in rows if r["category"] == category]
        c = Counter(r["verdict"] for r in group)
        summary.append(f"| {category} | {sum(r['executed'] for r in group)}/{len(group)} | {c['PASS']} | {c['FAIL']} | {c['REVIEW']} |")
    summary += ["", "## L2 최근접 거리", "",
                f"- 단일 FAQ {distribution(single)}", f"- OOS {distribution(none)}",
                f"- Selector 오선택 {distribution([r for r in single if 'SELECTOR_WRONG_FAQ' in r['causes']])}",
                f"- OOS 오수락 {distribution([r for r in none if 'SELECTOR_FALSE_ACCEPT' in r['causes']])}",
                f"- 오거절 전체 {distribution([r for r in single if r['selected'] is None and not r['error']])}",
                "", "기존 95문항의 Top-1 68/75, Recall@3 75/75, Selector 74/75, OOS 20/20은 별도 평가셋의 기준값이다. "
                "이번 평가에는 숫자 경계·구어체·복합 질문·근거 조작이 포함되므로 수치 차이만으로 성능 변화를 단정하지 않는다.",
                "", "단일 FAQ 지표는 정답으로 인정된 ID 중 하나를 기준으로 한다. 복합·부분·모호한 질문은 단일 FAQ 정확도에서 제외한다. "
                "FAIL은 기대 답변을 충족하지 못한 경우이며, 복합 문항에서 단일 FAQ 구조의 한계를 Selector 오류로 세지 않는다."
                ]
    analysis = REPORT / "analysis.md"
    if analysis.exists():
        summary += ["", analysis.read_text(encoding="utf-8-sig")]
    (REPORT / "summary.md").write_text("\n".join(summary) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
