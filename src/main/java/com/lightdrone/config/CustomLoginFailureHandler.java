package com.lightdrone.config;

import com.lightdrone.service.LoginAttemptService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;

import java.io.IOException;

/**
 * 로그인 실패 시 IP 기반 시도 횟수를 기록합니다.
 * 5회 실패 후에는 차단 메시지 페이지로 리다이렉트합니다.
 */
public class CustomLoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private final LoginAttemptService loginAttemptService;

    public CustomLoginFailureHandler(LoginAttemptService loginAttemptService) {
        this.loginAttemptService = loginAttemptService;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        // 비활성화(접근 제한) 계정은 시도 횟수와 무관하게 별도 안내
        if (exception instanceof DisabledException) {
            response.sendRedirect(request.getContextPath() + "/auth/login?disabled=true");
            return;
        }

        String ip = LoginRateLimitFilter.resolveClientIp(request);
        loginAttemptService.loginFailed(ip);

        if (loginAttemptService.isBlocked(ip)) {
            long remaining = loginAttemptService.getRemainingBlockSeconds(ip);
            response.sendRedirect(request.getContextPath() + "/auth/login?blocked=" + remaining);
            return;
        }
        response.sendRedirect(request.getContextPath() + "/auth/login?error=true");
    }
}
