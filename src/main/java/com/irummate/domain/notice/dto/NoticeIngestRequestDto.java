package com.irummate.domain.notice.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class NoticeIngestRequestDto {
    @NotNull
    @Size(max = 100)
    private List<@NotNull @Valid Item> notices;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        @NotBlank
        @Size(max = 100)
        @Pattern(regexp = "[1-9][0-9]{0,99}")
        private String externalId;

        @NotBlank
        @Size(max = 500)
        private String title;

        @NotNull
        private LocalDate publishedAt;

        @NotBlank
        @Size(max = 2048)
        private String url;

        @AssertTrue(message = "학교 공지 상세 URL과 externalId가 일치해야 합니다.")
        @JsonIgnore
        public boolean isValidSourceUrl() {
            return externalId != null && url != null
                    && url.equals("https://dormitory.uos.ac.kr/dormitory/web/bbs/dormitory_notice/" + externalId);
        }
    }
}
