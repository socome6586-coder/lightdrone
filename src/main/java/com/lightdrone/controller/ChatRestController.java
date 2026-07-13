package com.lightdrone.controller;

import com.lightdrone.domain.enums.ChatStatus;
import com.lightdrone.dto.ChatMessageDto;
import com.lightdrone.dto.ChatRoomDto;
import com.lightdrone.repository.ChatRoomRepository;
import com.lightdrone.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatRestController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatRoomRepository chatRoomRepository;

    /** 회원: 내 채팅방(없으면 생성) + 메시지 목록 */
    @GetMapping("/my-room")
    public ResponseEntity<?> myRoom(@AuthenticationPrincipal UserDetails user) {
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        ChatRoomDto room = chatService.getOrCreateMyRoom(user.getUsername());
        List<ChatMessageDto> messages = chatService.getMessages(room.id(), user.getUsername());
        // 관리자에게 새 방 알림(목록 갱신)
        messagingTemplate.convertAndSend("/topic/chat/admin", "refresh");
        return ResponseEntity.ok(Map.of("room", room, "messages", messages));
    }

    /** 방의 메시지 목록 (회원/담당 관리자) */
    @GetMapping("/rooms/{id}/messages")
    public ResponseEntity<?> messages(@PathVariable Long id,
                                      @AuthenticationPrincipal UserDetails user) {
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(chatService.getMessages(id, user.getUsername()));
    }

    /** 관리자: 대기 중인 채팅방 수 (배지 표시용 — 인증 부담 없이 폴링 가능) */
    @GetMapping("/admin/pending-count")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> pendingCount() {
        long count = chatRoomRepository.countByStatus(ChatStatus.WAITING);
        return ResponseEntity.ok(Map.of("count", count));
    }

    /** 관리자: 진행중인 채팅방 목록 */
    @GetMapping("/admin/rooms")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> adminRooms(@AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(chatService.listAdminRooms(user.getUsername()));
    }

    /** 관리자: 채팅방 점유(독점 잠금) */
    @PostMapping("/admin/rooms/{id}/claim")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> claim(@PathVariable Long id,
                                   @AuthenticationPrincipal UserDetails user) {
        try {
            ChatRoomDto room = chatService.claimRoom(id, user.getUsername());
            messagingTemplate.convertAndSend("/topic/chat/admin", "refresh");
            return ResponseEntity.ok(room);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
        }
    }

    /** 관리자/회원: 채팅방 종료 */
    @PostMapping("/admin/rooms/{id}/close")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> close(@PathVariable Long id,
                                   @AuthenticationPrincipal UserDetails user) {
        chatService.closeRoom(id, user.getUsername());
        messagingTemplate.convertAndSend("/topic/chat/room/" + id, Map.of("type", "closed"));
        messagingTemplate.convertAndSend("/topic/chat/admin", "refresh");
        return ResponseEntity.ok(Map.of("ok", true));
    }
}
