package com.irummate.domain.user.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Null;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UserDetailsUpdateRequestDto {

    private String realName;
    private String studentId;

    @Min(value = 1, message = "age must be at least 1.")
    private Integer age;

    // 성별은 최초 등록 후 수정 불가
    @Null(message = "성별은 최초 등록 후 변경할 수 없습니다.")
    private String gender;
    private String department;
    private String phoneNumber;

    @AssertTrue(message = "realName cannot be blank.")
    public boolean isRealNameValid() {
        return realName == null || !realName.isBlank();
    }

    @AssertTrue(message = "studentId cannot be blank.")
    public boolean isStudentIdValid() {
        return studentId == null || !studentId.isBlank();
    }

    @AssertTrue(message = "department cannot be blank.")
    public boolean isDepartmentValid() {
        return department == null || !department.isBlank();
    }

    @AssertTrue(message = "phoneNumber cannot be blank.")
    public boolean isPhoneNumberValid() {
        return phoneNumber == null || phoneNumber.matches("^010-\\d{3,4}-\\d{4}$");
    }

    @AssertTrue(message = "At least one field must be provided.")
    public boolean hasAnyField() {
        return realName != null
                || studentId != null
                || age != null
                || department != null
                || phoneNumber != null;
    }
}
