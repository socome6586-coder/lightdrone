package com.lightdrone.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FileStorageServiceTest {

    @Test
    void detectsSupportedImageSignatures() {
        assertEquals("jpg", FileStorageService.detectImageExtension(
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}));
        assertEquals("png", FileStorageService.detectImageExtension(
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}));
        assertEquals("gif", FileStorageService.detectImageExtension(
                "GIF89a".getBytes(StandardCharsets.US_ASCII)));
        assertEquals("webp", FileStorageService.detectImageExtension(
                "RIFF0000WEBP".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    void rejectsNonImageContent() {
        assertNull(FileStorageService.detectImageExtension(
                "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8)));
    }
}
