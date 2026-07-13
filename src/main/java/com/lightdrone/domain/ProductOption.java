package com.lightdrone.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "product_options")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductOption {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "product_option_seq")
    @SequenceGenerator(name = "product_option_seq", sequenceName = "product_option_seq", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** 옵션 제목/그룹 (예: 메인커넥터, 암 파이프 사이즈) — 같은 그룹끼리 하나의 드롭다운 */
    @Column(name = "group_name", length = 200)
    private String groupName;

    /** 옵션명 (예: 배터리 1개 추가, 보험 1년 등) */
    @Column(nullable = false, length = 200)
    private String name;

    /** 추가 금액 (기본 0원) */
    @Column(nullable = false)
    @Builder.Default
    private Long extraPrice = 0L;

    @Column(nullable = false)
    @Builder.Default
    private int sortOrder = 0;
}
