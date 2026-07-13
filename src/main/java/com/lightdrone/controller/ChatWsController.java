package com.lightdrone.controller;

import com.lightdrone.dto.ChatMessageDto;
import com.lightdrone.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class ChatWsController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    public record ChatSendRequest(Long roomId, String content) {}

    /** 클라이언트가 /app/chat/send 로 보낸 메시지를 저장하고 구독자에게 브로드캐스트 */
    @MessageMapping("/chat/send")
    public void send(@Payload ChatSendRequest req, Principal principal) {
        if (principal == null || req == null || req.roomId() == null) return;
        ChatMessageDto saved = chatService.sendMessage(req.roomId(), principal.getName(), req.content());
        // 해당 방 구독자에게 메시지 전달
        messagingTemplate.convertAndSend("/topic/chat/room/" + req.roomId(), saved);
        // 관리자 목록 갱신(최근 메시지순 정렬 반영)
        messagingTemplate.convertAndSend("/topic/chat/admin", "refresh");
    }
}
