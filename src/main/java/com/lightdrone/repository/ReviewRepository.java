package com.lightdrone.repository;

import com.lightdrone.domain.Member;
import com.lightdrone.domain.Product;
import com.lightdrone.domain.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    /** 사용자용: 숨김 제외, member·product 즉시 로딩 (썸네일용 상품 이미지) */
    @Query(value = "SELECT r FROM Review r LEFT JOIN FETCH r.member LEFT JOIN FETCH r.product WHERE r.visible = true ORDER BY r.createdAt DESC",
           countQuery = "SELECT COUNT(r) FROM Review r WHERE r.visible = true")
    Page<Review> findByVisibleTrueOrderByCreatedAtDesc(Pageable pageable);

    /** 관리자용: 전체, member 즉시 로딩 */
    @Query(value = "SELECT r FROM Review r LEFT JOIN FETCH r.member ORDER BY r.createdAt DESC",
           countQuery = "SELECT COUNT(r) FROM Review r")
    Page<Review> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** 단건 조회 + member·product·이미지 즉시 로딩 (OSIV off 대응) */
    @Query("SELECT r FROM Review r LEFT JOIN FETCH r.member LEFT JOIN FETCH r.product LEFT JOIN FETCH r.imageUrls WHERE r.id = :id")
    Optional<Review> findByIdWithMember(@Param("id") Long id);

    /** 상품별 공개 후기 수 */
    long countByProductIdAndVisibleTrue(Long productId);

    /** 상품 삭제 시 후기의 product 참조를 null 로 초기화 (후기 본문·productName 스냅샷은 보존) */
    @Modifying
    @Query("UPDATE Review r SET r.product = null WHERE r.product = :product")
    void clearProductReference(@Param("product") Product product);

    /** 상품별 공개 후기 목록 — member·이미지 즉시 로딩 (상품 상세 리뷰 탭용) */
    @Query("SELECT DISTINCT r FROM Review r LEFT JOIN FETCH r.member LEFT JOIN FETCH r.imageUrls "
         + "WHERE r.product.id = :productId AND r.visible = true ORDER BY r.createdAt DESC")
    List<Review> findVisibleByProductId(@Param("productId") Long productId);

    /** 여러 상품의 공개 후기 수 — [productId, count] 묶음 (목록 페이지 일괄 집계용) */
    @Query("SELECT r.product.id, COUNT(r) FROM Review r "
         + "WHERE r.visible = true AND r.product.id IN :productIds GROUP BY r.product.id")
    List<Object[]> countVisibleGroupedByProduct(@Param("productIds") List<Long> productIds);

    /** 통합검색용 — 제목·내용·제품명 부분일치 (최신순, member 즉시 로딩) */
    @Query("SELECT r FROM Review r LEFT JOIN FETCH r.member WHERE "
         + "LOWER(r.title) LIKE LOWER(CONCAT('%', :kw, '%')) "
         + "OR LOWER(r.content) LIKE LOWER(CONCAT('%', :kw, '%')) "
         + "OR LOWER(r.productName) LIKE LOWER(CONCAT('%', :kw, '%')) "
         + "ORDER BY r.createdAt DESC")
    List<Review> searchForAdmin(@Param("kw") String kw, Pageable pageable);

    /** 알림용: 특정 회원의 adminReply != null 후기, 최근 30일 이내 */
    @Query("SELECT r FROM Review r WHERE r.member = :member AND r.adminReply IS NOT NULL AND r.createdAt > :after ORDER BY r.createdAt DESC")
    List<Review> findByMemberWithAdminReplyAfter(@Param("member") Member member,
                                                 @Param("after") LocalDateTime after);

    /** 회원 삭제 전 처리 */
    void deleteAllByMember(Member member);
}
