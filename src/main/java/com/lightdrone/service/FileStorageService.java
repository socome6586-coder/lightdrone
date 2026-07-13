package com.lightdrone.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;

/**
 * 파일 저장 서비스
 * application.yml의 storage.type 값에 따라 로컬 저장 / AWS S3 저장을 선택합니다.
 *
 * ── 로컬 저장 (기본값) ─────────────────────────────────────
 *   storage.type: local
 *
 * ── AWS S3 저장 ────────────────────────────────────────────
 *   storage.type: s3
 *   storage.s3:
 *     region: ap-northeast-2
 *     bucket: 버킷명
 *     access-key: IAM Access Key
 *     secret-key: IAM Secret Key
 *     base-url: https://버킷명.s3.ap-northeast-2.amazonaws.com
 */
@Service
public class FileStorageService {

    private static final Set<String> ALLOWED_EXT = Set.of("jpg", "jpeg", "png", "gif", "webp");

    // ── 공통 설정 ──────────────────────────────────────────
    @Value("${storage.type:local}")
    private String storageType;

    // ── 로컬 저장 설정 ─────────────────────────────────────
    @Value("${file.upload-dir}")
    private String uploadDir;

    // ── S3 설정 (storage.type=s3 일 때만 사용) ─────────────
    @Value("${storage.s3.region:ap-northeast-2}")
    private String s3Region;

    @Value("${storage.s3.bucket:}")
    private String s3Bucket;

    @Value("${storage.s3.access-key:}")
    private String s3AccessKey;

    @Value("${storage.s3.secret-key:}")
    private String s3SecretKey;

    @Value("${storage.s3.base-url:}")
    private String s3BaseUrl;

    private S3Client s3Client;

    @PostConstruct
    private void init() {
        if ("s3".equalsIgnoreCase(storageType)) {
            s3Client = S3Client.builder()
                    .region(Region.of(s3Region))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(s3AccessKey, s3SecretKey)))
                    .build();
        }
    }

    /**
     * 이미지 파일을 저장하고 접근 가능한 URL을 반환합니다.
     * 로컬: "/uploads/파일명"
     * S3 : "https://버킷.s3.리전.amazonaws.com/파일명"
     */
    public String store(MultipartFile file) throws IOException {
        String ext = validateAndDetectExtension(file);
        String filename = UUID.randomUUID() + "." + ext;

        if ("s3".equalsIgnoreCase(storageType)) {
            return storeToS3(file, filename, ext);
        } else {
            return storeToLocal(file, filename);
        }
    }

    /**
     * 바이트 배열(외부에서 다운로드한 이미지 등)을 저장하고 접근 가능한 URL을 반환합니다.
     * 일괄 import 등 MultipartFile 이 아닌 원본을 저장할 때 사용합니다.
     */
    public String store(byte[] data, String ext) throws IOException {
        String e = detectImageExtension(data);
        if (e == null) {
            throw new IllegalArgumentException("올바른 이미지 파일이 아닙니다.");
        }
        String filename = UUID.randomUUID() + "." + e;

        if ("s3".equalsIgnoreCase(storageType)) {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(s3Bucket)
                    .key(filename)
                    .contentType("image/" + ("jpg".equals(e) ? "jpeg" : e))
                    .contentLength((long) data.length)
                    .build();
            s3Client.putObject(request, RequestBody.fromBytes(data));
            String base = s3BaseUrl.isBlank()
                    ? "https://" + s3Bucket + ".s3." + s3Region + ".amazonaws.com"
                    : s3BaseUrl.replaceAll("/$", "");
            return base + "/" + filename;
        } else {
            Path dir = Paths.get(uploadDir);
            Files.createDirectories(dir);
            Files.write(dir.resolve(filename), data);
            return "/uploads/" + filename;
        }
    }

    /**
     * 저장된 이미지를 삭제합니다.
     */
    public void delete(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) return;

        if ("s3".equalsIgnoreCase(storageType)) {
            deleteFromS3(imageUrl);
        } else {
            deleteFromLocal(imageUrl);
        }
    }

    // ── 로컬 저장 ──────────────────────────────────────────

    private String storeToLocal(MultipartFile file, String filename) throws IOException {
        Path dir = Paths.get(uploadDir);
        Files.createDirectories(dir);
        Files.copy(file.getInputStream(), dir.resolve(filename));
        return "/uploads/" + filename;
    }

    private void deleteFromLocal(String imageUrl) {
        try {
            String filename = imageUrl.replace("/uploads/", "");
            Path file = Paths.get(uploadDir).resolve(filename);
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
        }
    }

    // ── AWS S3 저장 ────────────────────────────────────────

    private String storeToS3(MultipartFile file, String filename, String ext) throws IOException {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(s3Bucket)
                .key(filename)
                .contentType(imageContentType(ext))
                .contentLength(file.getSize())
                .build();

        s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

        // base-url 설정이 있으면 사용, 없으면 기본 S3 URL 형식으로 반환
        String base = s3BaseUrl.isBlank()
                ? "https://" + s3Bucket + ".s3." + s3Region + ".amazonaws.com"
                : s3BaseUrl.replaceAll("/$", "");
        return base + "/" + filename;
    }

    private void deleteFromS3(String imageUrl) {
        try {
            // URL에서 파일명(키) 추출
            String key = imageUrl.substring(imageUrl.lastIndexOf('/') + 1);
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(s3Bucket)
                    .key(key)
                    .build();
            s3Client.deleteObject(request);
        } catch (Exception ignored) {
        }
    }

    // ── 공통 유틸 ──────────────────────────────────────────

    private String validateAndDetectExtension(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("이미지 파일을 선택해 주세요.");
        }
        String original = StringUtils.cleanPath(
                file.getOriginalFilename() != null ? file.getOriginalFilename() : "file");
        int dotIdx = original.lastIndexOf('.');
        String ext = dotIdx >= 0 ? original.substring(dotIdx + 1).toLowerCase() : "";
        if (!ALLOWED_EXT.contains(ext)) {
            throw new IllegalArgumentException("이미지 파일만 업로드할 수 있습니다. (jpg, png, gif, webp)");
        }

        byte[] header;
        try (InputStream input = file.getInputStream()) {
            header = input.readNBytes(12);
        }
        String detected = detectImageExtension(header);
        if (detected == null || !sameImageExtension(ext, detected)) {
            throw new IllegalArgumentException("파일 확장자와 실제 이미지 형식이 일치하지 않습니다.");
        }
        return detected;
    }

    static String detectImageExtension(byte[] data) {
        if (data == null) return null;
        if (data.length >= 3
                && unsigned(data[0]) == 0xFF && unsigned(data[1]) == 0xD8 && unsigned(data[2]) == 0xFF) {
            return "jpg";
        }
        if (data.length >= 8
                && unsigned(data[0]) == 0x89 && data[1] == 0x50 && data[2] == 0x4E && data[3] == 0x47
                && data[4] == 0x0D && data[5] == 0x0A && data[6] == 0x1A && data[7] == 0x0A) {
            return "png";
        }
        if (data.length >= 6) {
            String gif = new String(data, 0, 6, java.nio.charset.StandardCharsets.US_ASCII);
            if ("GIF87a".equals(gif) || "GIF89a".equals(gif)) return "gif";
        }
        if (data.length >= 12) {
            String riff = new String(data, 0, 4, java.nio.charset.StandardCharsets.US_ASCII);
            String webp = new String(data, 8, 4, java.nio.charset.StandardCharsets.US_ASCII);
            if ("RIFF".equals(riff) && "WEBP".equals(webp)) return "webp";
        }
        return null;
    }

    private static int unsigned(byte value) {
        return value & 0xFF;
    }

    private static boolean sameImageExtension(String filenameExt, String detectedExt) {
        return filenameExt.equals(detectedExt)
                || (("jpg".equals(filenameExt) || "jpeg".equals(filenameExt)) && "jpg".equals(detectedExt));
    }

    private static String imageContentType(String ext) {
        return switch (ext) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            default -> "application/octet-stream";
        };
    }
}
