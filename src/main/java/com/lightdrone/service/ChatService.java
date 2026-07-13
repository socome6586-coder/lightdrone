package com.lightdrone.service;

import com.lightdrone.domain.ChatMessage;
import com.lightdrone.domain.ChatRoom;
import com.lightdrone.domain.Member;
import com.lightdrone.domain.enums.ChatStatus;
import com.lightdrone.domain.enums.Role;
import com.lightdrone.dto.ChatMessageDto;
import com.lightdrone.dto.ChatRoomDto;
import com.lightdrone.repository.ChatMessageRepository;
import com.lightdrone.repository.ChatRoomRepository;
import com.lightdrone.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final MemberRepository memberRepository;

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("MM/dd HH:mm");

    private Member member(String username) {
        return memberRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다."));
    }

    private ChatRoom room(Long roomId) {
        return chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));
    }

    /** 회원 본인 또는 (이 방을 점유한) 관리자만 접근 가능 */
    private boolean canAccess(ChatRoom r, Member m) {
        if (r.getMember() != null && r.getMember().getId().equals(m.getId())) return true;
        return m.getRole() == Role.ADMIN
                && r.getAdmin() != null
                && r.getAdmin().getId().equals(m.getId());
    }

    /** 회원의 진행중인 방을 가져오거나 없으면 새로 만든다. */
    @Transactional
    public ChatRoomDto getOrCreateMyRoom(String username) {
        Member me = member(username);
        ChatRoom r = chatRoomRepository
                .findFirstByMemberAndStatusNotOrderByIdDesc(me, ChatStatus.CLOSED)
                .orElseGet(() -> chatRoomRepository.save(ChatRoom.builder()
                        .member(me)
                        .status(ChatStatus.WAITING)
                        .lastMessageAt(LocalDateTime.now())
                        .build()));
        return toRoomDto(r, me.getId());
    }

    public List<ChatMessageDto> getMessages(Long roomId, String username) {
        Member me = member(username);
        ChatRoom r = room(roomId);
        if (!canAccess(r, me)) {
            throw new IllegalStateException("이 채팅방에 접근할 수 없습니다.");
        }
        return chatMessageRepository.findByRoomOrderByCreatedAtAscIdAsc(r).stream()
                .map(this::toMessageDto)
                .toList();
    }

    /** 관리자 채팅 목록 (종료되지 않은 모든 방) */
    public List<ChatRoomDto> listAdminRooms(String adminUsername) {
        Member admin = member(adminUsername);
        return chatRoomRepository
                .findByStatusNotOrderByLastMessageAtDescIdDesc(ChatStatus.CLOSED).stream()
                .map(r -> toRoomDto(r, admin.getId()))
                .toList();
    }

    /**
     * 관리자 독점 점유. 이미 다른 관리자가 점유했으면 예외.
     * 본인이 이미 점유한 방이면 그대로 통과.
     */
    @Transactional
    public ChatRoomDto claimRoom(Long roomId, String adminUsername) {
        Member admin = member(adminUsername);
        ChatRoom r = room(roomId);
        if (r.getAdmin() != null && r.getAdmin().getId().equals(admin.getId())) {
            return toRoomDto(r, admin.getId()); // 이미 내가 점유
        }
        int updated = chatRoomRepository.claim(roomId, admin);
        if (updated == 0) {
            throw new IllegalStateException("이미 다른 관리자가 상담 중인 채팅입니다.");
        }
        return toRoomDto(room(roomId), admin.getId());
    }

    @Transactional
    public void closeRoom(Long roomId, String username) {
        Member me = member(username);
        ChatRoom r = room(roomId);
        if (!canAccess(r, me)) {
            throw new IllegalStateException("이 채팅방을 종료할 권한이 없습니다.");
        }
        r.setStatus(ChatStatus.CLOSED);
    }

    @Transactional
    public ChatMessageDto sendMessage(Long roomId, String username, String content) {
        Member me = member(username);
        ChatRoom r = room(roomId);
        if (!canAccess(r, me)) {
            throw new IllegalStateException("이 채팅방에 메시지를 보낼 수 없습니다.");
        }
        if (r.getStatus() == ChatStatus.CLOSED) {
            throw new IllegalStateException("종료된 채팅방입니다.");
        }
        String text = content == null ? "" : content.trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("메시지를 입력해 주세요.");
        }
        if (text.length() > 2000) text = text.substring(0, 2000);

        ChatMessage msg = chatMessageRepository.save(ChatMessage.builder()
                .room(r)
                .sender(me)
                .senderRole(me.getRole())
                .content(text)
                .build());
        r.setLastMessageAt(LocalDateTime.now());
        return toMessageDto(msg);
    }

    public Long roomIdOf(Long roomId) {
        return room(roomId).getId();
    }

    private ChatRoomDto toRoomDto(ChatRoom r, Long viewerId) {
        Member admin = r.getAdmin();
        boolean claimedByMe = admin != null && admin.getId().equals(viewerId);
        return new ChatRoomDto(
                r.getId(),
                r.getMember() != null ? r.getMember().getName() : "(알 수 없음)",
                r.getStatus().name(),
                admin != null ? admin.getId() : null,
                admin != null ? admin.getName() : null,
                claimedByMe,
                r.getLastMessageAt() != null ? r.getLastMessageAt().format(TS) : ""
        );
    }

    private ChatMessageDto toMessageDto(ChatMessage m) {
        return new ChatMessageDto(
                m.getId(),
                m.getRoom().getId(),
                m.getSenderRole().name(),
                m.getSender() != null ? m.getSender().getName() : "",
                m.getContent(),
                m.getCreatedAt() != null ? m.getCreatedAt().format(TS) : ""
        );
    }
}
