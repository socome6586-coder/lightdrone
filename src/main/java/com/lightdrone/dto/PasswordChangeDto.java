package com.lightdrone.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PasswordChangeDto {

    @NotBlank(message = "새 비밀번호를 입력해주세요.")
    @Pattern(
        regexp = "^(?:(?=.*[A-Z])(?=.*[a-z])|(?=.*[A-Z])(?=.*\\d)" +
                 "|(?=.*[A-Z])(?=.*[^A-Za-z0-9])|(?=.*[a-z])(?=.*\\d)" +
                 "|(?=.*[a-z])(?=.*[^A-Za-z0-9])|(?=.*\\d)(?=.*[^A-Za-z0-9])).{10,16}$",
        message = "비밀번호는 10~16자이며 영문 대소문자/숫자/특수문자 중 2가지 이상 조합이어야 합니다."
    )
    private String newPassword;

    @NotBlank(message = "비밀번호 확인을 입력해주세요.")
    private String newPasswordConfirm;
}
