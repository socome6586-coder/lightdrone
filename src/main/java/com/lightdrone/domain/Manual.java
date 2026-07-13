package com.lightdrone.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "manuals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Manual extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "manual_seq")
    @SequenceGenerator(name = "manual_seq", sequenceName = "manual_seq", allocationSize = 1)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(length = 50)
    private String category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member author;

    @Column(nullable = false)
    @Builder.Default
    private int viewCount = 0;

    @Column(nullable = false)
    @Builder.Default
    private boolean pinned = false;

    public void incrementViewCount() {
        this.viewCount++;
    }
}
