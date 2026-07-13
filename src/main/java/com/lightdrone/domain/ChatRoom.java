package com.lightdrone.domain;

import com.lightdrone.domain.enums.ChatStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 1:1 실시간 상담 채팅방.
 * 한 회원당 종료되지 않은 채팅방은 1개만 유지한다.
 * admin 필드가 "관리자 독점 잠금" 역할을 한다(null이면 대기중, 값이 있으면 해당 관리자만 참여 가능).
 */
@Entity
@Table(name = "chat_rooms")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatRoom extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "chat_room_seq")
    @SequenceGenerator(name = "chat_room_seq", sequenceName = "chat_room_seq", allocationSize = 1)
    private Long id;

    /** 상담을 요청한 회원(고객) */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id")
    private Member member;

    /** 상담을 점유한 관리자 (null = 대기중, 다른 관리자 참여 가능) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id")
    private Member admin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ChatStatus status = ChatStatus.WAITING;

    /** 마지막 메시지 시각 (목록 정렬용) */
    private LocalDateTime lastMessageAt;
}
