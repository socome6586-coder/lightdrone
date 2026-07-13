package com.lightdrone.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Getter
@Setter
public class QnaDto {

    private String category = "기타";

    @NotBlank(message = "제목을 입력해주세요.")
    @Size(max = 200, message = "제목은 200자 이하여야 합니다.")
    private String title;

    @NotBlank(message = "내용을 입력해주세요.")
    private String content;

    /** 비공개 여부 (true = 비밀번호 필요) */
    private boolean secret = true;

    /** 비밀번호 — secret=true 일 때만 필수 (컨트롤러에서 검증) */
    private String password;

    /** 첨부 이미지 (여러 장, 선택) */
    private List<MultipartFile> imageFiles;
}
