package com.lightdrone.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_item_seq")
    @SequenceGenerator(name = "order_item_seq", sequenceName = "order_item_seq", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id")
    private Product product;

    /** 주문 시점 제품명 스냅샷 */
    @Column(nullable = false, length = 200)
    private String productName;

    /** 주문 시점 가격 스냅샷 (옵션 추가금액 포함) */
    @Column(nullable = false)
    private Long productPrice;

    /** 주문 시점 선택 옵션명 스냅샷 (null = 옵션 없음) */
    @Column(name = "selected_option_name", length = 200)
    private String selectedOptionName;

    @Column(nullable = false)
    private int quantity;

    public long getTotalPrice() {
        return productPrice * quantity;
    }
}
