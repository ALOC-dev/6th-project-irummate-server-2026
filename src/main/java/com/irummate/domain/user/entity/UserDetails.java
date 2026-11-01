package com.irummate.domain.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_details")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserDetails {

    // Users 테이블과 공유하는 사용자 식별자
    @Id
    @Column(name = "user_id")
    private Long userId;

    // 추가 정보의 소유 사용자
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    // 실명
    @Column(name = "real_name", nullable = false)
    private String realName;

    // 학번
    @Column(name = "student_id", nullable = false)
    private String studentId;

    // 나이
    @Column(nullable = false)
    private int age;

    // 성별
    @Column(nullable = false)
    private String gender;

    // 학과
    @Column(nullable = false)
    private String department;

    // 전화번호
    @Column(name = "phone_number", nullable = false)
    private String phoneNumber;

    @Builder
    public UserDetails(Users user, String realName, String studentId, int age, String gender, String department, String phoneNumber){
        this.user = user;
        this.realName = realName;
        this.studentId = studentId;
        this.age = age;
        this.gender = gender;
        this.department = department;
        this.phoneNumber = phoneNumber;
    }

    public void update(String realName, String studentId, Integer age, String gender, String department, String phoneNumber) {
        if (realName != null) {
            this.realName = realName;
        }
        if (studentId != null) {
            this.studentId = studentId;
        }
        if (age != null) {
            this.age = age;
        }
        if (gender != null) {
            this.gender = gender;
        }
        if (department != null) {
            this.department = department;
        }
        if (phoneNumber != null) {
            this.phoneNumber = phoneNumber;
        }
    }


}
