package com.neusoft.cloudbrain.prescription.repository;

import com.neusoft.cloudbrain.prescription.entity.PrescriptionReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 处方 AI 审核记录 Repository
 */
@Repository
public interface PrescriptionReviewRepository extends JpaRepository<PrescriptionReview, Long> {

    /**
     * 按处方 ID 查询最新审核记录
     */
    Optional<PrescriptionReview> findFirstByPrescriptionIdOrderByCreatedAtDesc(Long prescriptionId);

    /**
     * 按处方 ID 查询所有审核记录
     */
    java.util.List<PrescriptionReview> findByPrescriptionId(Long prescriptionId);
}
