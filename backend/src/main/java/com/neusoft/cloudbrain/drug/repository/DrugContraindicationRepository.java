package com.neusoft.cloudbrain.drug.repository;

import com.neusoft.cloudbrain.drug.entity.DrugContraindication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 药品禁忌 Repository
 */
@Repository
public interface DrugContraindicationRepository extends JpaRepository<DrugContraindication, Long> {

    List<DrugContraindication> findByDrugCode(String drugCode);

    List<DrugContraindication> findByDrugCodeAndConditionType(String drugCode, String conditionType);
}
