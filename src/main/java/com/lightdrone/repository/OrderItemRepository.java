package com.lightdrone.repository;

import com.lightdrone.domain.OrderItem;
import com.lightdrone.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    /** 상품 삭제 시 주문 항목의 product 참조를 null 로 초기화 */
    @Modifying
    @Query("UPDATE OrderItem oi SET oi.product = null WHERE oi.product = :product")
    void clearProductReference(@Param("product") Product product);
}
