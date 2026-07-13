package com.lightdrone.config;

import com.lightdrone.service.LoginAttemptService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;

import java.io.IOException;

/**
 * 로그인 성공 시 해당 IP의 실패 기록을 초기화합니다.
 */
public class CustomLoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final LoginAttemptService loginAttemptService;

    public CustomLoginSuccessHandler(LoginAttemptService loginAttemptService) {
        setDefaultTargetUrl("/");
        setAlwaysUseDefaultTargetUrl(false);
        this.loginAttemptService = loginAttemptService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication)
            throws ServletException, IOException {
        String ip = LoginRateLimitFilter.resolveClientIp(request);
        loginAttemptService.loginSucceeded(ip);

        // 폼에 redirect 파라미터가 있으면 해당 페이지로 이동
        String redirectUrl = request.getParameter("redirect");
        if (redirectUrl != null && redirectUrl.startsWith("/")) {
            clearAuthenticationAttributes(request);
            response.sendRedirect(request.getContextPath() + redirectUrl);
            return;
        }

        super.onAuthenticationSuccess(request, response, authentication);
    }
}
