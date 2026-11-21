package com.irummate.domain.recruitment.entity;

import com.irummate.domain.recruitment.dto.RecruitmentResponseDto;
import com.irummate.domain.recruitment.dto.RecruitmentParticipationView;
import com.irummate.domain.user.entity.Users;
import com.irummate.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class RecruitmentPriceTest {
    private final LocalDateTime now = LocalDateTime.of(2035, 1, 1, 12, 0);

    private Recruitments recruitment() {
        return Recruitments.builder().category(RecruitmentCategory.GROUP_BUY)
                .author(Users.builder().nickname("작성자").build())
                .status(RecruitmentStatus.OPEN).visibility(RecruitmentVisibility.VISIBLE)
                .nowParticipants(3).maxParticipants(5)
                .deadlineAt(now.plusDays(1)).activityAt(now.plusDays(2)).build();
    }

    @Test
    void fixedAmountDoesNotDependOnCount() {
        Recruitments recruitment = recruitment();
        recruitment.configurePrice(RecruitmentPriceType.FIXED, 5000);
        assertEquals(5000, recruitment.calculateEstimatedFee(false, now));
        assertEquals(5000, recruitment.calculateEstimatedFee(true, now));
    }

    @Test
    void splitUsesCurrentOrProspectiveCountAndRoundsUp() {
        Recruitments recruitment = recruitment();
        recruitment.configurePrice(RecruitmentPriceType.SPLIT, 20000);
        assertEquals(6667, recruitment.calculateEstimatedFee(false, now));
        assertEquals(5000, recruitment.calculateEstimatedFee(true, now));
        recruitment.decreaseParticipants(now);
        assertEquals(10000, recruitment.calculateEstimatedFee(false, now));
        assertEquals(20000, recruitment.getAmount());
    }

    @ParameterizedTest
    @EnumSource(value = RecruitmentStatus.class, names = {"CLOSED", "CANCELLED", "ENDED"})
    void inactiveBoardUsesCurrentCount(RecruitmentStatus status) {
        Recruitments recruitment = Recruitments.builder()
                .category(RecruitmentCategory.GROUP_BUY).status(status)
                .visibility(RecruitmentVisibility.VISIBLE)
                .nowParticipants(4).maxParticipants(6).build();
        recruitment.configurePrice(RecruitmentPriceType.SPLIT, 20000);
        assertEquals(5000, recruitment.calculateEstimatedFee(true, now));
    }

    @Test
    void deadlineOrFullCapacityPreventsProspectiveCount() {
        Recruitments recruitment = recruitment();
        recruitment.configurePrice(RecruitmentPriceType.SPLIT, 20000);
        assertEquals(6667, recruitment.calculateEstimatedFee(true, now.plusDays(1)));
        recruitment.increaseParticipants(now);
        recruitment.increaseParticipants(now);
        assertEquals(4000, recruitment.calculateEstimatedFee(true, now));
    }

    @Test
    void groupBuyRequiresTypeAndPositiveAmount() {
        Recruitments recruitment = recruitment();
        assertThrows(BusinessException.class, () -> recruitment.configurePrice(null, 1000));
        assertThrows(BusinessException.class, () -> recruitment.configurePrice(RecruitmentPriceType.FIXED, null));
        assertThrows(BusinessException.class, () -> recruitment.configurePrice(RecruitmentPriceType.FIXED, 0));
        assertThrows(BusinessException.class, () -> recruitment.configurePrice(RecruitmentPriceType.FIXED, -1));
    }

    @Test
    void otherCategoriesHaveNoPrice() {
        Recruitments recruitment = Recruitments.builder().category(RecruitmentCategory.MEAL).build();
        recruitment.configurePrice(null, null);
        assertNull(recruitment.calculateEstimatedFee(false, now));
        assertThrows(BusinessException.class, () -> recruitment.configurePrice(RecruitmentPriceType.FIXED, 1000));
    }

    @Test
    void partialPriceUpdatePreservesOmittedValue() {
        Recruitments recruitment = recruitment();
        recruitment.configurePrice(RecruitmentPriceType.FIXED, 1000);
        recruitment.update(null, null, null, null, RecruitmentPriceType.SPLIT, null, now);
        assertEquals(1000, recruitment.getAmount());
        assertEquals(RecruitmentPriceType.SPLIT, recruitment.getPriceType());
        recruitment.update(null, null, null, null, null, 20000, now);
        assertEquals(6667, recruitment.calculateEstimatedFee(false, now));
        assertThrows(BusinessException.class, () -> recruitment.update(
                "변경 제목", null, null, null, null, 0, now));
        assertNull(recruitment.getTitle());
        assertEquals(20000, recruitment.getAmount());
    }

    @Test
    void roundingDoesNotOverflowInteger() {
        Recruitments recruitment = recruitment();
        recruitment.configurePrice(RecruitmentPriceType.SPLIT, Integer.MAX_VALUE);
        assertEquals(715827883, recruitment.calculateEstimatedFee(false, now));
    }

    @Test
    void listDistinguishesNewUserFromFormerParticipantAndMasksHiddenCard() {
        Recruitments recruitment = recruitment();
        recruitment.configurePrice(RecruitmentPriceType.SPLIT, 20000);
        assertEquals(5000, RecruitmentResponseDto.from(recruitment,
                RecruitmentParticipationView.NONE, false).getEstimatedFee());
        assertEquals(6667, RecruitmentResponseDto.from(recruitment,
                RecruitmentParticipationView.LEFT, false).getEstimatedFee());
        RecruitmentResponseDto hidden = RecruitmentResponseDto.builder()
                .visibility(RecruitmentVisibility.HIDDEN).title("숨겨진 게시글입니다.").build();
        assertNull(hidden.getAmount());
        assertNull(hidden.getPriceType());
        assertNull(hidden.getEstimatedFee());
    }
}
