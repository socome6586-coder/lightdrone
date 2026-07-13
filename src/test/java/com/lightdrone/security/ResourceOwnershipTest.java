package com.lightdrone.security;

import com.lightdrone.domain.Order;
import com.lightdrone.domain.Qna;
import com.lightdrone.domain.Review;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 요구사항 5: 소유권(IDOR) 검증.
 *
 * <p>모든 요청은 로그인한 일반 회원 alice 가 보내고, 대상 자원은 bob 소유다.
 * CSRF 토큰은 포함하므로(=CSRF 통과) 순수하게 소유권 검증만 확인한다.
 * 차단 시 컨트롤러는 작업을 수행하지 않고 안전한 곳으로 리다이렉트한다.
 */
@WithMockUser(username = "alice", roles = "USER")
class ResourceOwnershipTest extends AbstractSecurityIntegrationTest {

    // ── 주문: 다른 회원의 주문 취소·삭제 차단 ──

    @Test
    void cannotCancelOthersOrder() throws Exception {
        Order order = seedOrder(bob);
        mockMvc.perform(post("/order/{id}/cancel", order.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));   // 소유자 아님 → 홈
    }

    @Test
    void cannotDeleteOthersOrder() throws Exception {
        Order order = seedOrder(bob);
        mockMvc.perform(post("/order/{id}/delete", order.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    // ── Q&A: 다른 회원의 글 수정 차단 ──

    @Test
    void cannotEditOthersQna() throws Exception {
        Qna qna = seedQna(bob);
        mockMvc.perform(post("/qna/{id}/edit", qna.getId())
                        .param("title", "남의 글 제목 변경")
                        .param("content", "남의 글 내용 변경")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/qna/" + qna.getId()));  // 작성자 아님 → 상세로
    }

    // ── 후기: 다른 회원의 후기 수정·삭제 차단 ──

    @Test
    void cannotEditOthersReview() throws Exception {
        Review review = seedReview(bob);
        mockMvc.perform(post("/review/{id}/edit", review.getId())
                        .param("title", "남의 후기 변경")
                        .param("content", "남의 후기 내용 변경")
                        .param("category", "기타")
                        .param("rating", "1")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/review/" + review.getId()));
    }

    @Test
    void cannotDeleteOthersReview() throws Exception {
        Review review = seedReview(bob);
        mockMvc.perform(post("/review/{id}/delete", review.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/review/" + review.getId()));
    }

    // ── 대조군: 본인 자원은 정상 처리(소유권 통과) ──

    @Test
    void canDeleteOwnReview() throws Exception {
        Review review = seedReview(alice);   // alice 본인 소유
        mockMvc.perform(post("/review/{id}/delete", review.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/review"));  // 성공 → 목록
    }
}
