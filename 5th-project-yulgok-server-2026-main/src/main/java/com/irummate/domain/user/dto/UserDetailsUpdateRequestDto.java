package com.irummate.domain.user.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UserDetailsUpdateRequestDto {

    private String realName;
    private String studentId;
    @Min(value = 1, message = "age는 1 이상이어야 합니다.")
    private Integer age;
    private String gender;
    private String department;
    private String phoneNumber;

    @AssertTrue(message = "realName은 공백일 수 없습니다.")
    public boolean isRealNameValid() {
        return realName == null || !realName.isBlank();
    }

    @AssertTrue(message = "studentId는 공백일 수 없습니다.")
    public boolean isStudentIdValid() {
        return studentId == null || !studentId.isBlank();
    }

    @AssertTrue(message = "gender는 공백일 수 없습니다.")
    public boolean isGenderValid() {
        return gender == null || (gender.equals("MALE") || gender.equals("FEMALE"));
    }

    @AssertTrue(message = "department는 공백일 수 없습니다.")
    public boolean isDepartmentValid() {
        return department == null || !department.isBlank();
    }

    @AssertTrue(message = "phoneNumber는 공백일 수 없습니다.")
    public boolean isPhoneNumberValid() {
        return phoneNumber == null || phoneNumber.matches("^010-\\d{3,4}-\\d{4}$");
    }

    @AssertTrue(message = "수정할 값이 하나 이상 있어야 합니다.")
    public boolean hasAnyField() {
        return realName != null
                || studentId != null
                || age != null
                || gender != null
                || department != null
                || phoneNumber != null;
    }
}
