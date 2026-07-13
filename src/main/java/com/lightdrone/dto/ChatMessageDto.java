package com.lightdrone.dto;

public record ChatMessageDto(
        Long id,
        Long roomId,
        String senderRole,
        String senderName,
        String content,
        String createdAt
) {}
