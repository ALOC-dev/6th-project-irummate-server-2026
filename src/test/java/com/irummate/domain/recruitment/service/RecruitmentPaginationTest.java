package com.irummate.domain.recruitment.service;

import com.irummate.domain.recruitment.repository.RecruitmentParticipantsRepository;
import com.irummate.domain.recruitment.repository.RecruitmentsRepository;
import com.irummate.domain.user.repository.UsersRepository;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import com.irummate.global.util.HashIdsUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecruitmentPaginationTest {
    private final RecruitmentsRepository recruitments = mock(RecruitmentsRepository.class);
    private final RecruitmentParticipantsRepository participants = mock(RecruitmentParticipantsRepository.class);
    private final UsersRepository users = mock(UsersRepository.class);
    private final RecruitmentParticipationService participationService =
            new RecruitmentParticipationService(recruitments, participants, users, mock(HashIdsUtils.class));

    @ParameterizedTest
    @CsvSource({"-1,10", "0,0", "0,-1", "0,51", "0,100000"})
    void invalidHistoryPaginationFailsBeforeRepositoryAccess(int page, int size) {
        BusinessException historyError = assertThrows(BusinessException.class,
                () -> participationService.getMyParticipation(1L, page, size));

        assertEquals(ErrorCode.INVALID_INPUT, historyError.getErrorCode());
        verifyNoInteractions(recruitments, participants, users);
    }

    @ParameterizedTest
    @CsvSource({"0,1", "0,50", "1,10"})
    void validHistoryPaginationReachesRepository(int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        when(participants.findAllByUser_IdOrderByJoinedAtDescIdDesc(1L, pageable))
                .thenReturn(Page.empty());

        assertTrue(participationService.getMyParticipation(1L, page, size).isEmpty());
        verify(participants).findAllByUser_IdOrderByJoinedAtDescIdDesc(1L, pageable);
    }
}
