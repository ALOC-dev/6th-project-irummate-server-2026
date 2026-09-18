package com.irummate.domain.user.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UserDetailsRequestDto {

    @NotBlank(message = "realName은 필수입니다.")
    private String realName;

    @NotBlank(message = "studentId는 필수입니다.")
    private String studentId;

    @NotNull(message = "age는 필수입니다.")
    @Min(value = 1, message = "age는 1 이상이어야 합니다.")
    private Integer age;

    @NotBlank(message = "gender는 필수입니다.")
    @Pattern(regexp = "MALE|FEMALE", message = "gender는 MALE 또는 FEMALE이어야 합니다.")
    private String gender;

    @NotBlank(message = "department는 필수입니다.")
    private String department;

    @NotBlank(message = "phoneNumber is required.")
    @Pattern(regexp = "^010-\\d{3,4}-\\d{4}$", message = "phoneNumber must match 010-1234-5678 format.")
    private String phoneNumber;
}
