package com.lightdrone.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * 사업자 회원 등급별 상시 할인율 전역 설정.
 * 단일 행(id=1)만 사용하며, 관리자가 도매/도도매 할인율을 조정할 수 있다.
 */
@Entity
@Table(name = "business_grade_policy")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BusinessGradePolicy extends BaseEntity {

    /** 전역 설정은 단일 행(id=1)으로 관리한다. */
    public static final Long SINGLETON_ID = 1L;

    @Id
    private Long id;

    /** 도매 회원 상시 할인율(%) */
    @Column(nullable = false)
    @Builder.Default
    private int wholesalePercent = 20;

    /** 도도매 회원 상시 할인율(%) */
    @Column(nullable = false)
    @Builder.Default
    private int superWholesalePercent = 30;
}
