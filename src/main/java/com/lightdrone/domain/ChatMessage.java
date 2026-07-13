package com.lightdrone.domain;

import com.lightdrone.domain.enums.Role;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "chat_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "chat_message_seq")
    @SequenceGenerator(name = "chat_message_seq", sequenceName = "chat_message_seq", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id")
    private ChatRoom room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id")
    private Member sender;

    /** 메시지 작성 당시 발신자 역할(USER/ADMIN) — 화면 좌우 정렬에 사용 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Role senderRole;

    @Column(nullable = false, length = 2000)
    private String content;
}
