package com.lightdrone.security.oauth2;

import java.util.Map;

/**
 * Naver OAuth2 사용자 속성 파싱.
 * Naver UserInfo 응답 구조:
 * {
 *   "resultcode": "00",
 *   "message": "success",
 *   "response": {
 *     "id": "32742776",
 *     "email": "user@naver.com",
 *     "name": "홍길동",
 *     "nickname": "별명"
 *   }
 * }
 * user-name-attribute 를 "response" 로 두면 attributes 의 "response" 키에 위 내부 객체가 들어온다.
 */
public class NaverOAuth2UserInfo implements OAuth2UserInfo {

    private final Map<String, Object> response;

    @SuppressWarnings("unchecked")
    public NaverOAuth2UserInfo(Map<String, Object> attributes) {
        Object res = attributes.get("response");
        this.response = res instanceof Map ? (Map<String, Object>) res : Map.of();
    }

    @Override
    public String getProviderId() {
        Object id = response.get("id");
        return id != null ? id.toString() : null;
    }

    @Override
    public String getName() {
        String name = (String) response.get("name");
        if (name != null && !name.isBlank()) return name;
        String nickname = (String) response.get("nickname");
        return nickname != null && !nickname.isBlank() ? nickname : "네이버회원";
    }

    @Override
    public String getEmail() {
        return (String) response.get("email");
    }
}
