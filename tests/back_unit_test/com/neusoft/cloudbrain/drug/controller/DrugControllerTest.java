package com.neusoft.cloudbrain.drug.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.neusoft.cloudbrain.common.exception.BusinessException;
import com.neusoft.cloudbrain.common.exception.GlobalExceptionHandler;
import com.neusoft.cloudbrain.common.filter.TraceIdFilter;
import com.neusoft.cloudbrain.drug.dto.DrugInteractionResponse;
import com.neusoft.cloudbrain.drug.dto.DrugResponse;
import com.neusoft.cloudbrain.drug.service.DrugService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * DrugController 单元测试
 *
 * 覆盖三类用例：
 * - 正常：列表/详情/按编码/按分类/相互作用/禁忌查询
 * - 异常：药品不存在返回 404
 * - 边界：空列表、相互作用无参返回空数组、按分类空结果
 *
 * 药品接口全部为公开只读，无权限校验。
 */
@DisplayName("DrugController - 药品接口测试")
class DrugControllerTest {

    private MockMvc mockMvc;
    private DrugService drugService;

    @BeforeEach
    void setUp() {
        drugService = Mockito.mock(DrugService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(
                com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        mockMvc = MockMvcBuilders.standaloneSetup(new DrugController(drugService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .addFilters(new TraceIdFilter())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private DrugResponse sampleDrug() {
        return new DrugResponse(
                1L, "DRG001", "阿莫西林", "阿莫西林胶囊",
                "胶囊", "0.25g", "粒", "抗生素", "ENABLED",
                null, null, null, null, null);
    }

    private DrugInteractionResponse sampleInteraction() {
        return new DrugInteractionResponse(
                "DRG001", "DRG002", "MODERATE", "增加出血风险");
    }

    private DrugResponse.ContraindicationResponse sampleContraindication() {
        return new DrugResponse.ContraindicationResponse(
                "ALLERGY", "青霉素过敏", "青霉素过敏患者禁用");
    }

    // ========== 正常情况测试 ==========

    @Test
    @DisplayName("list - 返回药品分页列表")
    void list_shouldReturnPage() throws Exception {
        Page<DrugResponse> page = new PageImpl<>(
                List.of(sampleDrug()), PageRequest.of(0, 20), 1);
        when(drugService.getDrugList(1, 20)).thenReturn(page);

        mockMvc.perform(get("/api/drugs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(1))
                .andExpect(jsonPath("$.data.items[0].name").value("阿莫西林"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.page").value(1));
    }

    @Test
    @DisplayName("getById - 返回药品详情")
    void getById_shouldReturnDetail() throws Exception {
        when(drugService.getDrugById(1L)).thenReturn(sampleDrug());

        mockMvc.perform(get("/api/drugs/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.code").value("DRG001"))
                .andExpect(jsonPath("$.data.category").value("抗生素"));
    }

    @Test
    @DisplayName("getByCode - 按编码查询药品")
    void getByCode_shouldReturnDrug() throws Exception {
        when(drugService.getDrugByCode("DRG001")).thenReturn(sampleDrug());

        mockMvc.perform(get("/api/drugs/by-code/DRG001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("DRG001"))
                .andExpect(jsonPath("$.data.name").value("阿莫西林"));
    }

    @Test
    @DisplayName("getByCategory - 按分类查询药品")
    void getByCategory_shouldReturnList() throws Exception {
        when(drugService.getByCategory("抗生素")).thenReturn(List.of(sampleDrug()));

        mockMvc.perform(get("/api/drugs/by-category").param("category", "抗生素"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].category").value("抗生素"))
                .andExpect(jsonPath("$.data[0].name").value("阿莫西林"));
    }

    @Test
    @DisplayName("getInteraction - 按 drugCode 查询相互作用")
    void getInteraction_byDrugCode_shouldReturnList() throws Exception {
        when(drugService.getInteractionsByDrugCode("DRG001"))
                .thenReturn(List.of(sampleInteraction()));

        mockMvc.perform(get("/api/drugs/interaction").param("drugCode", "DRG001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].drugACode").value("DRG001"))
                .andExpect(jsonPath("$.data[0].drugBCode").value("DRG002"))
                .andExpect(jsonPath("$.data[0].severity").value("MODERATE"));
    }

    @Test
    @DisplayName("getInteraction - 按 drugACode 和 drugBCode 查询相互作用")
    void getInteraction_byPair_shouldReturnList() throws Exception {
        when(drugService.getInteraction("DRG001", "DRG002"))
                .thenReturn(List.of(sampleInteraction()));

        mockMvc.perform(get("/api/drugs/interaction")
                        .param("drugACode", "DRG001")
                        .param("drugBCode", "DRG002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].description").value("增加出血风险"));
    }

    @Test
    @DisplayName("getContraindications - 查询药品禁忌")
    void getContraindications_shouldReturnList() throws Exception {
        when(drugService.getContraindications(eq("DRG001"), any()))
                .thenReturn(List.of(sampleContraindication()));

        mockMvc.perform(get("/api/drugs/contraindications/DRG001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].conditionType").value("ALLERGY"))
                .andExpect(jsonPath("$.data[0].conditionValue").value("青霉素过敏"));
    }

    // ========== 异常情况测试 ==========

    @Test
    @DisplayName("getById - 药品不存在返回 404")
    void getById_notExist_shouldReturn404() throws Exception {
        when(drugService.getDrugById(999L))
                .thenThrow(new BusinessException("DRUG_NOT_FOUND", "药品不存在", 404));

        mockMvc.perform(get("/api/drugs/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DRUG_NOT_FOUND"));
    }

    @Test
    @DisplayName("getByCode - 编码不存在返回 404")
    void getByCode_notExist_shouldReturn404() throws Exception {
        when(drugService.getDrugByCode("NOT_EXIST"))
                .thenThrow(new BusinessException("DRUG_NOT_FOUND", "药品不存在", 404));

        mockMvc.perform(get("/api/drugs/by-code/NOT_EXIST"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DRUG_NOT_FOUND"));
    }

    // ========== 边界条件测试 ==========

    @Test
    @DisplayName("getInteraction - 无参数时返回空数组")
    void getInteraction_noParams_shouldReturnEmptyArray() throws Exception {
        mockMvc.perform(get("/api/drugs/interaction"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());

        verify(drugService, Mockito.never()).getInteraction(any(), any());
        verify(drugService, Mockito.never()).getInteractionsByDrugCode(any());
    }

    @Test
    @DisplayName("getByCategory - 分类下无药品时返回空数组")
    void getByCategory_empty_shouldReturnEmptyArray() throws Exception {
        when(drugService.getByCategory("未知分类")).thenReturn(List.of());

        mockMvc.perform(get("/api/drugs/by-category").param("category", "未知分类"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("list - 带 name 参数时调用 searchByName")
    void list_withName_shouldCallSearchByName() throws Exception {
        Page<DrugResponse> page = new PageImpl<>(
                List.of(sampleDrug()), PageRequest.of(0, 20), 1);
        when(drugService.searchByName(eq("阿莫"), eq(1), eq(20))).thenReturn(page);

        mockMvc.perform(get("/api/drugs").param("name", "阿莫"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].name").value("阿莫西林"));

        verify(drugService).searchByName("阿莫", 1, 20);
    }
}
