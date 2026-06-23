package com.neusoft.cloudbrain.drug.service;

import com.neusoft.cloudbrain.drug.dto.DrugInteractionResponse;
import com.neusoft.cloudbrain.drug.dto.DrugResponse;
import com.neusoft.cloudbrain.drug.entity.*;
import com.neusoft.cloudbrain.drug.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 药品 Service
 *
 * 功能：
 * - 药品字典查询
 * - 药品详情（含成分、剂量规则、禁忌）
 * - 药品相互作用查询
 * - 按分类查询
 *
 * 规则：
 * - 药品使用系统内固定虚构编码、名称、规格和基础安全标签
 * - 确定性规则表用于过敏、相互作用、剂量和禁忌检查
 */
@Service
@RequiredArgsConstructor
public class DrugService {

    private final DrugRepository drugRepository;
    private final DrugIngredientRepository drugIngredientRepository;
    private final DrugInteractionRuleRepository drugInteractionRuleRepository;
    private final DrugDosageRuleRepository drugDosageRuleRepository;
    private final DrugContraindicationRepository drugContraindicationRepository;

    /**
     * 获取药品列表（分页）
     */
    @Transactional(readOnly = true)
    public Page<DrugResponse> getDrugList(int page, int pageSize) {
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), Math.max(1, pageSize));
        return drugRepository.findAll(pageable).map(this::toSimpleResponse);
    }

    /**
     * 按名称搜索药品
     */
    @Transactional(readOnly = true)
    public Page<DrugResponse> searchByName(String name, int page, int pageSize) {
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), Math.max(1, pageSize));
        return drugRepository.findByNameContaining(name, pageable).map(this::toSimpleResponse);
    }

    /**
     * 按分类查询药品
     */
    @Transactional(readOnly = true)
    public List<DrugResponse> getByCategory(String category) {
        return drugRepository.findByCategory(category).stream()
                .map(this::toSimpleResponse)
                .collect(Collectors.toList());
    }

    /**
     * 获取药品详情（含成分、剂量规则、禁忌）
     */
    @Transactional(readOnly = true)
    public DrugResponse getDrugById(Long id) {
        Drug drug = drugRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "DRUG_NOT_FOUND:药品不存在"));
        return toDetailResponse(drug);
    }

    /**
     * 按编码查询药品
     */
    @Transactional(readOnly = true)
    public DrugResponse getDrugByCode(String code) {
        Drug drug = drugRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException(
                        "DRUG_NOT_FOUND:药品不存在"));
        return toDetailResponse(drug);
    }

    /**
     * 查询两个药品之间的相互作用
     */
    @Transactional(readOnly = true)
    public List<DrugInteractionResponse> getInteraction(String drugACode, String drugBCode) {
        return drugInteractionRuleRepository
                .findByDrugACodeAndDrugBCode(drugACode, drugBCode)
                .stream()
                .map(rule -> new DrugInteractionResponse(
                        rule.getDrugACode(),
                        rule.getDrugBCode(),
                        rule.getSeverity(),
                        rule.getDescription()))
                .collect(Collectors.toList());
    }

    /**
     * 查询某药品的所有相互作用
     */
    @Transactional(readOnly = true)
    public List<DrugInteractionResponse> getInteractionsByDrugCode(String drugCode) {
        return drugInteractionRuleRepository
                .findByDrugACodeOrDrugBCode(drugCode, drugCode)
                .stream()
                .map(rule -> new DrugInteractionResponse(
                        rule.getDrugACode(),
                        rule.getDrugBCode(),
                        rule.getSeverity(),
                        rule.getDescription()))
                .collect(Collectors.toList());
    }

    /**
     * 查询药品禁忌（按过敏、疾病等条件类型）
     */
    @Transactional(readOnly = true)
    public List<DrugResponse.ContraindicationResponse> getContraindications(
            String drugCode, String conditionType) {
        List<DrugContraindication> contraindications;
        if (conditionType != null && !conditionType.isBlank()) {
            contraindications = drugContraindicationRepository
                    .findByDrugCodeAndConditionType(drugCode, conditionType);
        } else {
            contraindications = drugContraindicationRepository.findByDrugCode(drugCode);
        }

        return contraindications.stream()
                .map(c -> new DrugResponse.ContraindicationResponse(
                        c.getConditionType(),
                        c.getConditionValue(),
                        c.getDescription()))
                .collect(Collectors.toList());
    }

    /**
     * 转换为简单响应（不含关联数据）
     */
    private DrugResponse toSimpleResponse(Drug drug) {
        return new DrugResponse(
                drug.getId(),
                drug.getCode(),
                drug.getName(),
                drug.getGenericName(),
                drug.getDosageForm(),
                drug.getStrength(),
                drug.getUnit(),
                drug.getCategory(),
                drug.getStatus(),
                null, null, null,
                drug.getCreatedAt(),
                drug.getUpdatedAt()
        );
    }

    /**
     * 转换为详细响应（含成分、剂量规则、禁忌）
     */
    private DrugResponse toDetailResponse(Drug drug) {
        List<DrugResponse.IngredientResponse> ingredients = drugIngredientRepository
                .findByDrugId(drug.getId()).stream()
                .map(i -> new DrugResponse.IngredientResponse(
                        i.getIngredientName(), i.getAmount()))
                .collect(Collectors.toList());

        DrugResponse.DosageRuleResponse dosageRule = drugDosageRuleRepository
                .findByDrugCode(drug.getCode())
                .map(r -> new DrugResponse.DosageRuleResponse(
                        r.getMinDose(), r.getMaxDose(),
                        r.getMaxSingleDose(), r.getFrequency()))
                .orElse(null);

        List<DrugResponse.ContraindicationResponse> contraindications =
                drugContraindicationRepository.findByDrugCode(drug.getCode()).stream()
                        .map(c -> new DrugResponse.ContraindicationResponse(
                                c.getConditionType(),
                                c.getConditionValue(),
                                c.getDescription()))
                        .collect(Collectors.toList());

        return new DrugResponse(
                drug.getId(),
                drug.getCode(),
                drug.getName(),
                drug.getGenericName(),
                drug.getDosageForm(),
                drug.getStrength(),
                drug.getUnit(),
                drug.getCategory(),
                drug.getStatus(),
                ingredients,
                dosageRule,
                contraindications,
                drug.getCreatedAt(),
                drug.getUpdatedAt()
        );
    }
}
