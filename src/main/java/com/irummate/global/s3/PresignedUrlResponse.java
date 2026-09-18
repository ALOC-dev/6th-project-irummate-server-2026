package com.irummate.global.s3;

public record PresignedUrlResponse(
        String presignedUrl,  // 프론트가 PUT할 임시 URL
        String key,           // 버킷 내 경로 (DB 저장용)
        String fileUrl        // 업로드 완료 후 접근 URL
) {}
