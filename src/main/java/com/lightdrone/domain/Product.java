package com.lightdrone.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "product_seq")
    @SequenceGenerator(name = "product_seq", sequenceName = "product_seq", allocationSize = 1)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** 판매가 (원) */
    @Column(nullable = false)
    private Long price;

    /** 대표 이미지 URL */
    private String imageUrl;

    /** 카테고리 (예: 농업용, 산업용, 교육용 ...) */
    @Column(length = 100)
    private String category;

    /** 세부 카테고리 (예: 조립완성기체, K20 ...) */
    @Column(length = 100)
    private String subcategory;

    /** 카테고리 목록 (다중 선택 지원) */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "product_category_links", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "category_name", length = 100)
    @Builder.Default
    private List<String> categories = new ArrayList<>();

    @Builder.Default
    private int stock = 0;

    @Builder.Default
    private boolean available = true;

    /** 가격문의요망 여부 (true면 가격 대신 "가격문의요망" 표시) */
    @Builder.Default
    private boolean priceOnRequest = false;

    /** 무료배송 여부 (true면 배송료 0원) */
    @Builder.Default
    private boolean freeShipping = false;

    /** BEST 상품 여부 (true면 카드 우측 상단에 BEST 리본 표시) */
    @Builder.Default
    @Column(name = "is_best", columnDefinition = "boolean default false")
    private boolean best = false;

    /** NEW 상품 여부 (true면 카드 우측 상단에 NEW 리본 표시) */
    @Builder.Default
    @Column(name = "is_new", columnDefinition = "boolean default false")
    private boolean newProduct = false;

    /** 목록 카드 이미지 하단 라벨 표시 여부 (홈·products 목록 전용, 상세 페이지 미표시) */
    @Builder.Default
    @Column(name = "show_image_label", columnDefinition = "boolean default false")
    private boolean showImageLabel = false;

    /** 목록 카드 이미지 하단 라벨 텍스트 (예: "K30 키트") */
    @Column(name = "image_label", length = 100)
    private String imageLabel;

    /** 라벨 실제 노출 조건: 표시 체크 + 텍스트 존재 (템플릿 가독성용 헬퍼) */
    @jakarta.persistence.Transient
    public boolean isImageLabelVisible() {
        return showImageLabel && imageLabel != null && !imageLabel.isBlank();
    }

    /** 할인율 (0~100, %). 0이면 할인 없음.
     *  기존 행은 컬럼이 NULL일 수 있어 Integer로 둔다 (primitive면 NULL 주입 시 부팅 크래시). */
    @Builder.Default
    @Column(name = "discount_percent")
    private Integer discountPercent = 0;

    /** 할인율 — NULL이면 0으로 처리 (기존 데이터 호환). Lombok 기본 게터 대체 */
    public int getDiscountPercent() {
        return discountPercent != null ? discountPercent : 0;
    }

    /**
     * 표시용 대표가격 (원). 실제 결제 금액과 무관하게 상품 목록·상세 상단에 "보여주기만" 하는 가격.
     * 옵션으로 금액이 구성되는 상품(기본가 0원)이 목록에 "0원"으로 보이지 않도록 할 때 사용한다.
     * null 또는 0이면 미설정으로 보고 실제 판매가(effectivePrice)를 그대로 표시한다.
     */
    @Column(name = "display_price")
    private Long displayPrice;

    /** 화면에 표시할 대표가격. displayPrice가 설정돼 있으면 그 값, 아니면 실제 판매가. */
    @jakarta.persistence.Transient
    public Long getHeadlinePrice() {
        return (displayPrice != null && displayPrice > 0) ? displayPrice : getEffectivePrice();
    }

    /** 슬라이더 대표 이미지 목록 (상품 상세 상단 캐러셀) */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "product_main_images",
                     joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "image_url", length = 500)
    @OrderColumn(name = "display_order")
    @Builder.Default
    private List<String> mainImages = new ArrayList<>();

    /** 내용 이미지 목록 (제품 설명에 표시) */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "product_content_images",
                     joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "image_url", length = 500)
    @OrderColumn(name = "display_order")
    @Builder.Default
    private List<String> contentImages = new ArrayList<>();

    @Builder.Default
    private int sortOrder = 0;

    /** 내용 이미지 레이아웃 (콤마 구분: full=전체너비, half=절반너비) */
    @Column(name = "content_image_layout", length = 2000)
    private String contentImageLayout;

    /** 내용 이미지 배경색 (예: #000000) */
    @Column(name = "content_bg_color", length = 20)
    private String contentBgColor;

    /** 동영상 URL (YouTube watch/short/embed 또는 직접 링크) */
    @Column(name = "video_url", length = 500)
    private String videoUrl;

    /** 제조사/원산지 */
    @Column(name = "manufacturer", length = 200)
    private String manufacturer;

    /** 상품코드 */
    @Column(name = "product_code", length = 100)
    private String productCode;

    /** 상품 옵션 목록 (이름 + 추가금액) */
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("sortOrder ASC, id ASC")
    @Builder.Default
    private List<ProductOption> options = new ArrayList<>();

    /** 저장/수정 시 category 필드를 categories 첫 번째 값으로 동기화 */
    @PrePersist
    @PreUpdate
    private void syncPrimaryCategory() {
        this.category = (categories != null && !categories.isEmpty()) ? categories.get(0) : null;
    }

    /** 할인 적용 후 실제 판매가 (할인 없거나 가격문의면 원가 반환) */
    public Long getEffectivePrice() {
        if (price == null) return 0L;
        int pct = getDiscountPercent();
        if (pct <= 0 || priceOnRequest) return price;
        long discounted = Math.round(price * (100 - pct) / 100.0);
        return discounted < 0 ? 0L : discounted;
    }

    /** 할인 표시 여부 (정가 취소선 + 할인 배지 노출 조건) */
    public boolean isOnSale() {
        return getDiscountPercent() > 0 && !priceOnRequest && price != null && price > 0;
    }

    /**
     * 상세 페이지 슬라이더용 이미지 목록.
     * 대표사진(imageUrl)을 항상 첫 번째로 포함하고, 슬라이더 이미지(mainImages) 중
     * 대표사진과 중복되는 항목은 제외하여 이어붙인다.
     * → 관리자가 슬라이더에 대표사진을 따로 올리지 않아도 슬라이더 첫 장에 대표사진이 표시된다.
     */
    @jakarta.persistence.Transient
    public List<String> getSliderImages() {
        List<String> result = new ArrayList<>();
        if (imageUrl != null && !imageUrl.isBlank()) {
            result.add(imageUrl);
        }
        if (mainImages != null) {
            for (String img : mainImages) {
                if (img != null && !img.isBlank() && !img.equals(imageUrl)) {
                    result.add(img);
                }
            }
        }
        return result;
    }

    /** 로드 시 기존 단일 category → categories 마이그레이션 (DB에는 반영 안 됨, 뷰 전용) */
    @PostLoad
    private void migrateLegacyCategory() {
        if ((categories == null || categories.isEmpty()) && category != null && !category.isBlank()) {
            if (categories == null) categories = new ArrayList<>();
            if (!categories.contains(category)) categories.add(category);
        }
    }

    /**
     * 동영상 임베드 URL 반환.
     * YouTube watch URL / short URL → embed URL 자동 변환
     */
    public String getVideoEmbedUrl() {
        if (videoUrl == null || videoUrl.isBlank()) return null;
        // https://www.youtube.com/watch?v=VIDEO_ID(&...)
        if (videoUrl.contains("youtube.com/watch")) {
            int idx = videoUrl.indexOf("v=");
            if (idx >= 0) {
                String id = videoUrl.substring(idx + 2);
                if (id.contains("&")) id = id.substring(0, id.indexOf("&"));
                return "https://www.youtube.com/embed/" + id;
            }
        }
        // https://youtu.be/VIDEO_ID(?...)
        if (videoUrl.contains("youtu.be/")) {
            String id = videoUrl.substring(videoUrl.lastIndexOf("/") + 1);
            if (id.contains("?")) id = id.substring(0, id.indexOf("?"));
            return "https://www.youtube.com/embed/" + id;
        }
        // 이미 embed URL이거나 그 외 직접 링크 → 그대로 반환
        return videoUrl;
    }
}
