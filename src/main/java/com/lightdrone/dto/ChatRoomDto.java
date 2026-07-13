package com.lightdrone.dto;

/**
 * 채팅방 표시용 DTO.
 * claimedByMe = 현재 보고 있는 관리자가 이 방을 점유했는지 여부(목록에서 잠금 표시에 사용).
 */
public record ChatRoomDto(
        Long id,
        String memberName,
        String status,
        Long adminId,
        String adminName,
        boolean claimedByMe,
        String lastMessageAt
) {}
