package com.neusoft.cloudbrain.prescription.repository;

import com.neusoft.cloudbrain.prescription.entity.PrescriptionItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 处方明细 Repository
 */
@Repository
public interface PrescriptionItemRepository extends JpaRepository<PrescriptionItem, Long> {

    /**
     * 按处方 ID 查询明细
     */
    List<PrescriptionItem> findByPrescriptionId(Long prescriptionId);

    /**
     * 按处方 ID 删除明细
     */
    void deleteByPrescriptionId(Long prescriptionId);
}
