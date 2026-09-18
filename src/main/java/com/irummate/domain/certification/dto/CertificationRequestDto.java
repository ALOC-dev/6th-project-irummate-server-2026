package com.irummate.domain.certification.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CertificationRequestDto {

    @NotBlank(message = "imageKey is required.")
    private String imageKey;
}
