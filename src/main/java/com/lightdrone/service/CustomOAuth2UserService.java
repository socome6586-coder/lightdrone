package com.lightdrone.service;

import com.lightdrone.domain.Member;
import com.lightdrone.domain.enums.Role;
import com.lightdrone.repository.MemberRepository;
import com.lightdrone.security.oauth2.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * 소셜 로그인 처리 서비스.
 * 1) provider + providerId 로 기존 소셜 계정 조회
 * 2) 동일 이메일 일반 계정 존재 시 소셜 계정 연결
 * 3) 신규 회원 자동 가입
 */
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final MemberRepository memberRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(request);
        String provider = request.getClientRegistration().getRegistrationId(); // "google" | "kakao" | "naver"
        Map<String, Object> attributes = oAuth2User.getAttributes();

        OAuth2UserInfo info = switch (provider) {
            case "google" -> new GoogleOAuth2UserInfo(attributes);
            case "kakao"  -> new KakaoOAuth2UserInfo(attributes);
            case "naver"  -> new NaverOAuth2UserInfo(attributes);
            default -> throw new OAuth2AuthenticationException("지원하지 않는 소셜 로그인: " + provider);
        };

        Member member = getOrCreateMember(provider, info);
        return new CustomOAuth2User(member, attributes);
    }

    private Member getOrCreateMember(String provider, OAuth2UserInfo info) {
        String providerId = info.getProviderId();
        String email      = info.getEmail();

        // 1. 기존 소셜 계정 조회
        var existing = memberRepository.findByProviderAndProviderId(provider, providerId);
        if (existing.isPresent()) return existing.get();

        // 2. 동일 이메일 일반 계정에 소셜 정보 연결
        if (email != null) {
            var byEmail = memberRepository.findByEmail(email);
            if (byEmail.isPresent()) {
                Member m = byEmail.get();
                m.setProvider(provider);
                m.setProviderId(providerId);
                return m; // dirty-check로 자동 업데이트
            }
        }

        // 3. 신규 회원 자동 생성
        String username = provider + "_" + providerId;
        String name     = truncate(info.getName() != null ? info.getName() : provider + "회원", 30);
        // 이메일을 제공하지 않는 경우 플레이스홀더 생성 (DB NOT NULL 제약 대응)
        // 최초 소셜 로그인 시 OAuth2LoginSuccessHandler에서 추가정보(실제 이메일) 입력 페이지로 유도됨
        String memberEmail = email != null ? email
                : username + Member.NO_EMAIL_DOMAIN;

        Member saved = memberRepository.save(Member.builder()
                .username(username)
                .password(UUID.randomUUID().toString()) // 소셜 전용 — 직접 로그인에 사용되지 않음
                .name(name)
                .email(memberEmail)
                .role(Role.USER)
                .provider(provider)
                .providerId(providerId)
                .agreePrivacy(true)
                .enabled(true)
                .build());
        // 이번 로그인에서 막 가입된 신규 회원 표시 → 성공 핸들러가 환영 페이지로 유도
        saved.setNewlyRegistered(true);
        return saved;
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
