package com.irummate.chat;

import com.irummate.domain.chat.dto.ChatEventType;
import com.irummate.domain.chat.dto.ChatReadEventDto;
import com.irummate.domain.chat.dto.ChatReadRequestDto;
import com.irummate.domain.chat.dto.ChatReadResponseDto;
import com.irummate.domain.chat.dto.ChatReadResult;
import com.irummate.domain.chat.controller.ChatController;
import com.irummate.domain.chat.service.ChatService;
import com.irummate.domain.chat.service.RecruitmentChatDelivery;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatControllerTest {

    private final ChatService chatService = mock(ChatService.class);
    private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
    private final ChatController controller = new ChatController(chatService, messagingTemplate, mock(RecruitmentChatDelivery.class));

    @Test
    void publishesReadEventWhenMessagesWereUpdated() {
        ChatReadEventDto event = new ChatReadEventDto(ChatEventType.READ, 7L, "reader", 105L);
        when(chatService.markMessagesAsRead(7L, 2L, 105L))
                .thenReturn(new ChatReadResult(new ChatReadResponseDto(true), event));

        controller.markMessagesAsRead(7L, 2L, new ChatReadRequestDto(105L));

        verify(messagingTemplate).convertAndSend("/topic/room/7", event);
    }

    @Test
    void doesNotPublishReadEventWhenNothingWasUpdated() {
        when(chatService.markMessagesAsRead(7L, 2L, 105L))
                .thenReturn(new ChatReadResult(new ChatReadResponseDto(true), null));

        controller.markMessagesAsRead(7L, 2L, new ChatReadRequestDto(105L));

        verifyNoInteractions(messagingTemplate);
    }
}
