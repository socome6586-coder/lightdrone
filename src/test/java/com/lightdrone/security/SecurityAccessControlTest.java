package com.lightdrone.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.test.context.support.WithMockUser;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 요구사항 1·2: URL 접근 권한.
 *
 * <p>이 앱의 SecurityConfig 는 거부를 401/403 이 아니라 <b>리다이렉트</b>로 처리한다:
 * <ul>
 *   <li>비로그인(anonymous) 사용자가 보호 자원 접근 → {@code /auth/login} 으로 302</li>
 *   <li>로그인했지만 권한 부족(예: 일반회원이 관리자 URL) → 홈("/")으로 302
 *       (커스텀 accessDeniedHandler)</li>
 * </ul>
 */
class SecurityAccessControlTest extends AbstractSecurityIntegrationTest {

    // ──────────────────────────────────────────────────────────────
    // 요구사항 1: 관리자 전용 URL — 비로그인/일반회원 접근 차단
    // ──────────────────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {
            "/admin/products",        // 관리자 페이지
            "/admin/orders",          // 관리자 페이지
            "/admin/reviews",         // 관리자 후기 관리
            "/api/admin/anything",    // 관리자 전용 API (핸들러 유무와 무관하게 보안 계층에서 차단)
            "/api/chat/admin/rooms"   // 관리자 전용 채팅 API
    })
    void anonymous_cannotAccessAdminUrls_redirectedToLogin(String url) throws Exception {
        mockMvc.perform(get(url))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/auth/login"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/admin/products",
            "/admin/orders",
            "/admin/reviews",
            "/api/admin/anything",
            "/api/chat/admin/rooms"
    })
    @WithMockUser(username = "alice", roles = "USER")
    void normalUser_cannotAccessAdminUrls_redirectedHome(String url) throws Exception {
        mockMvc.perform(get(url))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    // ──────────────────────────────────────────────────────────────
    // 요구사항 2: 일반 회원용 URL — 비로그인 시 인증 요구(로그인 리다이렉트)
    // ──────────────────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {
            "/order/my",        // 내 주문 목록
            "/mypage",          // 마이페이지
            "/mypage/edit",     // 마이페이지 하위
            "/qna/write",       // Q&A 작성
            "/review/write",    // 후기 작성
            "/inquiry/my",      // 내 문의
            "/quotation"        // 견적
    })
    void anonymous_memberOnlyUrls_requireAuthentication(String url) throws Exception {
        mockMvc.perform(get(url))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/auth/login"));
    }

    /** 로그인한 일반 회원은 회원 전용 URL 접근 시 로그인 페이지로 튕기지 않는다(인증 통과). */
    @Test
    @WithMockUser(username = "alice", roles = "USER")
    void authenticatedUser_memberOnlyUrl_notRedirectedToLogin() throws Exception {
        // /order/my 는 컨트롤러까지 도달해 본인 주문 목록을 렌더링한다(로그인 리다이렉트 아님).
        mockMvc.perform(get("/order/my"))
                .andExpect(result -> {
                    String redirected = result.getResponse().getRedirectedUrl();
                    if (redirected != null && redirected.contains("/auth/login")) {
                        throw new AssertionError("인증된 회원이 로그인 페이지로 리다이렉트됨: " + redirected);
                    }
                });
    }
}
