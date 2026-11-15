package com.irummate.domain.recruitment.controller;

import com.irummate.domain.recruitment.dto.RecruitmentParticipantRemoveRequestDto;
import com.irummate.domain.recruitment.dto.RecruitmentParticipantRemoveResponseDto;
import com.irummate.domain.recruitment.dto.RecruitmentParticipantsResponseDto;
import com.irummate.domain.recruitment.entity.RecruitmentParticipantRole;
import com.irummate.domain.recruitment.entity.RecruitmentParticipantStatus;
import com.irummate.domain.recruitment.entity.RecruitmentStatus;
import com.irummate.domain.recruitment.service.RecruitmentParticipationService;
import com.irummate.global.exception.GlobalExceptionController;
import com.irummate.global.util.HashIdsUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RecruitmentParticipantsControllerTest {
    private final RecruitmentParticipationService service = mock(RecruitmentParticipationService.class);
    private final String publicId = new HashIdsUtils("recruitment-controller-test", "8").encode(3L);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(2L, null, List.of()));
        mvc = MockMvcBuilders.standaloneSetup(new RecruitmentParticipationController(service))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionController()).build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void participantsEndpointReturnsPublicIdentityAndViewerFlag() throws Exception {
        when(service.getRecruitmentParticipants(2L, 11L)).thenReturn(RecruitmentParticipantsResponseDto.builder()
                .recruitmentId(11L).nowParticipants(2).maxParticipants(3)
                .participants(List.of(RecruitmentParticipantsResponseDto.Participant.builder()
                        .userId(publicId).nickname("참여자").role(RecruitmentParticipantRole.MEMBER).me(false).build()))
                .build());

        mvc.perform(get("/api/recruitments/11/participants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recruitmentId").value(11))
                .andExpect(jsonPath("$.data.participants[0].userId").value(publicId))
                .andExpect(jsonPath("$.data.participants[0].role").value("MEMBER"))
                .andExpect(jsonPath("$.data.participants[0].me").value(false));
        verify(service).getRecruitmentParticipants(2L, 11L);
    }

    @Test
    void removalRequestAndResponseUsePublicTargetId() throws Exception {
        when(service.removeParticipant(eq(2L), eq(11L), any())).thenReturn(RecruitmentParticipantRemoveResponseDto.builder()
                .recruitmentId(11L).targetUserId(publicId).participantStatus(RecruitmentParticipantStatus.REMOVED)
                .removalReason("제외 사유").nowParticipants(2).maxParticipants(3).status(RecruitmentStatus.OPEN).build());

        mvc.perform(post("/api/recruitments/11/participants/remove")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetUserId\":\"" + publicId + "\",\"removalReason\":\"제외 사유\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.targetUserId").value(publicId))
                .andExpect(jsonPath("$.data.participantStatus").value("REMOVED"))
                .andExpect(jsonPath("$.data.status").value("OPEN"));
        ArgumentCaptor<RecruitmentParticipantRemoveRequestDto> request =
                ArgumentCaptor.forClass(RecruitmentParticipantRemoveRequestDto.class);
        verify(service).removeParticipant(eq(2L), eq(11L), request.capture());
        assertEquals(publicId, request.getValue().getTargetUserId());
        assertEquals("제외 사유", request.getValue().getRemovalReason());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void emptyPublicIdIsRejectedBeforeService(String id) throws Exception {
        String jsonId = id == null ? "null" : "\"" + id + "\"";
        mvc.perform(post("/api/recruitments/11/participants/remove")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetUserId\":" + jsonId + ",\"removalReason\":\"제외 사유\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void emptyRemovalReasonIsRejectedBeforeService() throws Exception {
        mvc.perform(post("/api/recruitments/11/participants/remove")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetUserId\":\"" + publicId + "\",\"removalReason\":\"   \"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
