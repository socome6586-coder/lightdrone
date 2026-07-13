package com.lightdrone.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "qna")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Qna extends BaseEntity implements Ownable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "qna_seq")
    @SequenceGenerator(name = "qna_seq", sequenceName = "qna_seq", allocationSize = 1)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false, length = 100)
    private String password;

    /** 세부 카테고리: 상품문의 / 결제문의 / 견적문의 / 배송문의 / 기타 */
    @Column(length = 20)
    @Builder.Default
    private String category = "기타";

    /** 비공개 여부: true = 비밀번호 필요, false = 공개 */
    @Builder.Default
    private boolean secret = true;

    @Builder.Default
    private int viewCount = 0;

    @Builder.Default
    private boolean answered = false;

    @Column(columnDefinition = "TEXT")
    private String answer;

    /** 레거시 단일 첨부 이미지 (기존 데이터 호환용) */
    @Column(length = 500)
    private String imageUrl;

    /** 첨부 이미지 (여러 장) */
    @ElementCollection
    @CollectionTable(name = "qna_images", joinColumns = @JoinColumn(name = "qna_id"))
    @Column(name = "image_url", length = 500)
    @Builder.Default
    private List<String> imageUrls = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    public void incrementViewCount() {
        this.viewCount++;
    }

    /** 레거시 단일 이미지 + 다중 이미지를 합친 전체 첨부 목록 */
    @Transient
    public List<String> getAllImageUrls() {
        List<String> all = new ArrayList<>();
        if (imageUrl != null && !imageUrl.isBlank()) all.add(imageUrl);
        if (imageUrls != null) all.addAll(imageUrls);
        return all;
    }
}
