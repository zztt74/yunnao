package com.neusoft.cloudbrain.doctor.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 医生实体
 */
@Entity
@Table(name = "doctor", uniqueConstraints = {
        @UniqueConstraint(name = "uk_doctor_user_id", columnNames = "user_id")
}, indexes = {
        @Index(name = "idx_doctor_department_id", columnList = "department_id"),
        @Index(name = "idx_doctor_name", columnList = "name"),
        @Index(name = "idx_doctor_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Doctor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "department_id", nullable = false)
    private Long departmentId;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(nullable = false, length = 32)
    private String title;

    @Column(length = 255)
    private String specialty;

    @Column(nullable = false, length = 16)
    @Builder.Default
    private String status = "ENABLED";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Long version;
}
