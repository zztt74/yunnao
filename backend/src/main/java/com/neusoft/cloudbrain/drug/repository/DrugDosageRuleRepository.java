package com.neusoft.cloudbrain.drug.repository;

import com.neusoft.cloudbrain.drug.entity.DrugDosageRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 药品剂量规则 Repository
 */
@Repository
public interface DrugDosageRuleRepository extends JpaRepository<DrugDosageRule, Long> {

    Optional<DrugDosageRule> findByDrugCode(String drugCode);
}
