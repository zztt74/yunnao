package com.neusoft.cloudbrain.examination.repository;

import com.neusoft.cloudbrain.examination.entity.ExaminationResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 检查检验结果 Repository
 */
@Repository
public interface ExaminationResultRepository extends JpaRepository<ExaminationResult, Long> {

    /**
     * 按申请 ID 查询结果（一个申请对应一条结果）
     */
    Optional<ExaminationResult> findByOrderId(Long orderId);
}
