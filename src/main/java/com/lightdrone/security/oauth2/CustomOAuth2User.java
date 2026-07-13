package com.lightdrone.security.oauth2;

import com.lightdrone.domain.Member;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * OAuth2 인증 후 SecurityContext 에 저장되는 Principal.
 * OAuth2User 와 UserDetails 를 모두 구현해
 * 기존 @AuthenticationPrincipal UserDetails 코드와 완전히 호환된다.
 */
public class CustomOAuth2User implements OAuth2User, UserDetails {

    private final Member member;
    private final Map<String, Object> attributes;

    public CustomOAuth2User(Member member, Map<String, Object> attributes) {
        this.member = member;
        this.attributes = attributes;
    }

    public Member getMember() { return member; }

    /* ── OAuth2User ───────────────────────────────────── */
    @Override public Map<String, Object> getAttributes() { return attributes; }
    @Override public String getName() { return member.getUsername(); }

    /* ── UserDetails ──────────────────────────────────── */
    @Override public String getUsername() { return member.getUsername(); }
    @Override public String getPassword() { return member.getPassword(); }
    @Override public boolean isEnabled()  { return member.isEnabled(); }
    @Override public boolean isAccountNonExpired()     { return true; }
    @Override public boolean isAccountNonLocked()      { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singleton(
                new SimpleGrantedAuthority("ROLE_" + member.getRole().name()));
    }
}
