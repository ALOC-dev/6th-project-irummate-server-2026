package com.irummate.domain.certification.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CertificationPresignRequestDto {

    @NotBlank(message = "fileName is required.")
    private String fileName;

    @NotBlank(message = "contentType is required.")
    private String contentType;
}
