package com.neusoft.cloudbrain.encounter.repository;

import com.neusoft.cloudbrain.encounter.entity.EncounterDiagnosis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 就诊诊断 Repository
 */
@Repository
public interface EncounterDiagnosisRepository extends JpaRepository<EncounterDiagnosis, Long> {

    /**
     * 按就诊 ID 查询所有诊断
     */
    List<EncounterDiagnosis> findByEncounterId(Long encounterId);

    /**
     * 按就诊 ID 和来源查询诊断
     */
    List<EncounterDiagnosis> findByEncounterIdAndSource(Long encounterId, String source);

    /**
     * 按就诊 ID 和类型查询诊断
     */
    List<EncounterDiagnosis> findByEncounterIdAndType(Long encounterId, String type);

    /**
     * 按就诊 ID、类型和来源查询诊断
     */
    List<EncounterDiagnosis> findByEncounterIdAndTypeAndSource(Long encounterId, String type, String source);

    /**
     * 检查就诊是否存在医生最终诊断（type=FINAL, source=DOCTOR）
     */
    boolean existsByEncounterIdAndTypeAndSource(Long encounterId, String type, String source);
}
