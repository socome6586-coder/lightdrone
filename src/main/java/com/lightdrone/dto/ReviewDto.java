package com.lightdrone.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Getter
@Setter
public class ReviewDto {

    @NotBlank(message = "제목을 입력해주세요.")
    @Size(max = 200, message = "제목은 200자 이하여야 합니다.")
    private String title;

    @NotBlank(message = "내용을 입력해주세요.")
    private String content;

    private String category = "기타";

    /** 후기 대상 구매 제품 ID (회원이 실제 구매한 제품만 선택 가능) */
    private Long productId;

    /** 별점 (1~5) */
    private Integer rating = 5;

    /** 이미지 다중 첨부 */
    private List<MultipartFile> imageFiles;
}
