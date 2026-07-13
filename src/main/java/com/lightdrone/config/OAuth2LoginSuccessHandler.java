package com.lightdrone.config;

import com.lightdrone.domain.Member;
import com.lightdrone.security.oauth2.CustomOAuth2User;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.FlashMapManager;
import org.springframework.web.servlet.support.RequestContextUtils;

import java.io.IOException;
import java.time.format.DateTimeFormatter;

/**
 * OAuth2 소셜 로그인 성공 핸들러.
 * Spring Security SavedRequest 캐시를 활용해 로그인 전 접근하려던 페이지로 자동 복귀.
 * 세션에 저장된 oauth2_redirect_uri 가 있으면 해당 경로로 이동.
 */
@Component
public class OAuth2LoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    public static final String REDIRECT_URI_SESSION_KEY = "oauth2_redirect_uri";

    private static final DateTimeFormatter SIGNUP_DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");

    public OAuth2LoginSuccessHandler() {
        setDefaultTargetUrl("/");
        setAlwaysUseDefaultTargetUrl(false);
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication)
            throws ServletException, IOException {

        if (authentication.getPrincipal() instanceof CustomOAuth2User oauthUser) {
            Member member = oauthUser.getMember();

            // 1) 최초 소셜 로그인(실제 이메일 미보유, 예: 카카오) → 추가정보(이메일) 입력 페이지로 유도.
            //    세션의 redirect URI는 유지하여 입력 완료 후 원래 목적지로 복귀시킨다.
            //    (이메일 입력 완료 시 complete-profile 에서 환영 메시지 노출)
            if (member.isEmailPlaceholder()) {
                clearAuthenticationAttributes(request);
                response.sendRedirect(request.getContextPath() + "/auth/complete-profile");
                return;
            }

            // 2) 이메일을 바로 제공하는 소셜(예: 구글)로 막 가입한 신규 회원 → 일반 회원가입과
            //    동일한 가입 완료(환영) 페이지로 유도해 "가입됐다"는 경험을 동일하게 제공.
            if (member.isNewlyRegistered()) {
                FlashMap flashMap = new FlashMap();
                flashMap.put("signupName", member.getName());
                // 소셜 회원의 username 은 자동생성값(google_123…)이라 표시하지 않고
                // 식별자로는 이메일을, 라벨 구분용으로 제공자명을 함께 전달한다.
                flashMap.put("signupUsername", member.getEmail());
                flashMap.put("signupProvider", providerDisplayName(member.getProvider()));
                flashMap.put("signupCompletedAt",
                        member.getCreatedAt() != null ? member.getCreatedAt().format(SIGNUP_DATE_FMT) : "-");
                FlashMapManager flashMapManager = RequestContextUtils.getFlashMapManager(request);
                if (flashMapManager != null) {
                    flashMapManager.saveOutputFlashMap(flashMap, request, response);
                }
                clearAuthenticationAttributes(request);
                response.sendRedirect(request.getContextPath() + "/auth/signup-complete");
                return;
            }
        }

        // 로그인 페이지에서 redirect 파라미터를 세션에 저장해 두었으면 해당 경로로 이동
        HttpSession session = request.getSession(false);
        if (session != null) {
            String redirectUri = (String) session.getAttribute(REDIRECT_URI_SESSION_KEY);
            if (redirectUri != null && redirectUri.startsWith("/")) {
                session.removeAttribute(REDIRECT_URI_SESSION_KEY);
                clearAuthenticationAttributes(request);
                response.sendRedirect(request.getContextPath() + redirectUri);
                return;
            }
        }

        super.onAuthenticationSuccess(request, response, authentication);
    }

    /** OAuth2 제공자 식별자 → 사용자 표시용 한글 명칭 */
    private String providerDisplayName(String provider) {
        if (provider == null) return "소셜";
        return switch (provider) {
            case "google" -> "구글";
            case "kakao"  -> "카카오";
            case "naver"  -> "네이버";
            default        -> provider;
        };
    }
}
