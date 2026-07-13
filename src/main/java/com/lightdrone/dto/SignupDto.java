package com.lightdrone.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SignupDto {

    @NotBlank(message = "아이디를 입력해주세요.")
    @Size(min = 4, max = 20, message = "아이디는 4~20자여야 합니다.")
    @Pattern(regexp = "^[a-zA-Z0-9]+$", message = "아이디는 영문과 숫자만 사용 가능합니다.")
    private String username;

    /**
     * 비밀번호: 10~16자, 영문 대소문자 / 숫자 / 특수문자 중 2가지 이상 조합
     * 정규식 설명: 6가지 조합 중 하나라도 만족하면 OK (OR 로 연결)
     */
    @NotBlank(message = "비밀번호를 입력해주세요.")
    @Pattern(
        regexp = "^(?:(?=.*[A-Z])(?=.*[a-z])"
               + "|(?=.*[A-Z])(?=.*\\d)"
               + "|(?=.*[A-Z])(?=.*[^A-Za-z0-9])"
               + "|(?=.*[a-z])(?=.*\\d)"
               + "|(?=.*[a-z])(?=.*[^A-Za-z0-9])"
               + "|(?=.*\\d)(?=.*[^A-Za-z0-9])).{10,16}$",
        message = "비밀번호는 10~16자이며 영문 대소문자/숫자/특수문자 중 2가지 이상 조합이어야 합니다."
    )
    private String password;

    @NotBlank(message = "비밀번호 확인을 입력해주세요.")
    private String passwordConfirm;

    @NotBlank(message = "이름을 입력해주세요.")
    @Pattern(regexp = "^[가-힣]{2,30}$", message = "이름은 한글 2~30자로 입력해주세요.")
    private String name;

    @NotBlank(message = "이메일을 입력해주세요.")
    @Email(message = "올바른 이메일 형식을 입력해주세요.")
    private String email;

    @NotBlank(message = "연락처를 입력해주세요.")
    @Pattern(regexp = "^01[0-9]{8,9}$", message = "올바른 휴대전화번호를 입력해주세요. (예: 01012345678)")
    private String phone;

    /** 우편번호 */
    private String zipCode;

    /** 기본 주소 */
    private String address;

    /** 상세 주소 */
    private String addressDetail;

    /** [필수] 이용약관 동의 */
    private boolean agreeTerms = false;

    /** [필수] 개인정보 수집 및 이용 동의 */
    private boolean agreePrivacy = false;

    /** [선택] 개인정보 제3자 제공 동의 */
    private boolean agreeThirdParty = false;

    /** [선택] 개인정보 처리 위탁 동의 */
    private boolean agreeOutsourcing = false;
}
