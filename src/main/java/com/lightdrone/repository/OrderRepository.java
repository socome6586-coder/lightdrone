package com.lightdrone.repository;

import com.lightdrone.domain.Member;
import com.lightdrone.domain.Order;
import com.lightdrone.domain.enums.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);

    /** 회원 주문 목록 — items JOIN FETCH (OSIV 불필요) */
    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items WHERE o.member = :member ORDER BY o.createdAt DESC")
    List<Order> findByMemberWithItems(@Param("member") Member member);

    /** 관리자 전체 주문 목록 — items JOIN FETCH + countQuery 분리 (페이징 정확도 보장) */
    @Query(value = "SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items ORDER BY o.createdAt DESC",
           countQuery = "SELECT COUNT(DISTINCT o) FROM Order o")
    Page<Order> findAllWithItems(Pageable pageable);

    /** 주문 완료 페이지 — items JOIN FETCH */
    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.items WHERE o.orderNumber = :orderNumber")
    Optional<Order> findByOrderNumberWithItems(@Param("orderNumber") String orderNumber);

    /** 주문 상세 페이지 — items + product + member JOIN FETCH (OSIV off 환경) */
    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items i LEFT JOIN FETCH i.product LEFT JOIN FETCH o.member WHERE o.id = :id")
    Optional<Order> findByIdWithItems(@Param("id") Long id);

    Optional<Order> findByOrderNumber(String orderNumber);
    long countByStatus(OrderStatus status);

    /** 통합검색: 주문번호·구매자명·연락처 부분일치 (최신순) */
    @Query("SELECT o FROM Order o " +
           "WHERE LOWER(o.orderNumber) LIKE LOWER(CONCAT('%', :kw, '%')) " +
           "OR LOWER(o.buyerName) LIKE LOWER(CONCAT('%', :kw, '%')) " +
           "OR o.buyerPhone LIKE CONCAT('%', :kw, '%') " +
           "ORDER BY o.createdAt DESC")
    List<Order> searchForAdmin(@Param("kw") String kw, Pageable pageable);

    /** 미결제 자동취소 대상: 카드결제 + 결제대기 + 결제승인 없음 + cutoff 이전 생성 */
    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items " +
           "WHERE o.status = com.lightdrone.domain.enums.OrderStatus.PENDING_PAYMENT " +
           "AND o.paymentMethod = com.lightdrone.domain.enums.PaymentMethod.CARD " +
           "AND o.paymentKey IS NULL AND o.createdAt < :cutoff")
    List<Order> findStalePendingCardOrders(@Param("cutoff") LocalDateTime cutoff);

    /** 회원 삭제 전: 주문 내역 보존을 위해 member 참조만 해제 */
    @Modifying
    @Query("UPDATE Order o SET o.member = null WHERE o.member = :member")
    void detachMember(@Param("member") Member member);

    /** 취소/환불 제외 전체 주문 금액 합계 */
    @Query("SELECT COALESCE(SUM(o.totalPrice), 0) FROM Order o WHERE o.status NOT IN ('CANCELLED')")
    long sumTotalPrice();

    /** 오늘 취소/환불 제외 주문 금액 합계 */
    @Query("SELECT COALESCE(SUM(o.totalPrice), 0) FROM Order o WHERE o.status NOT IN ('CANCELLED') AND o.createdAt >= :start AND o.createdAt < :end")
    long sumTotalPriceByDate(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 오늘 신규 주문 수 */
    @Query("SELECT COUNT(o) FROM Order o WHERE o.createdAt >= :start AND o.createdAt < :end")
    long countByCreatedAtBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 기간 매출 통계/내보내기용 — 취소 제외 주문을 items 와 함께 기간 조회 (생성일 오름차순) */
    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items " +
           "WHERE o.status NOT IN ('CANCELLED', 'REJECTED') " +
           "AND o.createdAt >= :start AND o.createdAt < :end " +
           "ORDER BY o.createdAt ASC")
    List<Order> findSalesBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // items 를 함께 로딩(LEFT JOIN FETCH) — 대시보드 컨트롤러가 트랜잭션 밖(OSIV off)에서
    // order.getItems() 를 읽어 상품 요약을 만들 때 LazyInitializationException 이 나지 않도록 한다.
    // DISTINCT 로 items 다중 행에 의한 주문 중복을 제거.
    @Query("SELECT DISTINCT o FROM Order o " +
           "LEFT JOIN FETCH o.items " +
           "WHERE o.createdAt >= :since " +
           "AND o.status NOT IN ('CANCELLED', 'REJECTED') " +
           "ORDER BY o.createdAt DESC")
    List<Order> findAdminNotificationCandidates(@Param("since") LocalDateTime since);
}
