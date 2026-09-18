package com.irummate.domain.admin.dto;

import com.irummate.domain.certification.entity.Certification;
import com.irummate.global.util.HashIdsUtils;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class AdminCertificationResponseDto {

    private String certificationId;
    private String userId;
    private String userRealName;
    private String userNickname;
    private String semester;
    private String imageURL;
    private String status;
    private String adminComment;
    private LocalDateTime createdAt;

    public static AdminCertificationResponseDto from(Certification certification,
                                                     String certificationId,
                                                     String userId,
                                                     String imageURL) {


        return new AdminCertificationResponseDto(
                certificationId,
                userId,
                certification.getUser().getUserDetails() == null ? null : certification.getUser().getUserDetails().getRealName(),
                certification.getUser().getNickname(),
                certification.getSemester(),
                imageURL,
                certification.getCertificationStatus().name(),
                certification.getAdminComment(),
                certification.getCreatedAt()
        );
    }
}
