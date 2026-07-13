package com.lightdrone.security.oauth2;

import java.util.Map;

/**
 * Kakao OAuth2 사용자 속성 파싱.
 * Kakao UserInfo 응답 구조:
 * {
 *   "id": 123456789,
 *   "kakao_account": {
 *     "email": "user@kakao.com",
 *     "profile": { "nickname": "홍길동" }
 *   }
 * }
 */
public class KakaoOAuth2UserInfo implements OAuth2UserInfo {

    private final Map<String, Object> attributes;

    public KakaoOAuth2UserInfo(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    @Override
    public String getProviderId() {
        Object id = attributes.get("id");
        return id != null ? id.toString() : null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public String getName() {
        Map<String, Object> account = (Map<String, Object>) attributes.get("kakao_account");
        if (account == null) return "카카오회원";
        Map<String, Object> profile = (Map<String, Object>) account.get("profile");
        if (profile == null) return "카카오회원";
        String nickname = (String) profile.get("nickname");
        return nickname != null && !nickname.isBlank() ? nickname : "카카오회원";
    }

    @Override
    @SuppressWarnings("unchecked")
    public String getEmail() {
        Map<String, Object> account = (Map<String, Object>) attributes.get("kakao_account");
        if (account == null) return null;
        return (String) account.get("email");
    }
}
