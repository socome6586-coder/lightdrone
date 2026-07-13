package com.lightdrone.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
public class ProductDto {

    @NotBlank(message = "제품명을 입력해주세요.")
    @Size(max = 200, message = "제품명은 200자 이하여야 합니다.")
    private String name;

    private String description;

    @NotNull(message = "가격을 입력해주세요.")
    @Min(value = 0, message = "가격은 0 이상이어야 합니다.")
    private Long price;

    @NotEmpty(message = "카테고리를 하나 이상 선택해주세요.")
    private List<String> categories;

    /** 호환용: 첫 번째 카테고리 반환 */
    public String getCategory() {
        return (categories != null && !categories.isEmpty()) ? categories.get(0) : null;
    }

    private String subcategory;

    private int stock = 99;  // 신규 등록 폼 기본값 (수정/복사/일괄등록은 명시적으로 덮어씀)

    private boolean available = true;

    private boolean priceOnRequest = false;

    private boolean freeShipping = false;

    /** BEST 상품 여부 */
    private boolean best = false;

    /** NEW 상품 여부 */
    private boolean newProduct = false;

    /** 목록 카드 이미지 하단 라벨 표시 여부 */
    private boolean showImageLabel = false;

    /** 목록 카드 이미지 하단 라벨 텍스트 */
    private String imageLabel;

    /** 할인율 (0~100, %) */
    @Min(value = 0, message = "할인율은 0 이상이어야 합니다.")
    private int discountPercent = 0;

    private int sortOrder;

    /** 제품 대표 이미지 (선택, 썸네일용) */
    private MultipartFile imageFile;

    /** 슬라이더 대표 이미지 파일 목록 */
    private List<MultipartFile> mainImageFiles;

    /** 삭제할 슬라이더 이미지 URL 목록 */
    private List<String> deleteMainImageUrls;

    /** 내용 이미지 다중 업로드 */
    private List<MultipartFile> contentImageFiles;

    /** 삭제할 기존 내용 이미지 URL 목록 */
    private List<String> deleteContentImageUrls;

    /** 내용 이미지 레이아웃 (콤마 구분: full/half) */
    private String contentImageLayout;

    /** 내용 이미지 배경색 */
    private String contentBgColor;

    /** 동영상 URL */
    private String videoUrl;

    /** 제조사/원산지 */
    private String manufacturer;

    /** 상품코드 */
    private String productCode;

    /** 상품코드 자동 생성 여부 (true면 서버가 yyyyMMdd+6자리로 생성) */
    private boolean autoGenerateProductCode = true;

    /** 교체할 기존 이미지 인덱스 목록 (replaceContentImageFiles와 1:1 매핑) */
    private List<Integer> replaceContentImageIndexes;

    /** 교체할 새 파일 목록 (replaceContentImageIndexes와 1:1 매핑) */
    private List<MultipartFile> replaceContentImageFiles;

    /** 옵션 제목/그룹 목록 (optionNames와 1:1 매핑) */
    private List<String> optionGroupNames;

    /** 옵션 이름 목록 */
    private List<String> optionNames;

    /** 옵션 추가금액 목록 (optionNames와 1:1 매핑) */
    private List<Long> optionExtraPrices;

    /** 표시용 대표가격 (선택). 비우면 실제 판매가를 그대로 표시 */
    private Long displayPrice;

    // ─── 복사(carry-over) 전용 ─────────────────────────────────────────
    // 상품 복사 시 새 파일 업로드 없이 원본 이미지 URL을 그대로 재사용하기 위한 필드.
    // 일반 신규 등록에서는 null 이며 create() 동작에 영향을 주지 않는다.

    /** 복사: 재사용할 기존 대표(썸네일) 이미지 URL */
    private String existingImageUrl;

    /** 복사: 재사용할 기존 슬라이더 이미지 URL 목록 */
    private List<String> existingMainImages;

    /** 복사: 재사용할 기존 내용 이미지 URL 목록 */
    private List<String> existingContentImages;
}
