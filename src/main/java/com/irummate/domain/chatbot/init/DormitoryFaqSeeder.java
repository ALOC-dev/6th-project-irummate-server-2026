package com.irummate.domain.chatbot.init;

import com.irummate.domain.chatbot.entity.DormitoryFaq;
import com.irummate.domain.chatbot.entity.FaqCategory;
import com.irummate.domain.chatbot.repository.DormitoryFaqRepository;
import com.irummate.domain.chatbot.service.EmbeddingService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DormitoryFaqSeeder implements CommandLineRunner {

    private final DormitoryFaqRepository dormitoryFaqRepository;
    private final EmbeddingService embeddingService;

    @Override
    public void run(String... args) {

        if (dormitoryFaqRepository.count() > 0) {
            return;
        }

        List<FaqSeed> seeds = List.of(

                new FaqSeed(
                        FaqCategory.PAYMENT,
                        "기숙사비 분납 신청이 가능한가요?",
                        "정규학기 기숙사비는 4회(고정)으로 분납 신청이 가능하며, " +
                                "방학의 경우 2회(고정)으로 분납 신청이 가능합니다. " +
                                "기숙사 승인 공고를 통해 안내되는 분납 신청 방법으로 신청하시면 됩니다."
                ),

                new FaqSeed(
                        FaqCategory.PAYMENT,
                        "기숙사비 환불 규정은 어떻게 되나요?",
                        """
                        기숙사비 퇴실 시 환불 비율은 다음과 같습니다.

                        [정규학기]
                        - 입주 전: 95%
                        - 정시입주 후 15일까지: 75%
                        - 정시입주 후 30일까지: 62.50%
                        - 정시입주 후 45일까지: 50%
                        - 정시입주 후 60일까지: 37.50%
                        - 정시입주 후 75일까지: 25%
                        - 정시입주 후 76일 이후: 0%

                        [방학]
                        - 입주 전: 95%
                        - 정시입주 후 30일까지: 50%
                        - 정시입주 후 31일 이후: 0%

                        위 환불 규정은 2026년 6월 12일 기준 최신 기준입니다.
                        """
                ),

                new FaqSeed(
                        FaqCategory.PAYMENT,
                        "기숙사비 납부는 어떻게 하나요?",
                        "무통장입금 또는 계좌이체로 납부하시면 되오며 " +
                                "기숙사 사용 승인 공지사항에 자세한 납부 방법을 안내해드리고 있으니 참고 부탁드립니다."
                ),

                new FaqSeed(
                        FaqCategory.FACILITY,
                        "빨래는 어떻게 할 수 있나요?",
                        "생활관 L층 남, 여 별 공용 세탁실을 이용하며, " +
                                "세탁카드는 체력단련실 앞 세탁카드 발급기에서 발급받아 사용하시면 됩니다."
                ),

                new FaqSeed(
                        FaqCategory.FACILITY,
                        "세탁실·취사실·체력단련실 이용 시간이 궁금해요.",
                        "이용시간은 오전 6시부터 다음 날 새벽 1시까지입니다."
                ),

                new FaqSeed(
                        FaqCategory.FACILITY,
                        "냉장고를 사용하고 싶어요.",
                        "렌탈 냉장고 또는 개인 냉장고 사용이 가능하고, " +
                                "개인 냉장고는 50L 이하·생산 10년 이내 제품으로 행정실 등록이 필요합니다."
                ),

                new FaqSeed(
                        FaqCategory.FACILITY,
                        "택배는 어떻게 수령하나요?",
                        "택배는 로비 맞은편 택배보관함에 거주하는 ‘층’별로 분류되어 있습니다. " +
                                "거주하는 층에 해당하는 보관함에서 본인의 택배를 개별수령하면 됩니다."
                ),

                new FaqSeed(
                        FaqCategory.FACILITY,
                        "기숙사로 택배를 보내려면 주소를 어떻게 적어야 하나요?",
                        """
                        생활관: 서울특별시 동대문구 서울시립대로 163 서울시립대학교 생활관 000호, 우편번호 02504
                        국제학사: 서울특별시 동대문구 서울시립대로 163 서울시립대학교 국제학사 0000호, 우편번호 02504
                        """
                ),

                new FaqSeed(
                        FaqCategory.FACILITY,
                        "인터넷은 어떻게 사용할 수 있나요?",
                        "공용구간은 학교 Wi-Fi 사용 가능하고 생활관 실 내에서는 기숙사 공용 Wi-Fi, " +
                                "국제학사는 하우스별 설치된 Wi-Fi 사용하시면 됩니다."
                ),

                new FaqSeed(
                        FaqCategory.FACILITY,
                        "호실에서 고장난 비품이나 시설이 있어요.",
                        "호실에서 고장난 비품이 있거나 시설에 문제가 있는 경우 " +
                                "기술팀 사무실에 고장·불편 신고서 작성 또는 생활관 홈페이지 내 고장,불편신고 작성해주시면 됩니다."
                ),

                new FaqSeed(
                        FaqCategory.FACILITY,
                        "외부 친구나 방문객이 기숙사에 들어와도 되나요?",
                        "기숙사 내부에는 외부인 출입이 금지되어 있으며 기숙사생만 출입할 수 있습니다."
                ),

                new FaqSeed(
                        FaqCategory.ADMISSION,
                        "학기 중 휴학을 하면 퇴실해야 하나요?",
                        "기숙사 선발 후 학기 중 휴학·자퇴·졸업 등으로 거주 자격을 상실한 경우 퇴실해야 합니다."
                ),

                new FaqSeed(
                        FaqCategory.ADMISSION,
                        "선발을 취소하고 싶으면 어떻게 해야 하나요?",
                        """
                        1) 재학생, 기숙사비 납부한 경우:
                        생활관 홈페이지 Q&A게시판에 비밀글로 학과, 학번, 이름, 전화번호를 기재한 후 기숙사를 취소하겠다는 내용을 작성하고 퇴실환불신청서를 첨부합니다.
                        퇴실환불신청서 서식 위치: [서울시립대학교 생활관 홈페이지] - [커뮤니티] - [서식자료실] - [퇴실환불신청서]

                        2) 재학생, 기숙사비 납부하지 않은 경우:
                        생활관 홈페이지 Q&A게시판에 비밀글로 학과, 학번, 이름, 전화번호를 기재한 후 기숙사를 취소하겠다는 내용을 작성합니다.

                        3) 신입생, 기숙사비 납부한 경우:
                        생활관 행정실로 유선 취소요청(02-6490-5186~7) 후 담당자 이메일로 퇴실환불신청서를 송부합니다.
                        퇴실환불신청서 서식 위치: [서울시립대학교 생활관 홈페이지] - [커뮤니티] - [서식자료실] - [퇴실환불신청서]

                        4) 신입생, 기숙사비 납부하지 않은 경우:
                        생활관 행정실로 유선 취소요청(02-6490-5186~7)합니다.
                        """
                ),

                new FaqSeed(
                        FaqCategory.ADMISSION,
                        "입실 관련 서류는 어떻게 제출하나요?",
                        """
                        입실 당일 입실 절차 진행 시 공지문 내 공지된 제출서류를 신분증과 함께 제출 해주시면 됩니다.

                        한국인: 입실일 기준 6개월 이내 검사한 결핵검사결과지
                        외국인: 입실일 기준 6개월 이내 검사한 결핵검사결과지, 홍역예방접종증명서(2회 접종)
                        """
                ),

                new FaqSeed(
                        FaqCategory.ADMISSION,
                        "입사신청기간을 놓친 경우 어떻게 해야 하나요?",
                        "기숙사 신청은 정해진 기간에만 받고 있습니다. " +
                                "신청마감 이후에는 신청을 할 수 없습니다. " +
                                "기간을 놓친 경우 비정기적으로 진행되는 추가모집에 지원할 수 있습니다."
                ),

                new FaqSeed(
                        FaqCategory.ADMISSION,
                        "추가모집은 언제 진행되나요?",
                        "입실 완료 후 취소, 퇴실자가 있는 경우 추가모집을 진행합니다. " +
                                "기숙사 여석이 없는 경우 추가모집을 진행하지 않습니다. " +
                                "추가모집 시 서울시립대학교, 서울시립대학교 생활관 홈페이지에 공고합니다."
                ),

                new FaqSeed(
                        FaqCategory.ADMISSION,
                        "사회적배려대상자 및 국가유공자 자녀 관련 혜택이 있나요?",
                        "2026년 기숙사 선발 시 사회적배려대상자 우선입사대상자 항목에 " +
                                "‘국가유공자 및 그의 자녀’, ‘독립유공자의 자녀 및 손자녀’가 추가되었습니다."
                ),

                new FaqSeed(
                        FaqCategory.ADMISSION,
                        "기숙사 선발기준이 궁금합니다.",
                        "기숙사 선발은 기숙사 선발 계획 공지문에 안내된 기준 및 방식에 따라 진행됩니다. " +
                                "기본적으로 학생 대표가 참여하는 추첨 방식으로 선발하고 있으며, " +
                                "세부 선발 기준 및 절차는 공지문을 통해 확인해 주시기 바랍니다."
                ),

                new FaqSeed(
                        FaqCategory.ADMISSION,
                        "입사신청 및 입사절차가 궁금합니다.",
                        "선발계획 공지 → 기숙사 입사 신청 → 선발 → 기숙사비 납부 → " +
                                "학사·룸메이트 신청 → 입실 순으로 진행됩니다."
                ),

                new FaqSeed(
                        FaqCategory.RULE,
                        "생활관 수칙은 어디에서 확인하면 되나요?",
                        "생활관 수칙은 생활관 홈페이지 ‘생활관 입실안내 > 생활관 규정/수칙’에 게시된 내용을 기준으로 확인합니다."
                ),

                new FaqSeed(
                        FaqCategory.RULE,
                        "생활관·국제학사 운영규정은 어디에서 확인하면 되나요?",
                        "생활관·국제학사 운영규정은 서울시립대학교 홈페이지 ‘규정’에 게시된 " +
                                "‘서울시립대학교 생활관·국제학사 운영 규정’을 기준으로 확인합니다."
                ),

                new FaqSeed(
                        FaqCategory.RULE,
                        "건강진단서는 어떻게 준비해야 하나요?",
                        "필수항목(폐결핵)이 포함된 건강진단서가 필요합니다. " +
                                "가까운 병원이나 보건소에서 발급 가능합니다. " +
                                "병원보다는 보건소가 합리적인 가격으로 발급이 가능하여, " +
                                "보건소 이용(검사 후 발급까지 약 5일 소요)을 권장합니다. " +
                                "학교에서 가까운 곳은 동대문보건소로 동대문구청안에 있습니다. " +
                                "건강진단서(결핵검사결과지)는 입실일 기준 6개월 이내 검사한 서류를 제출하시면 됩니다."
                ),

                new FaqSeed(
                        FaqCategory.RULE,
                        "입실할 때 무엇을 준비해야 하나요?",
                        """
                        ※ 준비하면 좋을 물품
                        - 이불
                        - 얇은 요
                        - 베개
                        - 슬리퍼
                        - 세면도구(비누, 샴푸, 때수건, 마른수건, 치약, 칫솔)
                        - 세면도구 바구니
                        - 옷걸이
                        - 손걸레
                        - 개인청소도구
                        - 전기스탠드
                        - 헤어 드라이기
                        - 멀티콘센트
                        - 두루마리휴지

                        ※ 금지품목
                        - 전기장판
                        - 전기난로
                        - 전기쿠커
                        - TV
                        - 전기밥솥
                        - 온풍기
                        - 가스버너
                        - 기타 화재유발이 있는 물품 전체(헤어 드라이기는 예외)
                        - 술
                        - 화투
                        - 트럼프카드 등
                        """
                ),

                new FaqSeed(
                        FaqCategory.RULE,
                        "출입통제나 통금이 있나요?",
                        "야간통행금지시간은 없습니다. " +
                                "단, 소음을 발생시킬 경우 벌점(생활관 규정에 따름)이 부과됩니다. " +
                                "소란없이 만취상태로 귀가하는 경우에는 벌점 없습니다. " +
                                "단, 숙소로 들어갈 수는 없으며, 병원응급실 후송 또는 보호자에게 인계됩니다."
                ),

                new FaqSeed(
                        FaqCategory.ROOMMATE,
                        "룸메이트 신청 및 방 배정은 어떻게 이루어지나요?",
                        "매학기 입실자를 대상으로 룸메이트 신청을 받습니다. " +
                                "룸메이트 신청은 동일 학사에 배정된 학생들끼리 신청해야 하며, " +
                                "잔류 중인 학생들이 이사를 최소화할 수 있도록 현재 사용 중인 호실을 우선 배정하고 " +
                                "그 학생과 룸메이트 신청한 학생을 같은 방으로 배정합니다. " +
                                "그 밖의 룸메이트 신청자들은 짝을 지어 빈 호실로 배정하며, " +
                                "별도의 신청이 없는 경우에는 임의 배정됩니다."
                )
        );

        List<DormitoryFaq> faqs = new ArrayList<>();

        for (FaqSeed seed : seeds) {
            String embeddingText =
                    "질문: " + seed.question()
                            + "\n답변: " + seed.answer();

            float[] embedding =
                    embeddingService.embedToArray(embeddingText);

            DormitoryFaq faq = DormitoryFaq.builder()
                    .category(seed.category())
                    .question(seed.question())
                    .answer(seed.answer())
                    .embedding(embedding)
                    .build();

            faqs.add(faq);
        }

        dormitoryFaqRepository.saveAll(faqs);
    }

    private record FaqSeed(
            FaqCategory category,
            String question,
            String answer
    ) {
    }
}