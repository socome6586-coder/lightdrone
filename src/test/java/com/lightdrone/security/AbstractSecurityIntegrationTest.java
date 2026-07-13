package com.lightdrone.security;

import com.lightdrone.domain.Member;
import com.lightdrone.domain.Order;
import com.lightdrone.domain.Product;
import com.lightdrone.domain.Qna;
import com.lightdrone.domain.Review;
import com.lightdrone.domain.enums.OrderStatus;
import com.lightdrone.domain.enums.PaymentMethod;
import com.lightdrone.domain.enums.Role;
import com.lightdrone.repository.MemberRepository;
import com.lightdrone.repository.OrderRepository;
import com.lightdrone.repository.ProductRepository;
import com.lightdrone.repository.QnaRepository;
import com.lightdrone.repository.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Spring Security 권한·CSRF 통합 테스트 공통 베이스.
 *
 * <p>격리 전략(요구사항: 운영/로컬 DB 변경 금지):
 * <ul>
 *   <li>{@code @ActiveProfiles("test")} → 인메모리 H2(application-test.yml), 실제 DB 미접속</li>
 *   <li>{@code @Transactional} → 각 테스트가 끝나면 자동 롤백되어 데이터가 남지 않음</li>
 *   <li>웹 환경은 MOCK + MockMvc → 실제 포트 바인딩 없이 전체 SecurityFilterChain 통과</li>
 * </ul>
 *
 * <p>인증은 {@code @WithMockUser(username=...)} 로 주입한다. 컨트롤러가
 * {@code memberService.findByUsername(...)} 로 DB 회원을 조회하므로,
 * 목 사용자 이름과 동일한 회원을 {@link #seedMembers()} 에서 미리 저장해 둔다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public abstract class AbstractSecurityIntegrationTest {

    /** @WithMockUser 의 username 과 일치해야 컨트롤러의 회원 조회가 성공한다. */
    protected static final String ALICE = "alice"; // 일반 회원 (소유자/요청자)
    protected static final String BOB   = "bob";   // 다른 일반 회원 (소유권 비교 대상)
    protected static final String ADMIN = "admin"; // 관리자

    @Autowired protected MockMvc mockMvc;
    @Autowired protected MemberRepository memberRepository;
    @Autowired protected OrderRepository orderRepository;
    @Autowired protected QnaRepository qnaRepository;
    @Autowired protected ReviewRepository reviewRepository;
    @Autowired protected ProductRepository productRepository;

    protected Member alice;
    protected Member bob;
    protected Member admin;

    /** 주문번호·이메일 충돌 방지용 카운터 (테스트 격리 내 유일성 보장) */
    private static final AtomicLong SEQ = new AtomicLong(0);

    @BeforeEach
    void seedMembers() {
        alice = saveMember(ALICE, Role.USER);
        bob   = saveMember(BOB, Role.USER);
        admin = saveMember(ADMIN, Role.ADMIN);
    }

    protected Member saveMember(String username, Role role) {
        long n = SEQ.incrementAndGet();
        Member m = Member.builder()
                .username(username)
                .password("{noop}test")          // @WithMockUser 사용 → 실제 인증엔 미사용
                .name(username)
                .email(username + n + "@test.local")
                .phone("0100000" + String.format("%04d", n))
                .role(role)
                .enabled(true)
                .agreePrivacy(true)
                .build();
        return memberRepository.saveAndFlush(m);
    }

    /** 지정 회원이 소유한 주문 1건 생성 (소유권/권한 테스트용 최소 데이터) */
    protected Order seedOrder(Member owner) {
        long n = SEQ.incrementAndGet();
        Order o = Order.builder()
                .orderNumber("ORD-TEST-" + n)
                .member(owner)
                .status(OrderStatus.PENDING_PAYMENT)
                .paymentMethod(PaymentMethod.BANK_TRANSFER)
                .buyerName(owner.getName())
                .buyerPhone("01011112222")
                .receiverName(owner.getName())
                .receiverPhone("01011112222")
                .totalPrice(10000L)
                .build();
        return orderRepository.saveAndFlush(o);
    }

    /** 지정 회원이 소유한 공개 Q&A 1건 생성 (미답변 = 수정 가능 상태) */
    protected Qna seedQna(Member owner) {
        Qna q = Qna.builder()
                .title("테스트 질문")
                .content("테스트 내용")
                .password("0000")
                .category("기타")
                .secret(false)
                .answered(false)
                .member(owner)
                .build();
        return qnaRepository.saveAndFlush(q);
    }

    /** 상품 1건 생성 (관리자 상품 관리 API 권한 테스트용) */
    protected Product seedProduct() {
        long n = SEQ.incrementAndGet();
        Product p = Product.builder()
                .name("테스트 상품 " + n)
                .price(100000L)
                .stock(10)
                .available(true)
                .build();
        return productRepository.saveAndFlush(p);
    }

    /** 지정 회원이 소유한 후기 1건 생성 */
    protected Review seedReview(Member owner) {
        Review r = Review.builder()
                .title("테스트 후기")
                .content("테스트 후기 내용")
                .category("기타")
                .rating(5)
                .visible(true)
                .member(owner)
                .build();
        return reviewRepository.saveAndFlush(r);
    }
}
