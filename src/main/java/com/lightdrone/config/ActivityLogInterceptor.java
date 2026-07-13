package com.lightdrone.config;

import com.lightdrone.service.ActivityLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class ActivityLogInterceptor implements HandlerInterceptor {

    private final ActivityLogService activityLogService;

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        try {
            if (!"POST".equalsIgnoreCase(request.getMethod())) {
                return;
            }
            String uri = request.getRequestURI();
            if (uri == null || !uri.startsWith("/admin")) {
                return;
            }
            if (uri.startsWith("/admin/login") || uri.startsWith("/admin/logout")) {
                return;
            }
            if (response.getStatus() >= 400) {
                return;
            }

            String username = currentUsername();
            String menu = resolveMenu(uri);
            String action = resolveAction(uri);
            String description = menu + " " + action;

            activityLogService.record(username, menu, action,
                    request.getMethod(), uri, description, clientIp(request));
        } catch (Exception ignored) {
            // 감사 로그 실패가 실제 관리자 요청을 방해하지 않도록 무시합니다.
        }
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.isAuthenticated()) ? auth.getName() : "unknown";
    }

    private String resolveMenu(String uri) {
        if (uri.startsWith("/admin/products")) return "상품";
        if (uri.startsWith("/admin/orders")) return "주문";
        if (uri.startsWith("/admin/members") || uri.startsWith("/admin/member-grades")) return "회원";
        if (uri.startsWith("/admin/notices")) return "공지사항";
        if (uri.startsWith("/admin/manuals")) return "자료실";
        if (uri.startsWith("/admin/categories")) return "카테고리";
        if (uri.startsWith("/admin/custom-payments")) return "맞춤결제";
        if (uri.startsWith("/admin/home-panels")) return "메인 패널";
        if (uri.startsWith("/admin/popups")) return "팝업";
        if (uri.startsWith("/admin/services")) return "서비스";
        if (uri.startsWith("/admin/inquiries")) return "문의";
        if (uri.startsWith("/admin/qna")) return "Q&A";
        if (uri.startsWith("/admin/reviews")) return "후기";
        return "관리자";
    }

    private String resolveAction(String uri) {
        if (uri.endsWith("/bulk-delete")) return "일괄 삭제";
        if (uri.endsWith("/delete")) return "삭제";
        if (uri.endsWith("/edit")) return "수정";
        if (uri.endsWith("/answer")) return "답변 등록";
        if (uri.endsWith("/mark-paid")) return "결제완료 처리";
        if (uri.endsWith("/mark-pending")) return "결제대기 변경";
        if (uri.endsWith("/payment-cancel")) return "결제취소";
        if (uri.endsWith("/payment-reconcile")) return "정산 동기화";
        if (uri.endsWith("/toggle")) return "상태 변경";
        if (uri.endsWith("/cancel")) return "취소";
        if (uri.endsWith("/reject")) return "거절";
        if (uri.contains("/policy")) return "정책 저장";
        return "등록";
    }

    private String clientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
