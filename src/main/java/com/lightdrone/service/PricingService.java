package com.lightdrone.service;

import com.lightdrone.domain.BusinessGradePolicy;
import com.lightdrone.domain.Member;
import com.lightdrone.domain.enums.BusinessGrade;
import com.lightdrone.repository.BusinessGradePolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사업자 회원 등급별 상시 할인 계산 중앙 처리.
 *
 *  - 등급별 할인율은 전역 설정({@link BusinessGradePolicy}, id=1)에서 읽으며
 *    관리자가 조정할 수 있다.
 *  - 할인은 상품의 effectivePrice(상품 자체 세일가) "위에" 추가로 적용되며,
 *    옵션 추가금에도 동일하게 적용된다.
 *  - 금액 반올림 규칙은 기존 상품 할인과 동일: Math.round(base*(100-pct)/100.0).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PricingService {

    private final BusinessGradePolicyRepository policyRepository;

    /** 등급별 할인율 전역 설정(없으면 기본값 객체 반환 — 저장은 하지 않음) */
    public BusinessGradePolicy getPolicy() {
        return policyRepository.findById(BusinessGradePolicy.SINGLETON_ID)
                .orElseGet(() -> BusinessGradePolicy.builder()
                        .id(BusinessGradePolicy.SINGLETON_ID)
                        .wholesalePercent(20)
                        .superWholesalePercent(30)
                        .build());
    }

    /** 등급별 할인율 전역 설정 저장(관리자) */
    @Transactional
    public void updatePolicy(int wholesalePercent, int superWholesalePercent) {
        int w = clampPercent(wholesalePercent);
        int s = clampPercent(superWholesalePercent);
        BusinessGradePolicy policy = policyRepository.findById(BusinessGradePolicy.SINGLETON_ID)
                .orElseGet(() -> BusinessGradePolicy.builder().id(BusinessGradePolicy.SINGLETON_ID).build());
        policy.setWholesalePercent(w);
        policy.setSuperWholesalePercent(s);
        policyRepository.save(policy);
    }

    /** 해당 등급의 상시 할인율(%) */
    public int discountPercentFor(BusinessGrade grade) {
        if (grade == null) return 0;
        BusinessGradePolicy policy = getPolicy();
        return switch (grade) {
            case WHOLESALE -> policy.getWholesalePercent();
            case SUPER_WHOLESALE -> policy.getSuperWholesalePercent();
            case NONE -> 0;
        };
    }

    /** 회원의 상시 할인율(%) — 비로그인/일반은 0 */
    public int discountPercentFor(Member member) {
        return member == null ? 0 : discountPercentFor(member.getBusinessGrade());
    }

    /** 회원 등급 할인 여부 */
    public boolean hasGradeDiscount(Member member) {
        return discountPercentFor(member) > 0;
    }

    /** 금액에 회원 등급 할인을 적용한 결과(원) */
    public long applyTo(long amount, Member member) {
        return applyDiscount(amount, discountPercentFor(member));
    }

    /** 금액에 지정 할인율을 적용(원). 기존 상품 할인과 동일한 반올림 규칙. */
    public long applyDiscount(long amount, int pct) {
        if (pct <= 0) return amount;
        if (pct >= 100) return 0L;
        long discounted = Math.round(amount * (100 - pct) / 100.0);
        return discounted < 0 ? 0L : discounted;
    }

    private int clampPercent(int pct) {
        if (pct < 0) return 0;
        if (pct > 100) return 100;
        return pct;
    }
}
