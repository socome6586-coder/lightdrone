package com.lightdrone.security;

import com.lightdrone.domain.Product;
import com.lightdrone.domain.Qna;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 요구사항 3: 관리자 전용 쓰기 작업 권한 (Q&A 답변·상품 관리 API).
 *
 * <p>각 쓰기 요청은 CSRF 토큰을 포함시켜(=CSRF 는 통과) 순수하게 <b>권한</b>만 검증한다.
 * 거부는 리다이렉트로 나타난다: 일반회원 → "/", 비로그인 → 로그인 페이지.
 */
class AdminApiPermissionTest extends AbstractSecurityIntegrationTest {

    // ── Q&A 답변: 관리자만 가능 (/qna/{id}/answer, hasRole(ADMIN) + @PreAuthorize) ──

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void admin_canAnswerQna() throws Exception {
        Qna qna = seedQna(bob);
        mockMvc.perform(post("/qna/{id}/answer", qna.getId())
                        .param("answer", "관리자 답변입니다.")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/qna/" + qna.getId()));
    }

    @Test
    @WithMockUser(username = "alice", roles = "USER")
    void normalUser_cannotAnswerQna_redirectedHome() throws Exception {
        Qna qna = seedQna(bob);
        mockMvc.perform(post("/qna/{id}/answer", qna.getId())
                        .param("answer", "비관리자 답변 시도")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void anonymous_cannotAnswerQna_redirectedToLogin() throws Exception {
        Qna qna = seedQna(bob);
        mockMvc.perform(post("/qna/{id}/answer", qna.getId())
                        .param("answer", "익명 답변 시도")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/auth/login"));
    }

    // ── 상품 관리 API: 관리자만 가능 (/admin/products/**) ──

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void admin_canDeleteProduct() throws Exception {
        Product product = seedProduct();
        mockMvc.perform(post("/admin/products/{id}/delete", product.getId())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/products?page=0"));
    }

    @Test
    @WithMockUser(username = "alice", roles = "USER")
    void normalUser_cannotDeleteProduct_redirectedHome() throws Exception {
        Product product = seedProduct();
        mockMvc.perform(post("/admin/products/{id}/delete", product.getId())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void anonymous_cannotDeleteProduct_redirectedToLogin() throws Exception {
        Product product = seedProduct();
        mockMvc.perform(post("/admin/products/{id}/delete", product.getId())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/auth/login"));
    }
}
