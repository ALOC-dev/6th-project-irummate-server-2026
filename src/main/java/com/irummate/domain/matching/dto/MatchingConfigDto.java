package com.irummate.domain.matching.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class MatchingConfigDto {
    @NotNull
    private LocalDate matchStartDate;

    @NotNull
    private LocalDate matchEndDate;
}
