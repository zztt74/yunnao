package com.neusoft.cloudbrain.drug.repository;

import com.neusoft.cloudbrain.drug.entity.DrugInteractionRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 药品相互作用规则 Repository
 */
@Repository
public interface DrugInteractionRuleRepository extends JpaRepository<DrugInteractionRule, Long> {

    /**
     * 根据药品 A 编码查询
     */
    List<DrugInteractionRule> findByDrugACode(String drugACode);

    /**
     * 根据药品 A 或 B 编码查询
     */
    List<DrugInteractionRule> findByDrugACodeOrDrugBCode(String drugACode, String drugBCode);

    /**
     * 查询两个药品之间的相互作用
     */
    List<DrugInteractionRule> findByDrugACodeAndDrugBCode(String drugACode, String drugBCode);
}
