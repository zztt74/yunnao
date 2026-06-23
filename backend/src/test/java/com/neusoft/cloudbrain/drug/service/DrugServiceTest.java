package com.neusoft.cloudbrain.drug.service;

import com.neusoft.cloudbrain.common.exception.BusinessException;
import com.neusoft.cloudbrain.drug.dto.DrugInteractionResponse;
import com.neusoft.cloudbrain.drug.dto.DrugResponse;
import com.neusoft.cloudbrain.drug.entity.*;
import com.neusoft.cloudbrain.drug.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * DrugService 单元测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DrugService - 药品服务测试")
class DrugServiceTest {

    @Mock
    private DrugRepository drugRepository;
    @Mock
    private DrugIngredientRepository drugIngredientRepository;
    @Mock
    private DrugInteractionRuleRepository drugInteractionRuleRepository;
    @Mock
    private DrugDosageRuleRepository drugDosageRuleRepository;
    @Mock
    private DrugContraindicationRepository drugContraindicationRepository;

    @InjectMocks
    private DrugService drugService;

    private Drug testDrug;

    @BeforeEach
    void setUp() {
        testDrug = Drug.builder()
                .id(1L)
                .code("DRG_001")
                .name("云脑降压片")
                .genericName("氢氯噻嗪片")
                .dosageForm("TABLET")
                .strength("25mg")
                .unit("片")
                .category("WESTERN")
                .status("ENABLED")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("获取药品详情 - 应返回含成分、剂量规则和禁忌的完整信息")
    void getDrugById_shouldReturnFullDetail() {
        DrugIngredient ingredient = DrugIngredient.builder()
                .ingredientName("氢氯噻嗪").amount("25mg").build();
        DrugDosageRule dosageRule = DrugDosageRule.builder()
                .minDose(new BigDecimal("0.500"))
                .maxDose(new BigDecimal("2.000"))
                .maxSingleDose(new BigDecimal("1.000"))
                .frequency("QD").build();
        DrugContraindication contraindication = DrugContraindication.builder()
                .conditionType("ALLERGY")
                .conditionValue("磺胺类过敏")
                .description("对磺胺类药物过敏者禁用").build();

        when(drugRepository.findById(1L)).thenReturn(Optional.of(testDrug));
        when(drugIngredientRepository.findByDrugId(1L)).thenReturn(List.of(ingredient));
        when(drugDosageRuleRepository.findByDrugCode("DRG_001")).thenReturn(Optional.of(dosageRule));
        when(drugContraindicationRepository.findByDrugCode("DRG_001"))
                .thenReturn(List.of(contraindication));

        DrugResponse response = drugService.getDrugById(1L);

        assertThat(response.code()).isEqualTo("DRG_001");
        assertThat(response.ingredients()).hasSize(1);
        assertThat(response.dosageRule()).isNotNull();
        assertThat(response.dosageRule().frequency()).isEqualTo("QD");
        assertThat(response.contraindications()).hasSize(1);
        assertThat(response.contraindications().get(0).conditionType()).isEqualTo("ALLERGY");
    }

    @Test
    @DisplayName("获取药品详情 - 不存在时应抛出异常")
    void getDrugById_shouldThrowWhenNotExists() {
        when(drugRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> drugService.getDrugById(99L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "DRUG_NOT_FOUND")
                .hasFieldOrPropertyWithValue("httpStatus", 404);
    }

    @Test
    @DisplayName("按编码查询药品 - 存在时应返回")
    void getDrugByCode_shouldReturnWhenExists() {
        when(drugRepository.findByCode("DRG_001")).thenReturn(Optional.of(testDrug));
        when(drugIngredientRepository.findByDrugId(1L)).thenReturn(List.of());
        when(drugDosageRuleRepository.findByDrugCode("DRG_001")).thenReturn(Optional.empty());
        when(drugContraindicationRepository.findByDrugCode("DRG_001")).thenReturn(List.of());

        DrugResponse response = drugService.getDrugByCode("DRG_001");

        assertThat(response.code()).isEqualTo("DRG_001");
    }

    @Test
    @DisplayName("查询药品相互作用 - 两个药品编码时应返回相互作用")
    void getInteraction_shouldReturnWhenBothCodesProvided() {
        DrugInteractionRule rule = DrugInteractionRule.builder()
                .drugACode("DRG_001")
                .drugBCode("DRG_003")
                .severity("MEDIUM")
                .description("利尿剂与头孢类合用可能增加肾毒性风险")
                .build();

        when(drugInteractionRuleRepository.findByDrugACodeAndDrugBCode("DRG_001", "DRG_003"))
                .thenReturn(List.of(rule));

        List<DrugInteractionResponse> result = drugService.getInteraction("DRG_001", "DRG_003");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).severity()).isEqualTo("MEDIUM");
    }

    @Test
    @DisplayName("查询药品禁忌 - 按条件类型过滤")
    void getContraindications_shouldFilterByConditionType() {
        DrugContraindication contraindication = DrugContraindication.builder()
                .drugCode("DRG_001")
                .conditionType("ALLERGY")
                .conditionValue("磺胺类过敏")
                .description("对磺胺类药物过敏者禁用")
                .build();

        when(drugContraindicationRepository.findByDrugCodeAndConditionType("DRG_001", "ALLERGY"))
                .thenReturn(List.of(contraindication));

        List<DrugResponse.ContraindicationResponse> result =
                drugService.getContraindications("DRG_001", "ALLERGY");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).conditionType()).isEqualTo("ALLERGY");
    }

    @Test
    @DisplayName("按分类查询药品 - 应返回对应分类的药品")
    void getByCategory_shouldReturnDrugsOfCategory() {
        when(drugRepository.findByCategory("WESTERN")).thenReturn(List.of(testDrug));

        List<DrugResponse> result = drugService.getByCategory("WESTERN");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).category()).isEqualTo("WESTERN");
    }
}
