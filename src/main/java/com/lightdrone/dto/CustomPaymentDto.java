package com.lightdrone.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
public class CustomPaymentDto {

    /** 상품명 = 구매자 성함 또는 기업명 */
    @NotBlank(message = "상품명(구매자 성함/기업명)을 입력해주세요.")
    @Size(max = 200)
    private String name;

    /** 맞춤결제코드 = 전화번호 끝 4자리 (숫자 4자리) */
    @NotBlank(message = "맞춤결제코드를 입력해주세요.")
    @Pattern(regexp = "\\d{4}", message = "맞춤결제코드는 숫자 4자리여야 합니다.")
    private String code;

    @NotNull(message = "가격을 입력해주세요.")
    @Min(value = 0, message = "가격은 0 이상이어야 합니다.")
    private Long price;

    /** 배송 방법: "택배" 또는 "직접수령" */
    @NotBlank(message = "배송 방법을 선택해주세요.")
    private String deliveryMethod = "택배";

    /** 대표 이미지 (선택 — 미등록 시 기본 이미지 적용) */
    private MultipartFile imageFile;

    /** 수정 시 기존 이미지 URL 유지용 */
    private String existingImageUrl;
}
