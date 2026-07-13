package com.lightdrone.repository;

import com.lightdrone.domain.ChatMessage;
import com.lightdrone.domain.ChatRoom;
import com.lightdrone.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByRoomOrderByCreatedAtAscIdAsc(ChatRoom room);

    void deleteAllByRoom(ChatRoom room);

    /** 회원 삭제 시: 해당 회원이 보낸 모든 메시지 삭제 (sender FK NOT NULL 제약 해제용) */
    void deleteAllBySender(Member sender);
}
