package com.lightdrone.dto;

import com.lightdrone.domain.enums.BusinessGrade;
import com.lightdrone.domain.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminMemberEditDto {

    @NotBlank(message = "이름을 입력해주세요.")
    @Size(max = 30, message = "이름은 30자 이하여야 합니다.")
    private String name;

    @NotBlank(message = "이메일을 입력해주세요.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    @Size(max = 100)
    private String email;

    @Size(max = 20)
    private String phone;

    private Role role;

    /** 회원 등급(일반/도매/도도매) */
    private BusinessGrade businessGrade;

    private boolean enabled;

    /** 비워두면 비밀번호 변경 없음 */
    private String newPassword;
}
