package com.lightdrone.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SupportVideoDto {

    @NotBlank(message = "영상 제목을 입력해주세요.")
    @Size(max = 200, message = "제목은 200자 이하여야 합니다.")
    private String title;

    @NotBlank(message = "유튜브 링크를 입력해주세요.")
    @Size(max = 500, message = "링크는 500자 이하여야 합니다.")
    private String youtubeUrl;

    @Size(max = 50, message = "카테고리는 50자 이하여야 합니다.")
    private String category;

    @NotNull(message = "정렬 순서를 입력해주세요.")
    private Integer sortOrder = 0;

    private boolean visible = true;
}
