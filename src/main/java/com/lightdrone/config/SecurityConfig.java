package com.lightdrone.config;

import com.lightdrone.service.CustomOAuth2UserService;
import com.lightdrone.service.LoginAttemptService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private final LoginAttemptService loginAttemptService;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // Spring Security 6.4+ 기본값(XorCsrfTokenRequestAttributeHandler)이
        // Thymeleaf의 CsrfRequestDataValueProcessor.getExtraHiddenFields()와 충돌 방지
        // CsrfTokenRequestAttributeHandler 사용으로 토큰을 즉시 로드함
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        csrfHandler.setCsrfRequestAttributeName(null); // 즉시 토큰 로드를 위한 설정
        http
            .addFilterBefore(new LoginRateLimitFilter(loginAttemptService),
                             UsernamePasswordAuthenticationFilter.class)
            .csrf(csrf -> csrf
                .csrfTokenRequestHandler(csrfHandler)
                // 이미지 업로드 API는 @PreAuthorize(hasRole/isAuthenticated)로 보호되므로 CSRF 예외 처리
                // WebSocket(SockJS) 핸드셰이크는 CSRF 토큰을 전달하지 않으므로 예외 처리
                // 토스 웹훅은 외부(토스 서버)에서 토큰 없이 POST 하므로 '정확한 단일 경로'만 예외
                //   — 와일드카드(/api/payment/**) 금지: 다른 결제 API의 CSRF 보호 약화 방지(요구 11)
                .ignoringRequestMatchers("/api/admin/upload-image", "/api/upload-image", "/ws-chat/**",
                                         "/api/payment/toss/webhook")
            )

            /* ── 보안 응답 헤더 ──────────────────────────────────────────────
             * - HSTS: HTTPS 강제 (운영은 https, 리버스 프록시의 X-Forwarded-Proto를
             *         forward-headers-strategy=native 로 인식하므로 secure 요청에 한해 전송)
             * - CSP: 클릭재킹·base 태그 인젝션·object/embed·외부 폼 전송을 차단.
             *        단, 코드 전반이 인라인 스크립트/스타일과 외부 CDN(Toss·Kakao·Quill·
             *        Summernote·jQuery·Chart.js·Google Maps/Fonts·jsDelivr)에 의존하므로
             *        script/style 은 'unsafe-inline' 과 https: 를 허용한다(사이트 동작 보존).
             * - Referrer-Policy / X-Frame-Options(SAMEORIGIN) 로 정보 노출·프레이밍 보호
             *   (X-Content-Type-Options: nosniff 는 Spring Security 기본 적용)
             */
            .headers(headers -> headers
                .frameOptions(frame -> frame.sameOrigin())
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31536000))
                .referrerPolicy(ref -> ref.policy(
                    ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                    "default-src 'self'; " +
                    "script-src 'self' 'unsafe-inline' 'unsafe-eval' https:; " +
                    "style-src 'self' 'unsafe-inline' https:; " +
                    "img-src 'self' data: blob: https:; " +
                    "font-src 'self' data: https:; " +
                    "connect-src 'self' https: wss:; " +
                    "frame-src 'self' https: http://postcode.map.kakao.com https://postcode.map.kakao.com; " +
                    "frame-ancestors 'self'; " +
                    "object-src 'none'; " +
                    "base-uri 'self'; " +
                    "form-action 'self' https:"))
            )
            .authorizeHttpRequests(auth -> auth

                /* 관리자 전용 (페이지 + 관리자 전용 API — 컨트롤러 @PreAuthorize 에 더해 보안 계층에서도 차단) */
                .requestMatchers("/admin/**", "/qna/*/answer",
                                 "/api/admin/**", "/api/chat/admin/**").hasRole("ADMIN")

                /* 로그인 후 사용 가능 */
                .requestMatchers("/order/my",
                                 "/order/*/delete", "/order/*/cancel", "/order/*/refund",
                                 "/mypage", "/mypage/**",
                                 "/inquiry/my",
                                 "/qna/write",
                                 "/quotation", "/quotation/**",
                                 "/api/upload-image",
                                 "/review/write", "/review/*/edit", "/review/*/delete").authenticated()

                /* 공개 페이지 (GET / POST 모두)
                 * 참고: /review/write, /review/*\/edit, /review/*\/delete 는
                 * 위의 authenticated() 블록에서 이미 인증 필요로 처리됩니다.
                 * /review, /review/** permitAll은 목록·상세 조회를 위한 것이며,
                 * 쓰기·수정·삭제 경로는 authenticated() 규칙이 우선 적용됩니다.
                 * 컨트롤러 내 수동 리다이렉트는 SecurityConfig 규칙과 중복될 수 있으므로
                 * 향후 ReviewController에서 @PreAuthorize 또는 Spring Security 규칙으로 일원화를 권장합니다.
                 */
                .requestMatchers(
                    "/css/**", "/js/**", "/images/**", "/uploads/**", "/favicon.ico",
                    "/robots.txt", "/sitemap.xml",
                    "/oauth2/**", "/login/oauth2/**",   // 소셜 로그인 콜백 URL
                    "/",
                    "/order/form", "/order/prepare", "/order", "/order/complete", "/order/complete/**",
                    "/order/lookup", "/order/lookup-custom",
                    "/payment/success", "/payment/fail",
                    "/custom", "/custom/**",
                    "/company",
                    "/drone-law",
                    "/privacy",
                    "/refund",
                    "/products", "/products/**",
                    "/cart", "/cart/**",   // 비회원도 장바구니 이용 가능 (세션 기반)
                    "/support", "/support/**",
                    "/as",
                    "/notice", "/notice/**",
                    "/qna", "/qna/**",
                    "/review", "/review/**",
                    "/inquiry",
                    "/inquiry/lookup",
                    "/inquiry/*/view",
                    "/auth/**",
                    "/api/**",
                    "/error", "/error/**"
                ).permitAll()

                /* 그 외 경로는 보안 계층에서 막지 않고 통과시킨다.
                 *
                 * 이유(요구사항 6): catch-all 을 authenticated() 로 두면 매핑되지 않은
                 * "존재하지 않는 URL"까지 인증 대상이 되어, 비로그인 사용자가 오타/없는 주소를
                 * 요청하면 404 대신 /auth/login 으로 302 리다이렉트되었다. permitAll() 로 바꾸면
                 * 그런 요청이 DispatcherServlet 까지 도달해 NoResourceFoundException 이 발생하고
                 * GlobalExceptionHandler 가 올바른 404(error/404)를 반환한다.
                 *
                 * 보안 주의: 보호가 필요한 모든 경로(관리자 영역·회원 전용 쓰기 등)는 위의
                 * hasRole(ADMIN)/authenticated() 블록에 "명시적으로" 열거되어 있으므로
                 * 이 변경으로 노출되지 않는다. 단, 앞으로 민감한 엔드포인트를 추가할 때는
                 * 반드시 위 블록에 명시적으로 등록해야 한다(이 catch-all 은 더 이상 보호막이 아님).
                 */
                .anyRequest().permitAll()
            )

            /* 폼 로그인 */
            .formLogin(form -> form
                .loginPage("/auth/login")
                .loginProcessingUrl("/auth/login")
                .successHandler(new CustomLoginSuccessHandler(loginAttemptService))
                .failureHandler(new CustomLoginFailureHandler(loginAttemptService))
                .usernameParameter("username")
                .passwordParameter("password")
                .permitAll()
            )

            /* 소셜 로그인 (Google, Kakao) */
            .oauth2Login(oauth2 -> oauth2
                .loginPage("/auth/login")
                .userInfoEndpoint(u -> u.userService(customOAuth2UserService))
                .successHandler(oAuth2LoginSuccessHandler)
                .failureHandler((request, response, exception) -> {
                    log.error("[OAuth2 로그인 실패] {}: {}", exception.getClass().getSimpleName(), exception.getMessage(), exception);
                    response.sendRedirect(request.getContextPath() + "/auth/login?error");
                })
            )

            /* 로그아웃 */
            .logout(logout -> logout
                .logoutRequestMatcher(new AntPathRequestMatcher("/auth/logout", "POST"))
                .logoutSuccessUrl("/")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            )

            /* 접근 거부 처리 */
            .exceptionHandling(ex -> ex
                /* 비인증 사용자 → 로그인 페이지로 (원래 URL 세션에 저장 후 복귀) */
                .authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/auth/login"))
                /* 권한 없는 사용자 → 홈으로 */
                .accessDeniedHandler((request, response, accessDeniedException) ->
                    response.sendRedirect(request.getContextPath() + "/"))
            );

        return http.build();
    }
}
