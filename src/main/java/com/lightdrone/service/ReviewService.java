package com.lightdrone.service;

import com.lightdrone.domain.Member;
import com.lightdrone.domain.Order;
import com.lightdrone.domain.OrderItem;
import com.lightdrone.domain.Product;
import com.lightdrone.domain.enums.OrderStatus;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.lightdrone.domain.Review;
import com.lightdrone.dto.PurchasedProductDto;
import com.lightdrone.dto.ReviewDto;
import com.lightdrone.repository.OrderRepository;
import com.lightdrone.repository.ProductRepository;
import com.lightdrone.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {

    /** "구매 완료"로 인정하는 주문 상태 (결제 이후 단계) */
    private static final Set<OrderStatus> PURCHASED_STATUSES = EnumSet.of(
            OrderStatus.PAID, OrderStatus.PREPARING, OrderStatus.SHIPPING, OrderStatus.DELIVERED);

    private final ReviewRepository reviewRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final FileStorageService fileStorageService;
    private final HtmlSanitizer htmlSanitizer;

    /** 회원이 실제 구매한(결제 이후) 제품 목록 — 중복 제거, 최근 순 */
    public List<PurchasedProductDto> getPurchasedProducts(Member member) {
        if (member == null) return List.of();
        Map<Long, PurchasedProductDto> map = new LinkedHashMap<>();
        for (Order order : orderRepository.findByMemberWithItems(member)) {
            if (!PURCHASED_STATUSES.contains(order.getStatus())) continue;
            for (OrderItem item : order.getItems()) {
                Product p = item.getProduct();
                if (p == null) continue;
                map.putIfAbsent(p.getId(), new PurchasedProductDto(p.getId(), p.getName(), p.getImageUrl()));
            }
        }
        return new ArrayList<>(map.values());
    }

    /** 사용자용 목록 (숨김 제외) — 첨부 사진(imageUrls)은 페이징과 함께 페치조인하면
     *  메모리 페이징 문제가 있어, 트랜잭션 안에서 페이지 항목별로만 초기화한다. */
    @Transactional(readOnly = true)
    public Page<Review> getList(Pageable pageable) {
        Page<Review> page = reviewRepository.findByVisibleTrueOrderByCreatedAtDesc(pageable);
        page.getContent().forEach(r -> r.getImageUrls().size()); // LAZY 컬렉션 초기화
        return page;
    }

    /** 상품별 공개 후기 수 */
    public long getProductReviewCount(Long productId) {
        if (productId == null) return 0;
        return reviewRepository.countByProductIdAndVisibleTrue(productId);
    }

    /** 상품별 공개 후기 목록 (상품 상세 리뷰 탭용) */
    public List<Review> getProductReviews(Long productId) {
        if (productId == null) return List.of();
        return reviewRepository.findVisibleByProductId(productId);
    }

    /** 여러 상품의 공개 후기 수 (목록 페이지용) — productId → count */
    public Map<Long, Long> getReviewCounts(List<Long> productIds) {
        Map<Long, Long> map = new LinkedHashMap<>();
        if (productIds == null || productIds.isEmpty()) return map;
        for (Object[] row : reviewRepository.countVisibleGroupedByProduct(productIds)) {
            map.put((Long) row[0], (Long) row[1]);
        }
        return map;
    }

    /** 관리자용 전체 목록 */
    public Page<Review> getListForAdmin(Pageable pageable) {
        return reviewRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    /** 통합검색용 — 제목·내용·제품명 부분일치 (최신순, limit 건) */
    public List<Review> searchForAdmin(String keyword, int limit) {
        if (keyword == null || keyword.isBlank()) return List.of();
        return reviewRepository.searchForAdmin(keyword.trim(),
                org.springframework.data.domain.PageRequest.of(0, limit));
    }

    public Review getById(Long id) {
        return reviewRepository.findByIdWithMember(id)
                .orElseThrow(() -> new IllegalArgumentException("후기를 찾을 수 없습니다."));
    }

    @Transactional
    public Review create(ReviewDto dto, Member member) {
        // 구매한 제품만 후기 작성 가능
        Product product = resolvePurchasedProduct(dto.getProductId(), member);

        List<String> imageUrls = storeImages(dto.getImageFiles());

        String category = (dto.getCategory() == null || dto.getCategory().isBlank()) ? "기타" : dto.getCategory();
        return reviewRepository.save(Review.builder()
                .title(dto.getTitle())
                .content(htmlSanitizer.clean(dto.getContent()))
                .category(category)
                .product(product)
                .productName(product != null ? product.getName() : null)
                .rating(normalizeRating(dto.getRating()))
                .imageUrl(imageUrls.isEmpty() ? null : imageUrls.get(0))
                .imageUrls(imageUrls)
                .member(member)
                .build());
    }

    @Transactional
    public void update(Long id, ReviewDto dto) {
        Review review = getById(id);
        review.setTitle(dto.getTitle());
        review.setContent(htmlSanitizer.clean(dto.getContent()));
        String category = (dto.getCategory() == null || dto.getCategory().isBlank()) ? "기타" : dto.getCategory();
        review.setCategory(category);
        review.setRating(normalizeRating(dto.getRating()));

        // 새 이미지가 첨부된 경우에만 기존 이미지 전체 교체
        List<String> newImages = storeImages(dto.getImageFiles());
        if (!newImages.isEmpty()) {
            deleteImages(review);
            review.getImageUrls().clear();
            review.getImageUrls().addAll(newImages);
            review.setImageUrl(newImages.get(0));
        }
    }

    /** productId가 해당 회원의 구매 제품인지 검증 후 Product 반환 (null 허용 = 제품 미선택) */
    private Product resolvePurchasedProduct(Long productId, Member member) {
        if (productId == null) {
            throw new IllegalArgumentException("구매한 제품을 선택해주세요.");
        }
        boolean purchased = getPurchasedProducts(member).stream()
                .anyMatch(pp -> pp.getId().equals(productId));
        if (!purchased) {
            throw new IllegalArgumentException("실제 구매한 제품만 후기를 작성할 수 있습니다.");
        }
        return productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("제품을 찾을 수 없습니다."));
    }

    /** 다중 이미지 저장 — 빈 파일은 건너뜀 */
    private List<String> storeImages(List<MultipartFile> files) {
        List<String> urls = new ArrayList<>();
        if (files == null) return urls;
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) continue;
            try {
                urls.add(fileStorageService.store(file));
            } catch (Exception e) {
                throw new IllegalArgumentException("이미지 업로드 실패: " + e.getMessage());
            }
        }
        return urls;
    }

    /** 후기에 연결된 모든 이미지 스토리지 삭제 */
    private void deleteImages(Review review) {
        if (review.getImageUrls() != null) {
            for (String url : review.getImageUrls()) {
                fileStorageService.delete(url);
            }
        }
        // imageUrls에 포함되지 않은 레거시 단일 이미지도 삭제
        if (review.getImageUrl() != null
                && (review.getImageUrls() == null || !review.getImageUrls().contains(review.getImageUrl()))) {
            fileStorageService.delete(review.getImageUrl());
        }
    }

    /** 별점 보정: null/범위초과 시 1~5로 클램프 (기본 5) */
    private int normalizeRating(Integer rating) {
        if (rating == null) return 5;
        if (rating < 1) return 1;
        if (rating > 5) return 5;
        return rating;
    }

    @Transactional
    public void incrementViewCount(Long id) {
        getById(id).incrementViewCount();
    }

    @Transactional
    public void toggleVisible(Long id) {
        Review review = getById(id);
        review.setVisible(!review.isVisible());
    }

    @Transactional
    public void saveAdminReply(Long reviewId, String reply) {
        Review review = getById(reviewId);
        if (reply == null || reply.isBlank()) {
            review.setAdminReply(null);
            review.setAdminReplyAt(null);
        } else {
            review.setAdminReply(reply);
            review.setAdminReplyAt(java.time.LocalDateTime.now());
        }
    }

    @Transactional
    public void delete(Long id) {
        Review review = getById(id);
        deleteImages(review);
        reviewRepository.delete(review);
    }

    /** 일괄 삭제 */
    @Transactional
    public int bulkDelete(List<Long> ids) {
        int count = 0;
        for (Long id : ids) {
            try { delete(id); count++; } catch (Exception ignored) {}
        }
        return count;
    }
}
