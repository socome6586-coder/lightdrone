package com.lightdrone.service;

import com.lightdrone.domain.Product;
import com.lightdrone.domain.ProductOption;
import com.lightdrone.dto.ProductDto;
import com.lightdrone.repository.CartItemRepository;
import com.lightdrone.repository.OrderItemRepository;
import com.lightdrone.repository.ProductOptionRepository;
import com.lightdrone.repository.ProductRepository;
import com.lightdrone.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductOptionRepository productOptionRepository;
    private final FileStorageService fileStorageService;
    private final CartItemRepository cartItemRepository;
    private final OrderItemRepository orderItemRepository;
    private final ReviewRepository reviewRepository;

    public List<Product> getAvailableProducts() {
        return productRepository.findByAvailableTrueOrderBySortOrderAscIdDesc();
    }
    public Page<Product> getAvailableProducts(Pageable pageable) {
        return productRepository.findByAvailableTrueOrderBySortOrderAscIdDesc(pageable);
    }

    public List<Product> getByCategory(String category) {
        return productRepository.findAvailableInCategory(category);
    }
    public Page<Product> getByCategory(String category, Pageable pageable) {
        return productRepository.findAvailableInCategory(category, pageable);
    }

    public List<Product> getByCategoryAndSubcategory(String category, String subcategory) {
        return productRepository.findAvailableInCategoryAndSubcategory(category, subcategory);
    }
    public Page<Product> getByCategoryAndSubcategory(String category, String subcategory, Pageable pageable) {
        return productRepository.findAvailableInCategoryAndSubcategory(category, subcategory, pageable);
    }

    /** 관리자 전용: category/subcategory/keyword 조합 필터 (available 무관) */
    public Page<Product> findForAdmin(String category, String keyword, Pageable pageable) {
        return findForAdmin(category, null, keyword, pageable);
    }

    /** 관리자 전용: category/subcategory/keyword 조합 필터 (available 무관) */
    public Page<Product> findForAdmin(String category, String subcategory, String keyword, Pageable pageable) {
        boolean hasCat = category != null && !category.isBlank();
        boolean hasSub = subcategory != null && !subcategory.isBlank();
        boolean hasKw  = keyword  != null && !keyword.isBlank();
        if (hasCat && hasSub && hasKw) {
            return productRepository.findAdminInCategoryAndSubcategoryAndNameOrCodeLike(category, subcategory, keyword, pageable);
        } else if (hasCat && hasSub) {
            return productRepository.findAdminInCategoryAndSubcategory(category, subcategory, pageable);
        } else if (hasCat && hasKw) {
            return productRepository.findAdminInCategoryAndNameOrCodeLike(category, keyword, pageable);
        } else if (hasCat) {
            return productRepository.findAdminInCategory(category, pageable);
        } else if (hasKw) {
            return productRepository.searchAdminByNameOrCode(keyword, pageable);
        } else {
            return productRepository.findAllByOrderBySortOrderAscIdDesc(pageable);
        }
    }

    public List<Product> findAllOrdered() {
        return productRepository.findAllByOrderBySortOrderAscIdDesc();
    }
    public Page<Product> findAllOrdered(Pageable pageable) {
        return productRepository.findAllByOrderBySortOrderAscIdDesc(pageable);
    }

    public List<Product> searchByKeyword(String keyword) {
        return productRepository.searchAvailableByNameOrCode(keyword);
    }
    public Page<Product> searchByKeyword(String keyword, Pageable pageable) {
        return productRepository.searchAvailableByNameOrCode(keyword, pageable);
    }

    /** 공개 페이지: 카테고리 안에서 키워드 검색 (선택한 카테고리 유지) */
    public Page<Product> searchByKeywordInCategory(String category, String keyword, Pageable pageable) {
        return productRepository.searchAvailableInCategory(category, keyword, pageable);
    }

    /** 공개 페이지: 카테고리 + 세부분류 안에서 키워드 검색 */
    public Page<Product> searchByKeywordInCategoryAndSubcategory(String category, String subcategory, String keyword, Pageable pageable) {
        return productRepository.searchAvailableInCategoryAndSubcategory(category, subcategory, keyword, pageable);
    }

    public long count() {
        return productRepository.count();
    }

    /** 재고 부족/품절 상품 목록 (관리자 재고 부족 위젯·페이지용) */
    public List<Product> getLowStockProducts(int threshold) {
        return productRepository.findLowStock(threshold);
    }

    public Product findById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));
    }

    @Transactional
    public void create(ProductDto dto) {
        // 대표 이미지: 새 파일이 있으면 업로드, 없으면 복사로 넘어온 기존 URL 재사용
        // (주의) 복사 시 기존 URL은 원본 상품과 공유되므로, 새 파일 업로드 시에도 기존 URL을 삭제하지 않는다.
        String imageUrl = (dto.getImageFile() != null && !dto.getImageFile().isEmpty())
                ? uploadIfPresent(dto.getImageFile(), null)
                : dto.getExistingImageUrl();
        // 슬라이더/내용 이미지: 복사로 넘어온 기존 URL을 앞에 두고, 신규 업로드 파일을 뒤에 추가
        List<String> mainImages = new ArrayList<>();
        if (dto.getExistingMainImages() != null) mainImages.addAll(dto.getExistingMainImages());
        mainImages.addAll(uploadContentImages(dto.getMainImageFiles()));
        List<String> contentImages = new ArrayList<>();
        if (dto.getExistingContentImages() != null) contentImages.addAll(dto.getExistingContentImages());
        contentImages.addAll(uploadContentImages(dto.getContentImageFiles()));
        Product saved = productRepository.save(Product.builder()
                .name(dto.getName())
                .description(dto.getDescription())
                .price(dto.isPriceOnRequest() ? 0L : dto.getPrice())
                .priceOnRequest(dto.isPriceOnRequest())
                .freeShipping(dto.isFreeShipping())
                .best(dto.isBest())
                .newProduct(dto.isNewProduct())
                .showImageLabel(dto.isShowImageLabel())
                .imageLabel(trimToNull(dto.getImageLabel()))
                .discountPercent(dto.isPriceOnRequest() ? 0 : clampPercent(dto.getDiscountPercent()))
                .category(dto.getCategory())
                .categories(dto.getCategories() != null ? new ArrayList<>(dto.getCategories()) : new ArrayList<>())
                .subcategory(dto.getSubcategory())
                .stock(dto.getStock())
                .available(dto.isAvailable())
                .sortOrder(dto.getSortOrder())
                .imageUrl(imageUrl)
                .contentImages(contentImages)
                .contentImageLayout(dto.getContentImageLayout())
                .mainImages(mainImages)
                .contentBgColor(dto.getContentBgColor())
                .videoUrl(dto.getVideoUrl())
                .manufacturer(dto.getManufacturer())
                .productCode(resolveProductCode(dto))
                .displayPrice(dto.getDisplayPrice())
                .build());
        saveOptions(saved, dto);
    }

    @Transactional
    public void update(Long id, ProductDto dto) {
        Product product = findById(id);
        product.setName(dto.getName());
        product.setDescription(dto.getDescription());
        product.setPriceOnRequest(dto.isPriceOnRequest());
        product.setFreeShipping(dto.isFreeShipping());
        product.setBest(dto.isBest());
        product.setNewProduct(dto.isNewProduct());
        product.setShowImageLabel(dto.isShowImageLabel());
        product.setImageLabel(trimToNull(dto.getImageLabel()));
        product.setPrice(dto.isPriceOnRequest() ? 0L : dto.getPrice());
        product.setDiscountPercent(dto.isPriceOnRequest() ? 0 : clampPercent(dto.getDiscountPercent()));
        product.setCategory(dto.getCategory());
        product.setCategories(dto.getCategories() != null ? new ArrayList<>(dto.getCategories()) : new ArrayList<>());
        product.setSubcategory(dto.getSubcategory());
        product.setStock(dto.getStock());
        product.setAvailable(dto.isAvailable());
        product.setSortOrder(dto.getSortOrder());
        product.setImageUrl(uploadIfPresent(dto.getImageFile(), product.getImageUrl()));

        // 내용 이미지: 교체 처리 (삭제 전에 먼저 수행 — 인덱스 기준)
        if (dto.getReplaceContentImageIndexes() != null && dto.getReplaceContentImageFiles() != null) {
            List<Integer> idxList  = dto.getReplaceContentImageIndexes();
            List<MultipartFile> newFiles = dto.getReplaceContentImageFiles();
            for (int r = 0; r < idxList.size(); r++) {
                int idx = idxList.get(r);
                if (r < newFiles.size() && newFiles.get(r) != null && !newFiles.get(r).isEmpty()
                        && idx >= 0 && idx < product.getContentImages().size()) {
                    fileStorageService.delete(product.getContentImages().get(idx));
                    try {
                        product.getContentImages().set(idx,
                                fileStorageService.store(newFiles.get(r)));
                    } catch (Exception e) {
                        throw new IllegalArgumentException("이미지 교체 업로드 실패: " + e.getMessage());
                    }
                }
            }
        }

        // 내용 이미지: 삭제 처리
        if (dto.getDeleteContentImageUrls() != null) {
            for (String url : dto.getDeleteContentImageUrls()) {
                fileStorageService.delete(url);
                product.getContentImages().remove(url);
            }
        }
        // 내용 이미지: 신규 추가
        List<String> newContentImages = uploadContentImages(dto.getContentImageFiles());
        product.getContentImages().addAll(newContentImages);

        // 레이아웃 + 배경색 저장
        product.setContentImageLayout(dto.getContentImageLayout());
        product.setContentBgColor(dto.getContentBgColor());
        product.setVideoUrl(dto.getVideoUrl());
        product.setManufacturer(dto.getManufacturer());
        product.setProductCode(dto.getProductCode());
        product.setDisplayPrice(dto.getDisplayPrice());

        // 슬라이더 이미지: 삭제
        if (dto.getDeleteMainImageUrls() != null) {
            for (String url : dto.getDeleteMainImageUrls()) {
                fileStorageService.delete(url);
                product.getMainImages().remove(url);
            }
        }
        // 슬라이더 이미지: 신규 추가
        List<String> newMainImages = uploadContentImages(dto.getMainImageFiles());
        product.getMainImages().addAll(newMainImages);

        // 옵션 저장 (기존 옵션 삭제 후 재등록)
        // 컬렉션을 비우면 orphanRemoval=true 로 기존 옵션이 DB에서 삭제된다.
        // (bulk delete 후 컬렉션이 그대로 남아 flush 시 재삽입되던 버그 수정)
        product.getOptions().clear();
        saveOptions(product, dto);
    }

    /**
     * 상품 복사용 DTO 생성 (DB 저장 없음).
     *
     * 기존에는 copy()가 즉시 새 Product 를 DB에 저장한 뒤 수정 폼으로 보냈기 때문에,
     * 관리자가 저장하지 않고 뒤로가기/목록 이동을 해도 복제본이 그대로 남는 문제가 있었다.
     * 이제는 원본 정보로 채운 DTO만 만들어 신규 등록 폼에 미리 채워주고,
     * 관리자가 '등록'을 눌러 POST /admin/products 로 제출할 때 비로소 저장된다.
     * 이미지는 새로 업로드하지 않고 원본 URL을 그대로 재사용한다(existing* 필드).
     */
    @Transactional(readOnly = true)
    public ProductDto buildCopyDto(Long id) {
        Product src = findById(id);
        ProductDto dto = new ProductDto();
        dto.setName(src.getName() + " (복사본)");
        dto.setDescription(src.getDescription());
        dto.setPrice(src.getPrice());
        dto.setPriceOnRequest(src.isPriceOnRequest());
        dto.setFreeShipping(src.isFreeShipping());
        dto.setBest(src.isBest());
        dto.setNewProduct(src.isNewProduct());
        dto.setShowImageLabel(src.isShowImageLabel());
        dto.setImageLabel(src.getImageLabel());
        dto.setDiscountPercent(src.getDiscountPercent());
        List<String> cats = new ArrayList<>(src.getCategories());
        if (cats.isEmpty() && src.getCategory() != null) cats.add(src.getCategory());
        dto.setCategories(cats);
        dto.setSubcategory(src.getSubcategory());
        dto.setStock(src.getStock());
        dto.setAvailable(true);
        dto.setSortOrder(src.getSortOrder());
        dto.setContentImageLayout(src.getContentImageLayout());
        dto.setContentBgColor(src.getContentBgColor());
        dto.setVideoUrl(src.getVideoUrl());
        dto.setManufacturer(src.getManufacturer());
        dto.setDisplayPrice(src.getDisplayPrice());
        // 상품코드는 복사하지 않고 저장 시 새로 자동 생성
        dto.setAutoGenerateProductCode(true);
        dto.setProductCode(null);
        // 이미지: 새 업로드 없이 원본 URL 재사용
        dto.setExistingImageUrl(src.getImageUrl());
        dto.setExistingMainImages(new ArrayList<>(src.getMainImages()));
        dto.setExistingContentImages(new ArrayList<>(src.getContentImages()));
        // 옵션 (sortOrder 순서 유지)
        List<ProductOption> opts = new ArrayList<>(src.getOptions());
        opts.sort(java.util.Comparator.comparingInt(ProductOption::getSortOrder)
                .thenComparing(ProductOption::getId));
        List<String> groupNames = new ArrayList<>();
        List<String> names = new ArrayList<>();
        List<Long> extras = new ArrayList<>();
        for (ProductOption opt : opts) {
            groupNames.add(opt.getGroupName());
            names.add(opt.getName());
            extras.add(opt.getExtraPrice() != null ? opt.getExtraPrice() : 0L);
        }
        dto.setOptionGroupNames(groupNames);
        dto.setOptionNames(names);
        dto.setOptionExtraPrices(extras);
        return dto;
    }

    // ─── 상품코드(14자리: yyyyMMdd + 6자리 순번) ──────────────────────

    /** 등록 시 상품코드 결정: 자동 생성 토글 ON이거나 입력이 비었으면 새로 생성 */
    private String resolveProductCode(ProductDto dto) {
        if (dto.isAutoGenerateProductCode()
                || dto.getProductCode() == null || dto.getProductCode().isBlank()) {
            return generateUniqueProductCode();
        }
        return dto.getProductCode().trim();
    }

    /** 오늘 날짜(yyyyMMdd) + 6자리 순번으로 중복 없는 14자리 상품코드 생성 */
    @Transactional(readOnly = true)
    public String generateUniqueProductCode() {
        String prefix = java.time.LocalDate.now()
                .format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE); // yyyyMMdd
        java.util.Set<String> used = new java.util.HashSet<>();
        for (Product p : productRepository.findAll()) {
            if (p.getProductCode() != null && !p.getProductCode().isBlank()) {
                used.add(p.getProductCode());
            }
        }
        int seq = 1;
        String code;
        do {
            code = prefix + String.format("%06d", seq++);
        } while (used.contains(code));
        return code;
    }

    /**
     * 상품코드가 비어 있는 기존 상품에 코드를 일괄 부여 (최초 1회, 멱등).
     * 형식: 20260615 + 6자리 순번(000001~). 이미 사용 중인 코드와 충돌하지 않도록 건너뜀.
     */
    @Transactional
    public void backfillProductCodes() {
        List<Product> all = productRepository.findAllByOrderBySortOrderAscIdDesc();
        java.util.Set<String> used = new java.util.HashSet<>();
        for (Product p : all) {
            if (p.getProductCode() != null && !p.getProductCode().isBlank()) {
                used.add(p.getProductCode());
            }
        }
        final String prefix = "20260615";
        int seq = 1;
        for (Product p : all) {
            if (p.getProductCode() != null && !p.getProductCode().isBlank()) continue;
            String code;
            do {
                code = prefix + String.format("%06d", seq++);
            } while (used.contains(code));
            used.add(code);
            p.setProductCode(code);
            productRepository.save(p);
        }
    }

    /** 할인율을 0~100 범위로 보정 */
    private int clampPercent(int p) {
        if (p < 0) return 0;
        if (p > 100) return 100;
        return p;
    }

    /** 앞뒤 공백 제거 후 빈 문자열이면 null 반환 (라벨 텍스트 정규화) */
    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /** 기존 단일 category → categories 컬렉션 DB 마이그레이션 (최초 1회) */
    @Transactional
    public void migrateCategoryToCategories() {
        productRepository.findAll().forEach(p -> {
            if ((p.getCategories() == null || p.getCategories().isEmpty()) && p.getCategory() != null && !p.getCategory().isBlank()) {
                List<String> cats = new ArrayList<>();
                cats.add(p.getCategory());
                p.setCategories(cats);
                productRepository.save(p);
            } else if (p.getCategories() != null && !p.getCategories().isEmpty()) {
                String primaryCategory = p.getCategories().get(0);
                if (p.getCategory() == null || p.getCategory().isBlank() || !p.getCategory().equals(primaryCategory)) {
                    p.setCategory(primaryCategory);
                    productRepository.save(p);
                }
            }
        });
    }

    @Transactional
    public void renameCategoryReferences(String oldName, String newName) {
        if (oldName == null || oldName.isBlank() || newName == null || newName.isBlank() || oldName.equals(newName)) {
            return;
        }

        productRepository.findAll().forEach(product -> {
            boolean changed = false;

            if (oldName.equals(product.getCategory())) {
                product.setCategory(newName);
                changed = true;
            }

            List<String> categories = product.getCategories();
            if (categories != null && categories.contains(oldName)) {
                LinkedHashSet<String> renamed = new LinkedHashSet<>();
                for (String category : categories) {
                    renamed.add(oldName.equals(category) ? newName : category);
                }
                product.setCategories(new ArrayList<>(renamed));
                changed = true;
            }

            if (changed) {
                productRepository.save(product);
            }
        });
    }

    @Transactional
    public void renameSubcategoryReferences(String categoryName, String oldName, String newName) {
        if (oldName == null || oldName.isBlank() || newName == null || newName.isBlank() || oldName.equals(newName)) {
            return;
        }

        productRepository.findAll().forEach(product -> {
            if (!oldName.equals(product.getSubcategory())) {
                return;
            }
            boolean inCategory = categoryName == null
                    || categoryName.equals(product.getCategory())
                    || (product.getCategories() != null && product.getCategories().contains(categoryName));
            if (inCategory) {
                product.setSubcategory(newName);
                productRepository.save(product);
            }
        });
    }

    @Transactional
    public void delete(Long id) {
        Product product = findById(id);
        // FK 제약 해소: 장바구니 항목 삭제, 주문 항목·구매후기 product 참조 초기화
        cartItemRepository.deleteAllByProduct(product);
        orderItemRepository.clearProductReference(product);
        reviewRepository.clearProductReference(product);
        // 이미지 파일 삭제 — 다른 상품이 같은 URL을 공유 중이면 파일은 보존
        if (product.getImageUrl() != null
                && productRepository.countByImageUrl(product.getImageUrl()) <= 1) {
            fileStorageService.delete(product.getImageUrl());
        }
        for (String url : product.getContentImages()) {
            if (productRepository.countByContentImageUrl(url) <= 1) {
                fileStorageService.delete(url);
            }
        }
        for (String url : product.getMainImages()) {
            if (productRepository.countByMainImageUrl(url) <= 1) {
                fileStorageService.delete(url);
            }
        }
        productRepository.delete(product);
    }

    /** 파일이 있으면 기존 삭제 후 새 파일 저장, 없으면 기존 URL 유지 */
    private String uploadIfPresent(MultipartFile file, String existingUrl) {
        if (file != null && !file.isEmpty()) {
            fileStorageService.delete(existingUrl);
            try {
                return fileStorageService.store(file);
            } catch (Exception e) {
                throw new IllegalArgumentException("이미지 업로드 실패: " + e.getMessage());
            }
        }
        return existingUrl;
    }

    /** 옵션 저장 헬퍼 */
    private void saveOptions(Product product, ProductDto dto) {
        if (dto.getOptionNames() == null) return;
        for (int i = 0; i < dto.getOptionNames().size(); i++) {
            String name = dto.getOptionNames().get(i);
            if (name == null || name.isBlank()) continue;
            // 주의: 삼항 연산자의 한쪽을 primitive(0L)로 두면 Long 쪽이 자동 언박싱되어
            // 리스트 원소가 null 일 때 NPE 가 난다. 두 분기 모두 Long 으로 유지해 언박싱을 막는다.
            Long extra = (dto.getOptionExtraPrices() != null && i < dto.getOptionExtraPrices().size())
                    ? dto.getOptionExtraPrices().get(i) : null;
            String group = (dto.getOptionGroupNames() != null && i < dto.getOptionGroupNames().size())
                    ? dto.getOptionGroupNames().get(i) : null;
            // 엔티티 컬렉션(orphanRemoval=true, cascade=ALL)에 추가해 영속성 컨텍스트와 일치시킨다.
            product.getOptions().add(ProductOption.builder()
                    .product(product)
                    .groupName(group != null && !group.isBlank() ? group.trim() : null)
                    .name(name.trim())
                    .extraPrice(extra != null ? extra : 0L)
                    .sortOrder(i)
                    .build());
        }
    }

    /** 다중 내용 이미지 업로드 */
    private List<String> uploadContentImages(List<MultipartFile> files) {
        List<String> urls = new ArrayList<>();
        if (files == null) return urls;
        for (MultipartFile file : files) {
            if (file != null && !file.isEmpty()) {
                try {
                    urls.add(fileStorageService.store(file));
                } catch (Exception e) {
                    throw new IllegalArgumentException("내용 이미지 업로드 실패: " + e.getMessage());
                }
            }
        }
        return urls;
    }
}
