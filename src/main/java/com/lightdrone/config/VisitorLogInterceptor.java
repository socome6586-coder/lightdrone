package com.lightdrone.config;

import com.lightdrone.service.VisitorLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class VisitorLogInterceptor implements HandlerInterceptor {

    private final VisitorLogService visitorLogService;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        String uri = request.getRequestURI();
        // 어드민·API·정적 리소스·파일 요청 제외
        if (uri.startsWith("/admin") || uri.startsWith("/api")
                || uri.startsWith("/uploads") || uri.startsWith("/css")
                || uri.startsWith("/js") || uri.startsWith("/images")
                || uri.startsWith("/actuator") || uri.contains(".")) {
            return true;
        }
        String ip = getClientIp(request);
        visitorLogService.record(ip);
        return true;
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
