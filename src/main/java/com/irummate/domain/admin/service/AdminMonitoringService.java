package com.irummate.domain.admin.service;

import com.irummate.domain.admin.dto.AdminMonitoringSummaryResponseDto;
import com.irummate.domain.admin.dto.AdminMonitoringUserResponseDto;
import com.irummate.domain.admin.dto.AdminMonitoringUsersResponseDto;
import com.irummate.domain.chat.entity.ChatRoomStatus;
import com.irummate.domain.chat.repository.ChatMessageRepository;
import com.irummate.domain.chat.repository.ChatRoomRepository;
import com.irummate.domain.matching.repository.MatchRepository;
import com.irummate.domain.user.entity.UserRole;
import com.irummate.domain.user.entity.UserStatus;
import com.irummate.domain.user.entity.Users;
import com.irummate.domain.user.repository.UsersRepository;
import com.irummate.global.util.HashIdsUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminMonitoringService {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final MatchRepository matchRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UsersRepository usersRepository;
    private final HashIdsUtils hashIdsUtils;

    public AdminMonitoringSummaryResponseDto getSummary() {
        LocalDateTime todayStart = LocalDate.now(SERVICE_ZONE).atStartOfDay();

        return new AdminMonitoringSummaryResponseDto(
                new AdminMonitoringSummaryResponseDto.MatchingSummary(
                        matchRepository.countHeartSent(),
                        matchRepository.countHeartMatched(),
                        matchRepository.countConfirmPending(),
                        matchRepository.countFinalConfirmed(),
                        matchRepository.countClosed()
                ),
                new AdminMonitoringSummaryResponseDto.ChatSummary(
                        chatRoomRepository.countActiveUserRooms(),
                        chatRoomRepository.countActiveUserRoomsByStatus(ChatRoomStatus.OPEN),
                        chatRoomRepository.countActiveUserRoomsByStatus(ChatRoomStatus.CLOSED),
                        chatMessageRepository.countActiveUserRoomMessages(),
                        chatMessageRepository.countActiveUserRoomMessagesByCreatedAtGreaterThanEqual(todayStart)
                ),
                LocalDateTime.now(SERVICE_ZONE)
        );
    }

    public AdminMonitoringUsersResponseDto getUsers() {
        return new AdminMonitoringUsersResponseDto(
                usersRepository.findAllByRoleAndStatusWithDetails(UserRole.USER, UserStatus.ACTIVE)
                        .stream()
                        .map(this::toMonitoringUser)
                        .toList()
        );
    }

    private AdminMonitoringUserResponseDto toMonitoringUser(Users user) {
        Long userId = user.getId();

        return new AdminMonitoringUserResponseDto(
                hashIdsUtils.encode(userId),
                user.getUserDetails() == null ? null : user.getUserDetails().getRealName(),
                user.getNickname(),
                matchRepository.countHeartSentByUserId(userId),
                matchRepository.countHeartReceivedByUserId(userId),
                matchRepository.countHeartMatchedByUserId(userId),
                matchRepository.existsFinalConfirmedByUserId(userId),
                chatRoomRepository.countByParticipantIdAndStatus(userId, ChatRoomStatus.OPEN),
                chatRoomRepository.countByParticipantIdAndStatus(userId, ChatRoomStatus.CLOSED)
        );
    }
}
