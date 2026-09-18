package com.irummate.global.s3;

import java.util.UUID;

public final class S3UrlUtil {

    private S3UrlUtil() {
        // 인스턴스화 방지
    }

    /**
     * 버킷 내 저장 경로(key) 생성
     * 예: images/550e8400-..._cat.jpg
     */
    public static String createKey(String dirName, String extension) {
        return dirName + "/" + UUID.randomUUID() + "." + extension;
    }

    /**
     * 업로드 완료 후 접근 가능한 파일 URL 생성
     */
    public static String createFileUrl(String bucket, String region, String key) {
        return "https://" + bucket + ".s3." + region + ".amazonaws.com/" + key;
    }

    /**
     * 파일 URL에서 key 추출 (삭제 시 URL만 갖고 있을 때 사용)
     * 예: https://bucket.s3.region.amazonaws.com/images/uuid_cat.jpg → images/uuid_cat.jpg
     */
    public static String extractKeyFromUrl(String fileUrl) {
        return fileUrl.substring(fileUrl.indexOf(".amazonaws.com/") + ".amazonaws.com/".length());
    }
}
