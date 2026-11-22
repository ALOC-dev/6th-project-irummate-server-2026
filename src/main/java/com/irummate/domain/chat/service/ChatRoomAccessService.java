package com.irummate.domain.chat.service;

import com.irummate.domain.chat.repository.ChatRoomMemberRepository;
import com.irummate.domain.chat.repository.ChatRoomRepository;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatRoomAccessService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;

    public void requireCurrentMember(Long roomId, Long userId) {
        if (chatRoomMemberRepository.existsByRoom_IdAndUser_IdAndLeftAtIsNull(roomId, userId)) {
            return;
        }
        if (!chatRoomRepository.existsById(roomId)) {
            throw new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND);
        }
        throw new BusinessException(ErrorCode.NOT_CHAT_PARTICIPANT);
    }
}
