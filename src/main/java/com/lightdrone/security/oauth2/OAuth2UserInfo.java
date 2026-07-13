package com.lightdrone.security.oauth2;

/** 소셜 로그인 제공자(Google, Kakao)별 사용자 정보 추상화 */
public interface OAuth2UserInfo {
    String getProviderId();
    String getName();
    String getEmail();
}
