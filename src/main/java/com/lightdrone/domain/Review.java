package com.lightdrone.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "reviews")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Review extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "review_seq")
    @SequenceGenerator(name = "review_seq", sequenceName = "review_seq", allocationSize = 1)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 카테고리: 드론 구매후기 / A/S 후기 / 교육 후기 / 기타 */
    @Column(nullable = false, length = 30)
    @Builder.Default
    private String category = "기타";

    /** 후기 대상 구매 제품 (회원이 실제 구매한 제품) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    /** 작성 시점 제품명 스냅샷 (목록·상세에서 LAZY 없이 표시) */
    @Column(length = 200)
    private String productName;

    /** 대표 이미지 (첫 번째 첨부 — 목록 썸네일/하위호환용) */
    @Column(length = 500)
    private String imageUrl;

    /** 첨부 이미지 전체 (다중 첨부) */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "review_images", joinColumns = @JoinColumn(name = "review_id"))
    @Column(name = "image_url", length = 500)
    @OrderColumn(name = "sort_order")
    @Builder.Default
    private List<String> imageUrls = new ArrayList<>();

    /** 별점 (1~5) */
    @Builder.Default
    private Integer rating = 5;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @Builder.Default
    private int viewCount = 0;

    /** 관리자 숨김 여부 */
    @Builder.Default
    private boolean visible = true;

    /** 관리자 답글 */
    @Column(columnDefinition = "TEXT")
    private String adminReply;

    /** 관리자 답글 작성일시 */
    private java.time.LocalDateTime adminReplyAt;

    public void incrementViewCount() {
        this.viewCount++;
    }
}
