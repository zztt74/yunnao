package com.neusoft.cloudbrain.department.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 科室实体
 */
@Entity
@Table(name = "department", uniqueConstraints = {
        @UniqueConstraint(name = "uk_department_code", columnNames = "code")
}, indexes = {
        @Index(name = "idx_department_parent_id", columnList = "parent_id"),
        @Index(name = "idx_department_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(nullable = false)
    @Builder.Default
    private Integer level = 1;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(nullable = false, length = 16)
    @Builder.Default
    private String status = "ENABLED";

    @Column(length = 255)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Long version;
}
