package com.lightdrone.config;

import com.lightdrone.domain.Member;
import com.lightdrone.security.oauth2.CustomOAuth2User;
import com.lightdrone.service.MemberService;
import com.lightdrone.service.ProductCategoryService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import com.lightdrone.domain.ProductCategory;

@Slf4j
@ControllerAdvice
@RequiredArgsConstructor
public class GlobalModelAttributeAdvice {

    private final ProductCategoryService productCategoryService;
    private final MemberService memberService;

    @Value("${site.url:https://lightdrone.co.kr}")
    private String siteUrl;

    @Value("${kakao.javascript-key:}")
    private String kakaoJsKey;

    /** 모든 뷰에 사이트 URL 주입 (OG 태그의 절대 URL 생성에 사용) */
    @ModelAttribute("siteUrl")
    public String siteUrl() {
        return siteUrl.replaceAll("/$", ""); // 끝 슬래시 제거
    }

    /**
     * 카카오 JS SDK 키 (카카오톡 공유 버튼용).
     * 브라우저에 노출되는 것을 전제로 하는 클라이언트 키이며,
     * 실제 보호는 Kakao Developers 콘솔의 사이트 도메인 등록으로 이뤄진다.
     */
    @ModelAttribute("kakaoJsKey")
    public String kakaoJsKey() {
        return kakaoJsKey;
    }

    /** 검색엔진과 공유 미리보기에 사용할 쿼리스트링 없는 대표 절대 URL. */
    @ModelAttribute("canonicalUrl")
    public String canonicalUrl(HttpServletRequest request) {
        return siteUrl() + request.getRequestURI();
    }

    /**
     * 로그인한 회원의 실제 이름을 모든 뷰에 주입합니다.
     * 자동 생성된 아이디(kakao_xxx 등)가 고객 화면에 노출되지 않도록
     * navbar 등에서 sec:authentication="name"(=username) 대신 이 값을 사용합니다.
     * 비로그인 시 null.
     */
    @ModelAttribute("currentMemberName")
    public String currentMemberName() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()
                    || "anonymousUser".equals(auth.getPrincipal())) {
                return null;
            }
            Object principal = auth.getPrincipal();
            // 소셜 로그인: CustomOAuth2User → Member 직접 보유
            if (principal instanceof CustomOAuth2User oauthUser) {
                return oauthUser.getMember().getName();
            }
            // 폼 로그인: principal은 username만 보유 → 조회
            Member member = memberService.findByUsername(auth.getName());
            return member.getName();
        } catch (Exception e) {
            return null;
        }
    }

    @ModelAttribute("navCategories")
    public List<ProductCategory> navCategories() {
        try {
            return productCategoryService.getAllCategories();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * CSRF 토큰을 모든 뷰의 모델에 추가합니다.
     * Spring Security 6.4+에서 CsrfToken.class.getName() 속성 또는 "_csrf" 속성을 통해
     * 토큰을 찾고, Supplier<CsrfToken> 타입도 처리합니다.
     */
    @ModelAttribute
    public void csrf(HttpServletRequest request, Model model) {
        try {
            CsrfToken csrfToken = resolveCsrfToken(request);
            if (csrfToken != null) {
                model.addAttribute("_csrf", csrfToken);
            } else {
                log.debug("CSRF token not available for request: {}", request.getRequestURI());
            }
        } catch (Exception e) {
            log.warn("CSRF token 설정 실패 [{}]: {}", request.getRequestURI(), e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private CsrfToken resolveCsrfToken(HttpServletRequest request) {
        // 1차: CsrfToken.class.getName() 속성 (Spring Security 6.x 표준)
        Object attr = request.getAttribute(CsrfToken.class.getName());
        if (attr instanceof CsrfToken) {
            return (CsrfToken) attr;
        }
        // 2차: Supplier<CsrfToken> 래퍼인 경우 (일부 Spring Security 버전)
        if (attr instanceof Supplier) {
            Object supplied = ((Supplier<?>) attr).get();
            if (supplied instanceof CsrfToken) {
                return (CsrfToken) supplied;
            }
        }
        // 3차: "_csrf" 문자열 속성 키 폴백
        Object fallback = request.getAttribute("_csrf");
        if (fallback instanceof CsrfToken) {
            return (CsrfToken) fallback;
        }
        if (fallback instanceof Supplier) {
            Object supplied = ((Supplier<?>) fallback).get();
            if (supplied instanceof CsrfToken) {
                return (CsrfToken) supplied;
            }
        }
        return null;
    }
}
