package com.lightdrone.config;

import com.lightdrone.service.LoginAttemptService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 로그인 POST 요청 전, IP 차단 여부를 먼저 확인합니다.
 * 차단된 IP는 즉시 /auth/login?blocked={남은초} 로 리다이렉트합니다.
 */
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private final LoginAttemptService loginAttemptService;

    public LoginRateLimitFilter(LoginAttemptService loginAttemptService) {
        this.loginAttemptService = loginAttemptService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if ("POST".equalsIgnoreCase(request.getMethod())
                && "/auth/login".equals(request.getServletPath())) {
            String ip = resolveClientIp(request);
            if (loginAttemptService.isBlocked(ip)) {
                long remaining = loginAttemptService.getRemainingBlockSeconds(ip);
                response.sendRedirect(request.getContextPath() + "/auth/login?blocked=" + remaining);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    /** X-Forwarded-For 헤더를 우선 사용, 없으면 RemoteAddr */
    static String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
