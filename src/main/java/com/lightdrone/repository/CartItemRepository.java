package com.lightdrone.repository;

import com.lightdrone.domain.CartItem;
import com.lightdrone.domain.Member;
import com.lightdrone.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    List<CartItem> findByMemberOrderByCreatedAtDesc(Member member);

    /** username 문자열로 직접 조회 — Member 조회 없이 사용 가능, 미존재 시 빈 목록 반환 */
    List<CartItem> findByMemberUsernameOrderByCreatedAtDesc(String memberUsername);

    Optional<CartItem> findByMemberAndProduct(Member member, Product product);

    /** 상품 + 옵션 ID 조합으로 장바구니 항목 조회 (null 옵션 대응) */
    @Query("SELECT c FROM CartItem c WHERE c.member = :member AND c.product = :product " +
           "AND ((:optionId IS NULL AND c.selectedOptionId IS NULL) OR c.selectedOptionId = :optionId)")
    Optional<CartItem> findByMemberAndProductAndOption(@Param("member") Member member,
                                                       @Param("product") Product product,
                                                       @Param("optionId") Long optionId);

    /** 상품 + 옵션 조합명(스냅샷)으로 장바구니 항목 조회 (다중 옵션 그룹 대응, null 대응) */
    @Query("SELECT c FROM CartItem c WHERE c.member = :member AND c.product = :product " +
           "AND ((:optName IS NULL AND c.selectedOptionName IS NULL) OR c.selectedOptionName = :optName)")
    Optional<CartItem> findByMemberAndProductAndOptionName(@Param("member") Member member,
                                                           @Param("product") Product product,
                                                           @Param("optName") String optName);

    int countByMember(Member member);

    /** username 문자열로 직접 카운트 — Member 조회 없이 사용 가능, 미존재 시 0 반환 */
    int countByMemberUsername(String memberUsername);

    void deleteAllByMember(Member member);

    void deleteAllByProduct(Product product);
}
