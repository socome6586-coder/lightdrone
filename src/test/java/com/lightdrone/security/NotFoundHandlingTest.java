package com.lightdrone.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * 요구사항 6: 존재하지 않는 공개 URL은 로그인 페이지로 튕기지 않고 올바른 404를 반환한다.
 *
 * <p>이전에는 SecurityConfig 의 catch-all 규칙이 {@code anyRequest().authenticated()} 였기 때문에
 * 매핑되지 않은(=존재하지 않는) URL 까지 인증 대상이 되어, 비로그인 사용자가 오타/없는 주소를
 * 요청하면 404 가 아니라 {@code /auth/login} 으로 302 리다이렉트되었다. catch-all 을
 * {@code permitAll()} 로 바꿔 이런 요청이 DispatcherServlet 까지 도달하도록 하면
 * {@code NoResourceFoundException} 이 발생하고 {@link com.lightdrone.controller.GlobalExceptionHandler}
 * 가 {@code error/404} 뷰와 함께 HTTP 404 를 반환한다.
 */
class NotFoundHandlingTest extends AbstractSecurityIntegrationTest {

    /** 비로그인 사용자가 존재하지 않는 공개 URL 요청 → 로그인 리다이렉트가 아니라 404. */
    @Test
    void anonymous_unknownUrl_returns404_notLoginRedirect() throws Exception {
        mockMvc.perform(get("/this-page-does-not-exist-xyz"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/404"));
    }

    /** 중첩 경로의 존재하지 않는 URL 도 동일하게 404. */
    @Test
    void anonymous_unknownNestedUrl_returns404() throws Exception {
        mockMvc.perform(get("/foo/bar/baz-not-real"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/404"));
    }

    /** 로그인한 일반 회원이 존재하지 않는 URL 요청 → 역시 404(권한/인증 문제로 가로채지 않음). */
    @Test
    @WithMockUser(username = "alice", roles = "USER")
    void authenticatedUser_unknownUrl_returns404() throws Exception {
        mockMvc.perform(get("/nope-not-a-real-route-123"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/404"));
    }
}
