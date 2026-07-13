package com.lightdrone.repository;

import com.lightdrone.domain.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * 재고 차감 시 동시 주문으로 인한 Race Condition을 방지하기 위해
     * SELECT ... FOR UPDATE (비관적 락) 으로 상품 행을 잠금 처리합니다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);

    List<Product> findByAvailableTrueOrderBySortOrderAscIdDesc();
    Page<Product> findByAvailableTrueOrderBySortOrderAscIdDesc(Pageable pageable);

    List<Product> findByCategoryAndAvailableTrue(String category);
    Page<Product> findByCategoryAndAvailableTrue(String category, Pageable pageable);

    List<Product> findByCategoryAndSubcategoryAndAvailableTrue(String category, String subcategory);
    Page<Product> findByCategoryAndSubcategoryAndAvailableTrue(String category, String subcategory, Pageable pageable);

    List<Product> findAllByOrderBySortOrderAscIdDesc();
    Page<Product> findAllByOrderBySortOrderAscIdDesc(Pageable pageable);

    List<Product> findByNameContainingIgnoreCaseAndAvailableTrueOrderBySortOrderAscIdDesc(String keyword);
    Page<Product> findByNameContainingIgnoreCaseAndAvailableTrueOrderBySortOrderAscIdDesc(String keyword, Pageable pageable);

    // 공개 페이지: 제품명 또는 상품코드로 검색 (available 필터 포함)
    @Query("SELECT p FROM Product p WHERE p.available = true AND (" +
           "LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(p.productCode) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "ORDER BY p.sortOrder ASC, p.id DESC")
    List<Product> searchAvailableByNameOrCode(@Param("keyword") String keyword);

    @Query(value = "SELECT p FROM Product p WHERE p.available = true AND (" +
                   "LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
                   "LOWER(p.productCode) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
                   "ORDER BY p.sortOrder ASC, p.id DESC",
           countQuery = "SELECT COUNT(p) FROM Product p WHERE p.available = true AND (" +
                        "LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
                        "LOWER(p.productCode) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Product> searchAvailableByNameOrCode(@Param("keyword") String keyword, Pageable pageable);

    // ── 관리자 전용 (available 필터 없음) ─────────────────────────────
    Page<Product> findByCategoryOrderBySortOrderAscIdDesc(String category, Pageable pageable);
    Page<Product> findByNameContainingIgnoreCaseOrderBySortOrderAscIdDesc(String keyword, Pageable pageable);
    Page<Product> findByCategoryAndNameContainingIgnoreCaseOrderBySortOrderAscIdDesc(String category, String keyword, Pageable pageable);

    // 관리자: 제품명 또는 상품코드로 검색 (available 필터 없음)
    @Query(value = "SELECT p FROM Product p WHERE " +
                   "LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
                   "LOWER(p.productCode) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
                   "ORDER BY p.sortOrder ASC, p.id DESC",
           countQuery = "SELECT COUNT(p) FROM Product p WHERE " +
                        "LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
                        "LOWER(p.productCode) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<Product> searchAdminByNameOrCode(@Param("keyword") String keyword, Pageable pageable);

    // 관리자: 카테고리 + (제품명 또는 상품코드) 검색
    @Query(value = "SELECT p FROM Product p WHERE :category MEMBER OF p.categories AND (" +
                   "LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
                   "LOWER(p.productCode) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
                   "ORDER BY p.sortOrder ASC, p.id DESC",
           countQuery = "SELECT COUNT(p) FROM Product p WHERE :category MEMBER OF p.categories AND (" +
                        "LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
                        "LOWER(p.productCode) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Product> findAdminInCategoryAndNameOrCodeLike(@Param("category") String category, @Param("keyword") String keyword, Pageable pageable);

    // 공개 페이지: categories 컬렉션에서 검색 (available 필터 포함)
    @Query("SELECT p FROM Product p WHERE :category MEMBER OF p.categories AND p.available = true ORDER BY p.sortOrder ASC, p.id DESC")
    List<Product> findAvailableInCategory(@Param("category") String category);

    @Query(value = "SELECT p FROM Product p WHERE :category MEMBER OF p.categories AND p.available = true ORDER BY p.sortOrder ASC, p.id DESC",
           countQuery = "SELECT COUNT(p) FROM Product p WHERE :category MEMBER OF p.categories AND p.available = true")
    Page<Product> findAvailableInCategory(@Param("category") String category, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE :category MEMBER OF p.categories AND p.subcategory = :subcategory AND p.available = true ORDER BY p.sortOrder ASC, p.id DESC")
    List<Product> findAvailableInCategoryAndSubcategory(@Param("category") String category, @Param("subcategory") String subcategory);

    @Query(value = "SELECT p FROM Product p WHERE :category MEMBER OF p.categories AND p.subcategory = :subcategory AND p.available = true ORDER BY p.sortOrder ASC, p.id DESC",
           countQuery = "SELECT COUNT(p) FROM Product p WHERE :category MEMBER OF p.categories AND p.subcategory = :subcategory AND p.available = true")
    Page<Product> findAvailableInCategoryAndSubcategory(@Param("category") String category, @Param("subcategory") String subcategory, Pageable pageable);

    // 공개 페이지: 카테고리 안에서 (제품명 또는 상품코드) 검색 (available 필터 포함)
    @Query(value = "SELECT p FROM Product p WHERE :category MEMBER OF p.categories AND p.available = true AND (" +
                   "LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
                   "LOWER(p.productCode) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
                   "ORDER BY p.sortOrder ASC, p.id DESC",
           countQuery = "SELECT COUNT(p) FROM Product p WHERE :category MEMBER OF p.categories AND p.available = true AND (" +
                        "LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
                        "LOWER(p.productCode) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Product> searchAvailableInCategory(@Param("category") String category, @Param("keyword") String keyword, Pageable pageable);

    // 공개 페이지: 카테고리 + 세부분류 안에서 (제품명 또는 상품코드) 검색 (available 필터 포함)
    @Query(value = "SELECT p FROM Product p WHERE :category MEMBER OF p.categories AND p.subcategory = :subcategory AND p.available = true AND (" +
                   "LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
                   "LOWER(p.productCode) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
                   "ORDER BY p.sortOrder ASC, p.id DESC",
           countQuery = "SELECT COUNT(p) FROM Product p WHERE :category MEMBER OF p.categories AND p.subcategory = :subcategory AND p.available = true AND (" +
                        "LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
                        "LOWER(p.productCode) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Product> searchAvailableInCategoryAndSubcategory(@Param("category") String category, @Param("subcategory") String subcategory, @Param("keyword") String keyword, Pageable pageable);

    // 관리자: categories 컬렉션 검색 (available 필터 없음)
    @Query(value = "SELECT p FROM Product p WHERE :category MEMBER OF p.categories ORDER BY p.sortOrder ASC, p.id DESC",
           countQuery = "SELECT COUNT(p) FROM Product p WHERE :category MEMBER OF p.categories")
    Page<Product> findAdminInCategory(@Param("category") String category, Pageable pageable);

    // 관리자: 카테고리 + 세부카테고리 검색 (available 필터 없음)
    @Query(value = "SELECT p FROM Product p WHERE :category MEMBER OF p.categories AND p.subcategory = :subcategory ORDER BY p.sortOrder ASC, p.id DESC",
           countQuery = "SELECT COUNT(p) FROM Product p WHERE :category MEMBER OF p.categories AND p.subcategory = :subcategory")
    Page<Product> findAdminInCategoryAndSubcategory(@Param("category") String category,
                                                    @Param("subcategory") String subcategory,
                                                    Pageable pageable);

    @Query(value = "SELECT p FROM Product p WHERE :category MEMBER OF p.categories AND LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) ORDER BY p.sortOrder ASC, p.id DESC",
           countQuery = "SELECT COUNT(p) FROM Product p WHERE :category MEMBER OF p.categories AND LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<Product> findAdminInCategoryAndNameLike(@Param("category") String category, @Param("keyword") String keyword, Pageable pageable);

    // 관리자: 카테고리 + 세부카테고리 + (제품명 또는 상품코드) 검색
    @Query(value = "SELECT p FROM Product p WHERE :category MEMBER OF p.categories AND p.subcategory = :subcategory AND (" +
                   "LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
                   "LOWER(p.productCode) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
                   "ORDER BY p.sortOrder ASC, p.id DESC",
           countQuery = "SELECT COUNT(p) FROM Product p WHERE :category MEMBER OF p.categories AND p.subcategory = :subcategory AND (" +
                        "LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
                        "LOWER(p.productCode) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Product> findAdminInCategoryAndSubcategoryAndNameOrCodeLike(@Param("category") String category,
                                                                     @Param("subcategory") String subcategory,
                                                                     @Param("keyword") String keyword,
                                                                     Pageable pageable);

    /**
     * 재고 부족/품절 상품 목록 — 판매중(available)이면서 견적문의(priceOnRequest)가 아닌 상품 중
     * 재고가 임계치 이하인 것을 재고 오름차순으로 조회한다. (관리자 재고 부족 위젯)
     */
    @Query("SELECT p FROM Product p WHERE p.available = true AND p.priceOnRequest = false " +
           "AND p.stock <= :threshold ORDER BY p.stock ASC, p.id DESC")
    List<Product> findLowStock(@Param("threshold") int threshold);

    /** 동일 제품명 존재 여부 (대소문자 무시) — 일괄 import 중복 방지용 */
    boolean existsByNameIgnoreCase(String name);

    List<Product> findBySubcategory(String subcategory);

    /**
     * 동일 제품명+세부카테고리 존재 여부 (대소문자 무시) — 카테고리별 동일상품 허용 import 중복 방지용.
     * 같은 부품이 여러 모델 카테고리에 따로 올라가야 하므로, 세부카테고리까지 같아야 중복으로 본다.
     */
    boolean existsByNameIgnoreCaseAndSubcategory(String name, String subcategory);

    /** 제품명으로 조회 (대소문자 무시) — import reset(교체) 시 기존 상품 삭제용 */
    List<Product> findByNameIgnoreCase(String name);

    /** 특정 URL을 imageUrl로 사용하는 상품 수 (파일 삭제 전 공유 여부 확인용) */
    long countByImageUrl(String imageUrl);

    /** 특정 URL을 contentImages로 사용하는 상품 수 */
    @Query("SELECT COUNT(p) FROM Product p JOIN p.contentImages img WHERE img = :url")
    long countByContentImageUrl(@Param("url") String url);

    /** 특정 URL을 mainImages로 사용하는 상품 수 */
    @Query("SELECT COUNT(p) FROM Product p JOIN p.mainImages img WHERE img = :url")
    long countByMainImageUrl(@Param("url") String url);
}
