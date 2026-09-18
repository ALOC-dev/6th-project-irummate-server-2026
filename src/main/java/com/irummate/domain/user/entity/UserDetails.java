package com.irummate.domain.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_details")
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class UserDetails {

    @Id
    @Column(name = "user_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private Users user;

    @Column(name = "real_name", nullable = false)
    private String realName;

    @Column(name = "student_id", nullable = false)
    private String studentId;   // 학번

    @Column(nullable = false)
    private int age;

    @Column(nullable = false)
    private String gender;

    @Column(nullable = false)
    private String department;

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
