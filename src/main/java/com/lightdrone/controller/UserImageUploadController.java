package com.lightdrone.controller;

import com.lightdrone.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 로그인한 일반 사용자가 에디터(Summernote) 내에서 이미지를 업로드할 때 사용하는 엔드포인트.
 * 구매후기 작성, Q&amp;A 작성 등 인증된 사용자 화면에서 호출됩니다.
 * (관리자 전용 /api/admin/upload-image 와 구분)
 */
@RestController
@RequestMapping("/api")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class UserImageUploadController {

    private final FileStorageService fileStorageService;

    @PostMapping(value = "/upload-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> uploadImage(
            @RequestParam("file") MultipartFile file) {
        try {
            String url = fileStorageService.store(file);
            return ResponseEntity.ok(Map.of("url", url));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
