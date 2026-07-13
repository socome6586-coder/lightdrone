package com.lightdrone.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "cart_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "cart_seq")
    @SequenceGenerator(name = "cart_seq", sequenceName = "cart_seq", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    @Builder.Default
    private int quantity = 1;

    /** 선택한 옵션 ID (null = 옵션 없음) */
    @Column(name = "selected_option_id")
    private Long selectedOptionId;

    /** 선택한 옵션명 스냅샷 */
    @Column(name = "selected_option_name", length = 200)
    private String selectedOptionName;

    /** 선택한 옵션 추가금액 스냅샷 */
    @Column(name = "selected_option_extra_price")
    @Builder.Default
    private Long selectedOptionExtraPrice = 0L;

    public long getTotalPrice() {
        long base = (product != null) ? product.getEffectivePrice() : 0L;
        long extra = (selectedOptionExtraPrice != null) ? selectedOptionExtraPrice : 0L;
        return (base + extra) * quantity;
    }
}
