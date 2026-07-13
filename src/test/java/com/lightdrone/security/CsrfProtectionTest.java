package com.lightdrone.security;

import com.lightdrone.domain.Qna;
import com.lightdrone.domain.Review;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 요구사항 4: POST/DELETE 쓰기 요청의 CSRF 보호.
 *
 * <p>이 앱은 CSRF 실패(토큰 없음/무효)도 커스텀 accessDeniedHandler 를 통해
 * 403 이 아니라 홈("/")으로 302 리다이렉트한다. 따라서 "거부"는
 * <b>홈으로 리다이렉트</b>, "성공"은 <b>해당 컨트롤러의 정상 리다이렉트 목적지</b>로 구분한다.
 */
class CsrfProtectionTest extends AbstractSecurityIntegrationTest {

    // ── 후기 삭제(본인) — 토큰 유무에 따른 거부/성공 ──

    @Test
    @WithMockUser(username = "bob", roles = "USER")
    void postWithoutCsrfToken_isRejected() throws Exception {
        Review review = seedReview(bob);
        mockMvc.perform(post("/review/{id}/delete", review.getId()))   // 토큰 없음
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));                        // 거부 → 홈
    }

    @Test
    @WithMockUser(username = "bob", roles = "USER")
    void postWithInvalidCsrfToken_isRejected() throws Exception {
        Review review = seedReview(bob);
        mockMvc.perform(post("/review/{id}/delete", review.getId())
                        .with(csrf().useInvalidToken()))               // 잘못된 토큰
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));                        // 거부 → 홈
    }

    @Test
    @WithMockUser(username = "bob", roles = "USER")
    void postWithValidCsrfToken_succeeds() throws Exception {
        Review review = seedReview(bob);
        mockMvc.perform(post("/review/{id}/delete", review.getId())
                        .with(csrf()))                                 // 유효 토큰
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/review"));                 // 성공 → 후기 목록
    }

    // ── 관리자 Q&A 답변 — 토큰 없으면 관리자라도 거부 ──

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminPostWithoutCsrfToken_isRejected() throws Exception {
        Qna qna = seedQna(bob);
        mockMvc.perform(post("/qna/{id}/answer", qna.getId())
                        .param("answer", "토큰 없는 답변"))            // 토큰 없음
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));                        // 거부 → 홈
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminPostWithValidCsrfToken_succeeds() throws Exception {
        Qna qna = seedQna(bob);
        mockMvc.perform(post("/qna/{id}/answer", qna.getId())
                        .param("answer", "정상 답변")
                        .with(csrf()))                                 // 유효 토큰
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/qna/" + qna.getId()));     // 성공
    }
}
