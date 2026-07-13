package com.lightdrone.domain;

import com.lightdrone.domain.enums.BusinessGrade;
import com.lightdrone.domain.enums.Role;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "members")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Member extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "member_seq")
    @SequenceGenerator(name = "member_seq", sequenceName = "member_seq", allocationSize = 1)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 30)
    private String name;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(length = 20)
    private String phone;

    /** 우편번호 */
    @Column(length = 10)
    private String zipCode;

    /** 기본 주소 */
    @Column(length = 200)
    private String address;

    /** 상세 주소 */
    @Column(length = 200)
    private String addressDetail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    /** 회원 등급(사업자 회원 세부 등급) — 관리자가 직접 변경. 기본 일반(NONE) */
    @Enumerated(EnumType.STRING)
    @Column(name = "business_grade", nullable = false, length = 30)
    @Builder.Default
    private BusinessGrade businessGrade = BusinessGrade.NONE;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    /** [필수] 개인정보 수집 및 이용 동의 */
    @Column(nullable = false)
    @Builder.Default
    private boolean agreePrivacy = false;

    /** [선택] 개인정보 제3자 제공 동의 */
    @Builder.Default
    private boolean agreeThirdParty = false;

    /** [선택] 개인정보 처리 위탁 동의 */
    @Builder.Default
    private boolean agreeOutsourcing = false;

    /** OAuth2 제공자 이름 (google, kakao) — 일반 회원은 null */
    @Column(length = 20)
    private String provider;

    /** OAuth2 제공자 고유 사용자 ID — 일반 회원은 null */
    @Column(name = "provider_id", length = 200)
    private String providerId;

    /** 이메일을 제공하지 않는 소셜 로그인 시 자동 생성되는 플레이스홀더 이메일 도메인 */
    public static final String NO_EMAIL_DOMAIN = "@noemail.lightdrone.com";

    /**
     * 이번 요청에서 막 자동 가입된 소셜 회원 여부.
     * DB에 저장하지 않는 일시적 표시값으로, 소셜 로그인 성공 핸들러가
     * 신규 가입 환영 페이지 노출 여부를 판단하는 데만 사용한다.
     */
    @Transient
    @Builder.Default
    private boolean newlyRegistered = false;

    /** 자동 생성된 플레이스홀더 이메일 여부 (실제 이메일이 아직 입력되지 않은 소셜 회원) */
    public boolean isEmailPlaceholder() {
        return email != null && email.endsWith(NO_EMAIL_DOMAIN);
    }

    /** 소셜 로그인 회원 여부 */
    public boolean isSocialLogin() {
        return provider != null;
    }
}
