package com.irummate.global.s3;

import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class S3Utils {

    private static final Duration PRESIGNED_URL_DURATION = Duration.ofMinutes(5);
    private static final long MAX_IMAGE_SIZE_BYTES = 5 * 1024 * 1024;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Value("${cloud.aws.region}")
    private String region;

    private final S3Presigner s3Presigner;
    private final S3Client s3Client;


    public String createDownloadUrl(String key) {
        GetObjectRequest objectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .responseContentDisposition("inline")
                .build();

        GetObjectPresignRequest presignRequest =
                GetObjectPresignRequest.builder()
                        .signatureDuration(PRESIGNED_URL_DURATION)
                        .getObjectRequest(objectRequest)
                        .build();

        return s3Presigner.presignGetObject(presignRequest)
                .url()
                .toString();
    }


    /**
     * 업로드용 Presigned URL 발급
     */
    public PresignedUrlResponse createUploadUrl(String fileName, String contentType, String dirName) {

        String extension = validateAndExtractExtension(fileName, contentType);

        String key = S3UrlUtil.createKey(dirName, extension);

        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(PRESIGNED_URL_DURATION)
                .putObjectRequest(objectRequest)
                .build();

        String presignedUrl = s3Presigner.presignPutObject(presignRequest).url().toString();
        String fileUrl = S3UrlUtil.createFileUrl(bucket, region, key);

        return new PresignedUrlResponse(presignedUrl, key, fileUrl);
    }

    public void validateUploadedImage(String key) {
        HeadObjectResponse headObjectResponse = headObject(key);

        validateUploadedImageMetadata(headObjectResponse);
        validateUploadedImageSignature(key, headObjectResponse.contentType());
    }

    /**
     * 파일 삭제
     */
    public void delete(String key) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build());
    }


    private static final Map<String, Set<String>> ALLOWED_EXTENSIONS_BY_CONTENT_TYPE = Map.of(
            "image/jpeg", Set.of("jpg", "jpeg"),
            "image/png", Set.of("png"),
            "image/webp", Set.of("webp")
    );

    private String validateAndExtractExtension(String fileName, String contentType) {
        Set<String> allowedExtensions = ALLOWED_EXTENSIONS_BY_CONTENT_TYPE.get(contentType);

        if (allowedExtensions == null) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE_FORMAT);
        }

        String extension = extractExtension(fileName);

        if (!allowedExtensions.contains(extension)) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE_FORMAT);
        }

        return extension.equals("jpeg") ? "jpg" : extension;
    }

    private String extractExtension(String fileName) {
        String normalized = fileName.replace("\\", "/");
        String lastFileName = normalized.substring(normalized.lastIndexOf("/") + 1);

        int dotIndex = lastFileName.lastIndexOf(".");
        if (dotIndex == -1 || dotIndex == lastFileName.length() - 1) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE_FORMAT);
        }

        return lastFileName.substring(dotIndex + 1).toLowerCase();
    }

    private HeadObjectResponse headObject(String key) {
        try {
            return s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
        } catch (NoSuchKeyException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                throw new BusinessException(ErrorCode.BAD_REQUEST);
            }
            throw e;
        }
    }

    private void validateUploadedImageMetadata(HeadObjectResponse headObjectResponse) {
        if (headObjectResponse.contentLength() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE_FORMAT);
        }

        if (headObjectResponse.contentLength() > MAX_IMAGE_SIZE_BYTES) {
            throw new BusinessException(ErrorCode.IMAGE_SIZE_EXCEEDED);
        }

        if (!ALLOWED_EXTENSIONS_BY_CONTENT_TYPE.containsKey(headObjectResponse.contentType())) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE_FORMAT);
        }
    }

    private void validateUploadedImageSignature(String key, String contentType) {
        ResponseBytes<GetObjectResponse> objectBytes = s3Client.getObject(
                GetObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .range("bytes=0-15")
                        .build(),
                ResponseTransformer.toBytes()
        );

        byte[] bytes = objectBytes.asByteArray();

        if (!matchesImageSignature(bytes, contentType)) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE_FORMAT);
        }
    }

    private boolean matchesImageSignature(byte[] bytes, String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> isJpeg(bytes);
            case "image/png" -> isPng(bytes);
            case "image/webp" -> isWebp(bytes);
            default -> false;
        };
    }

    private boolean isJpeg(byte[] bytes) {
        return bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF;
    }

    private boolean isPng(byte[] bytes) {
        return bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4E
                && bytes[3] == 0x47
                && bytes[4] == 0x0D
                && bytes[5] == 0x0A
                && bytes[6] == 0x1A
                && bytes[7] == 0x0A;
    }

    private boolean isWebp(byte[] bytes) {
        return bytes.length >= 12
                && bytes[0] == 0x52
                && bytes[1] == 0x49
                && bytes[2] == 0x46
                && bytes[3] == 0x46
                && bytes[8] == 0x57
                && bytes[9] == 0x45
                && bytes[10] == 0x42
                && bytes[11] == 0x50;
    }
}
