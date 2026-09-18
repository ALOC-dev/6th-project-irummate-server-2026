package com.irummate.domain.matching.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MatchingRequestDto {
    @NotNull
    String receiverId;

    @NotNull
    @Pattern(regexp="HEART|REJECT",message = "matchStatus는 HEART 또는 REJECT만 가능합니다.")
    String matchStatus;
}
