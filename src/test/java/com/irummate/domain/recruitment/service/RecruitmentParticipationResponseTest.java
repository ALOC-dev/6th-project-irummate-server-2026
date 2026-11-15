package com.irummate.domain.recruitment.service;

import com.irummate.domain.recruitment.dto.RecruitmentDetailResponseDto;
import com.irummate.domain.recruitment.dto.RecruitmentLeaveResponseDto;
import com.irummate.domain.recruitment.dto.RecruitmentParticipationView;
import com.irummate.domain.recruitment.dto.RecruitmentResponseDto;
import com.irummate.domain.recruitment.entity.*;
import com.irummate.domain.recruitment.repository.RecruitmentParticipantsRepository;
import com.irummate.domain.recruitment.repository.RecruitmentsRepository;
import com.irummate.domain.user.entity.Users;
import com.irummate.domain.user.repository.UsersRepository;
import com.irummate.global.util.HashIdsUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecruitmentParticipationResponseTest {
    private final LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    private final RecruitmentsRepository recruitments = mock(RecruitmentsRepository.class);
    private final RecruitmentParticipantsRepository participants = mock(RecruitmentParticipantsRepository.class);
    private final UsersRepository users = mock(UsersRepository.class);
    private final RecruitmentService service = new RecruitmentService(recruitments, participants, users);
    private final RecruitmentParticipationService participationService =
            new RecruitmentParticipationService(recruitments, participants, users, mock(HashIdsUtils.class));
    private final Users viewer = user(2L);

    @ParameterizedTest
    @EnumSource(RecruitmentParticipationView.class)
    void listAndDetailReturnSameViewerStateAndJoinability(RecruitmentParticipationView expected) {
        Recruitments recruitment = recruitment(expected == RecruitmentParticipationView.MINE ? viewer : user(1L));
        RecruitmentParticipants participant = participant(recruitment, expected);
        RecruitmentsRepository.RecruitmentListRow row = mock(RecruitmentsRepository.RecruitmentListRow.class);
        when(row.getRecruitment()).thenReturn(recruitment);
        when(row.getParticipantStatus()).thenReturn(participant == null ? null : participant.getStatus());
        when(recruitments.findRecruitmentList(2L, null, RecruitmentVisibility.VISIBLE, null, null, PageRequest.of(0, 11)))
                .thenReturn(List.of(row));
        when(users.findById(2L)).thenReturn(Optional.of(viewer));
        when(recruitments.findById(11L)).thenReturn(Optional.of(recruitment));
        when(participants.findByRecruitment_IdAndUser_Id(11L, 2L)).thenReturn(Optional.ofNullable(participant));

        RecruitmentResponseDto list = service.getRecruitments(2L, null, null, null, 10).getItems().getFirst();
        RecruitmentDetailResponseDto detail = service.getRecruitment(2L, 11L);

        assertEquals(expected, list.getParticipating());
        assertEquals(expected, detail.getParticipating());
        assertEquals(expected == RecruitmentParticipationView.NONE, list.isJoinable());
        assertEquals(list.isJoinable(), detail.isJoinable());
        assertEquals(expected == RecruitmentParticipationView.NONE ? 5000 : 6667, list.getEstimatedFee());
        assertEquals(list.getEstimatedFee(), detail.getEstimatedFee());
        assertEquals(expected == RecruitmentParticipationView.REMOVED ? "제외 사유" : null,
                detail.getRemovalReason());
    }

    @ParameterizedTest
    @EnumSource(value = RecruitmentParticipationView.class, names = {"MINE", "JOINED", "LEFT", "REMOVED"})
    void historyPreservesAllParticipationStates(RecruitmentParticipationView expected) {
        Recruitments recruitment = recruitment(expected == RecruitmentParticipationView.MINE ? viewer : user(1L));
        when(participants.findAllByUser_IdOrderByJoinedAtDescIdDesc(2L, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(participant(recruitment, expected))));

        RecruitmentResponseDto response = participationService.getMyParticipation(2L, 0, 10).getFirst();

        assertEquals(expected, response.getParticipating());
        assertFalse(response.isJoinable());
    }

    @Test
    void hiddenHistoryPreservesOwnRemovalStateWithoutExposingPostInformation() {
        Recruitments recruitment = recruitment(user(1L));
        ReflectionTestUtils.setField(recruitment, "visibility", RecruitmentVisibility.HIDDEN);
        when(participants.findAllByUser_IdOrderByJoinedAtDescIdDesc(2L, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(participant(recruitment, RecruitmentParticipationView.REMOVED))));

        RecruitmentResponseDto response = participationService.getMyParticipation(2L, 0, 10).getFirst();

        assertEquals(RecruitmentParticipationView.REMOVED, response.getParticipating());
        assertFalse(response.isJoinable());
        assertEquals("숨겨진 게시글입니다.", response.getTitle());
        assertNull(response.getAuthorId());
        assertNull(response.getPlace());
        assertNull(response.getAmount());
    }

    @ParameterizedTest
    @CsvSource({
            "OPEN,VISIBLE,3,5,1,2,true",
            "CLOSED,VISIBLE,3,5,1,2,false",
            "CANCELLED,VISIBLE,3,5,1,2,false",
            "ENDED,VISIBLE,3,5,1,2,false",
            "OPEN,HIDDEN,3,5,1,2,false",
            "OPEN,VISIBLE,5,5,1,2,false",
            "OPEN,VISIBLE,3,5,-1,2,false",
            "OPEN,VISIBLE,3,5,1,-1,false"
    })
    void joinabilityAlsoDependsOnPostStateAndTime(RecruitmentStatus status, RecruitmentVisibility visibility,
                                                int count, int capacity, int deadlineDays, int activityDays,
                                                boolean expected) {
        Recruitments recruitment = recruitment(user(1L));
        ReflectionTestUtils.setField(recruitment, "status", status);
        ReflectionTestUtils.setField(recruitment, "visibility", visibility);
        ReflectionTestUtils.setField(recruitment, "nowParticipants", count);
        ReflectionTestUtils.setField(recruitment, "maxParticipants", capacity);
        ReflectionTestUtils.setField(recruitment, "deadlineAt", now.plusDays(deadlineDays));
        ReflectionTestUtils.setField(recruitment, "activityAt", now.plusDays(activityDays));

        assertEquals(expected, RecruitmentResponseDto.from(recruitment, RecruitmentParticipationView.NONE).isJoinable());
        assertEquals(expected, RecruitmentDetailResponseDto.from(recruitment, RecruitmentParticipationView.NONE, null)
                .isJoinable());
    }

    @Test
    void ownStatusResponsesSerializeOneEnumAndBooleanJoinability() {
        Recruitments recruitment = recruitment(user(1L));
        RecruitmentParticipants participant = participant(recruitment, RecruitmentParticipationView.LEFT);
        RecruitmentLeaveResponseDto leave = RecruitmentLeaveResponseDto.from(participant);
        assertEquals(RecruitmentParticipationView.LEFT, leave.getParticipating());
        assertEquals(participant.getLeftAt(), leave.getLeftAt());
        JsonMapper mapper = JsonMapper.builder().build();

        List<Object> responses = List.of(
                RecruitmentResponseDto.from(recruitment, RecruitmentParticipationView.LEFT),
                RecruitmentDetailResponseDto.from(recruitment, RecruitmentParticipationView.LEFT, null), leave);
        for (Object response : responses) {
            Map<?, ?> json = mapper.readValue(mapper.writeValueAsString(response), Map.class);
            assertEquals("LEFT", json.get("participating"));
            assertEquals(false, json.get("joinable"));
            assertFalse(json.containsKey("mine"));
            assertFalse(json.containsKey("participantStatus"));
        }
    }

    private RecruitmentParticipants participant(Recruitments recruitment, RecruitmentParticipationView view) {
        if (view == RecruitmentParticipationView.NONE) {
            return null;
        }
        if (view == RecruitmentParticipationView.MINE) {
            return RecruitmentParticipants.createHost(recruitment, viewer, now.minusDays(1));
        }
        RecruitmentParticipants participant = RecruitmentParticipants.joinAsMember(recruitment, viewer, now.minusDays(1));
        if (view == RecruitmentParticipationView.LEFT) {
            participant.leave(now.minusMinutes(1));
        } else if (view == RecruitmentParticipationView.REMOVED) {
            participant.remove("제외 사유", now.minusMinutes(1));
        }
        return participant;
    }

    private Recruitments recruitment(Users author) {
        Recruitments recruitment = Recruitments.builder().author(author).title("모집글")
                .category(RecruitmentCategory.GROUP_BUY).status(RecruitmentStatus.OPEN)
                .visibility(RecruitmentVisibility.VISIBLE).nowParticipants(3).maxParticipants(5)
                .priceType(RecruitmentPriceType.SPLIT).amount(20000)
                .deadlineAt(now.plusDays(1)).activityAt(now.plusDays(2)).build();
        ReflectionTestUtils.setField(recruitment, "id", 11L);
        return recruitment;
    }

    private Users user(Long id) {
        Users user = Users.builder().nickname("사용자" + id).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
