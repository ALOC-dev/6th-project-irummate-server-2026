package com.irummate.domain.chat.service;

import com.irummate.domain.chat.entity.ChatRoom;
import com.irummate.domain.chat.entity.ChatRoomMember;
import com.irummate.domain.chat.repository.ChatMessageRepository;
import com.irummate.domain.chat.repository.ChatRoomMemberRepository;
import com.irummate.domain.chat.repository.ChatRoomRepository;
import com.irummate.domain.recruitment.entity.Recruitments;
import com.irummate.domain.user.entity.Users;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class RecruitmentChatRoomService {

    private final ChatRoomRepository rooms;
    private final ChatRoomMemberRepository members;
    private final ChatMessageRepository messages;
    private final ChatSocketSessions sockets;

    public void create(Recruitments recruitment, Users host, LocalDateTime now) {
        ChatRoom room = rooms.save(ChatRoom.forRecruitment(recruitment));
        members.save(ChatRoomMember.join(room, host, now));
    }

    public void join(Long recruitmentId, Users user, LocalDateTime now) {
        ChatRoom room = lockRoom(recruitmentId);
        Long cursor = messages.findLastMessageId(room.getId()).orElse(null);
        members.save(ChatRoomMember.join(room, user, now, cursor));
    }

    public void leave(Long recruitmentId, Long userId, LocalDateTime now) {
        ChatRoom room = lockRoom(recruitmentId);
        if (members.leaveRoom(room.getId(), userId, now) != 1) {
            throw new BusinessException(ErrorCode.NOT_CHAT_PARTICIPANT);
        }
        sockets.revokeMembership(room.getId(), userId);
    }

    public void reschedule(Long recruitmentId, LocalDateTime activityAt) {
        lockRoom(recruitmentId).rescheduleRecruitment(activityAt);
    }

    public void close(Long recruitmentId) {
        lockRoom(recruitmentId).close();
    }

    private ChatRoom lockRoom(Long recruitmentId) {
        return rooms.findByRecruitmentIdForUpdate(recruitmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));
    }
}
