package com.lightdrone.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class QuotationFormDto {

    // 수신처 정보
    private String receiverCompany;   // 수신 기업/기관명
    private String receiverName;      // 담당자명

    @Pattern(regexp = "^$|^01[0-9]-\\d{3,4}-\\d{4}$", message = "올바른 휴대전화 번호를 입력해주세요. (예: 010-1234-5678)")
    private String receiverPhone;     // 연락처
    private String receiverEmail;     // 이메일

    // 견적 옵션
    private String deliveryZipCode;      // 납품 장소 우편번호

    @NotBlank(message = "납품 장소를 입력해주세요. 주소 찾기 버튼을 이용해 주세요.")
    private String deliveryAddress;      // 납품 장소 기본 주소
    private String deliveryAddressDetail; // 납품 장소 상세 주소
    private String note;              // 비고
    private int validDays = 30;       // 견적 유효기간 (기본 30일)
}
