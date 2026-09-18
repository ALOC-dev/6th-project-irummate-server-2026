package com.irummate.domain.user.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserDetailsResponseDto {

    private final String realName;
    private final String studentId;
    private final Integer age;
    private final String gender;
    private final String department;
    private final String phoneNumber;
}
