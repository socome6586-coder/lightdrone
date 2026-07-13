package com.lightdrone.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProfileUpdateDto {

    /* ── 연락처 ── */
    @Pattern(regexp = "^[0-9]*$", message = "연락처는 숫자만 입력 가능합니다.")
    @Size(max = 11, message = "연락처는 11자리 이하로 입력해주세요.")
    private String phone;

    /* ── 배송지 ── */
    private String zipCode;

    @Size(max = 200, message = "주소는 200자 이하로 입력해주세요.")
    private String address;

    @Size(max = 200, message = "상세주소는 200자 이하로 입력해주세요.")
    private String addressDetail;

    /* ── 비밀번호 변경 (선택, 비워두면 변경 안 함) ── */
    @Pattern(
        regexp = "^$|^(?:(?=.*[A-Z])(?=.*[a-z])|(?=.*[A-Z])(?=.*\\d)" +
                 "|(?=.*[A-Z])(?=.*[^A-Za-z0-9])|(?=.*[a-z])(?=.*\\d)" +
                 "|(?=.*[a-z])(?=.*[^A-Za-z0-9])|(?=.*\\d)(?=.*[^A-Za-z0-9])).{10,16}$",
        message = "비밀번호는 10~16자이며 영문 대소문자/숫자/특수문자 중 2가지 이상 조합이어야 합니다."
    )
    private String newPassword;

    private String newPasswordConfirm;
}
