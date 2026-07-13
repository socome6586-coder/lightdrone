package com.lightdrone.dto;

import com.lightdrone.domain.enums.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderFormDto {

    /* 주문 출처 (컨트롤러에서 hidden으로 전달) */
    private Long productId;     // 바로구매 시 제품 ID
    private Integer quantity;   // 바로구매 시 수량
    private boolean fromCart;   // 장바구니에서 주문 시 true
    private List<Long> optionIds;  // 바로구매 시 선택한 옵션 ID 목록 (옵션 제목별 1개씩)

    /* 구매자 정보 */
    @NotBlank(message = "구매자 이름을 입력해주세요.")
    private String buyerName;

    @NotBlank(message = "구매자 연락처를 입력해주세요.")
    @Pattern(regexp = "^01[0-9]-\\d{3,4}-\\d{4}$", message = "올바른 휴대전화 번호를 입력해주세요. (예: 010-1234-5678)")
    private String buyerPhone;

    private String buyerEmail;

    /* 배송지 정보 */
    @NotBlank(message = "수령인 이름을 입력해주세요.")
    private String receiverName;

    @NotBlank(message = "수령인 연락처를 입력해주세요.")
    @Pattern(regexp = "^01[0-9]-\\d{3,4}-\\d{4}$", message = "올바른 휴대전화 번호를 입력해주세요. (예: 010-1234-5678)")
    private String receiverPhone;

    private String zipCode;

    @NotBlank(message = "배송 주소를 입력해주세요.")
    private String address;

    @NotBlank(message = "상세 주소를 입력해주세요.")
    private String addressDetail;
    private String deliveryMessage;

    /* 결제 */
    @NotNull(message = "결제 수단을 선택해주세요.")
    private PaymentMethod paymentMethod;
}
