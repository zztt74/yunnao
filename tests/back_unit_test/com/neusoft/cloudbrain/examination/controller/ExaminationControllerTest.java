package com.neusoft.cloudbrain.examination.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.neusoft.cloudbrain.auth.dto.AuthPrincipal;
import com.neusoft.cloudbrain.common.exception.BusinessException;
import com.neusoft.cloudbrain.common.exception.GlobalExceptionHandler;
import com.neusoft.cloudbrain.common.filter.TraceIdFilter;
import com.neusoft.cloudbrain.examination.dto.ExaminationOrderCreateRequest;
import com.neusoft.cloudbrain.examination.dto.ExaminationOrderResponse;
import com.neusoft.cloudbrain.examination.dto.ExaminationResultResponse;
import com.neusoft.cloudbrain.examination.dto.ExaminationTrackingResponse;
import com.neusoft.cloudbrain.examination.service.ExaminationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ExaminationController 单元测试
 *
 * 覆盖三类用例：
 * - 正常：创建申请/开始执行/详情/患者分页/结果详情/流程追踪
 * - 异常：申请不存在返回 404、参数校验 400
 * - 边界：空申请列表、空流程追踪、无 body 取消请求
 */
@DisplayName("ExaminationController - 检查检验接口测试")
class ExaminationControllerTest {

    private MockMvc mockMvc;
    private ExaminationService examinationService;

    @BeforeEach
    void setUp() {
        examinationService = Mockito.mock(ExaminationService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(
                com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        mockMvc = MockMvcBuilders.standaloneSetup(new ExaminationController(examinationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .addFilters(new TraceIdFilter())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(String username, Set<String> roles) {
        AuthPrincipal principal = new AuthPrincipal(1L, username, roles, 0L);
        var authorities = roles.stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }

    private ExaminationOrderResponse sampleOrder(Long id) {
        return new ExaminationOrderResponse(
                id, 10L, 1L, 2L, "EXAMINATION", "XRAY_CHEST", "胸部X光",
                "ORDERED", LocalDateTime.now(), null, null, null, null,
                null, null, LocalDateTime.now(), null);
    }

    // ========== 正常情况测试 ==========

    @Test
    @DisplayName("createOrder - 医生创建检查申请成功")
    void createOrder_shouldReturnOrder() throws Exception {
        loginAs("doctor1", Set.of("DOCTOR"));
        when(examinationService.createOrder(any(ExaminationOrderCreateRequest.class)))
                .thenReturn(sampleOrder(100L));

        mockMvc.perform(post("/api/examinations")
                        .contentType("application/json")
                        .content("{\"encounterId\":10,\"orderType\":\"EXAMINATION\","
                                + "\"itemCode\":\"XRAY_CHEST\",\"itemName\":\"胸部X光\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.status").value("ORDERED"))
                .andExpect(jsonPath("$.data.itemName").value("胸部X光"));
    }

    @Test
    @DisplayName("startProgress - 开始执行检查成功")
    void startProgress_shouldReturnInProgress() throws Exception {
        ExaminationOrderResponse order = new ExaminationOrderResponse(
                100L, 10L, 1L, 2L, "EXAMINATION", "XRAY_CHEST", "胸部X光",
                "IN_PROGRESS", LocalDateTime.now(), LocalDateTime.now(),
                null, null, null, null, null, LocalDateTime.now(), null);
        when(examinationService.startProgress(100L)).thenReturn(order);

        mockMvc.perform(post("/api/examinations/100/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
    }

    @Test
    @DisplayName("getById - 返回申请详情")
    void getById_shouldReturnDetail() throws Exception {
        when(examinationService.getOrderById(50L)).thenReturn(sampleOrder(50L));

        mockMvc.perform(get("/api/examinations/50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(50))
                .andExpect(jsonPath("$.data.orderType").value("EXAMINATION"));
    }

    @Test
    @DisplayName("getByPatient - 返回患者检查申请分页")
    void getByPatient_shouldReturnPage() throws Exception {
        Page<ExaminationOrderResponse> page = new PageImpl<>(
                List.of(sampleOrder(1L)), PageRequest.of(0, 20), 1);
        when(examinationService.getOrdersByPatient(eq(1L), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/examinations/patient/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(1))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    @DisplayName("getResult - 返回检查结果详情")
    void getResult_shouldReturnResult() throws Exception {
        ExaminationResultResponse result = new ExaminationResultResponse(
                1L, 50L, "未见异常", "正常范围", "未见异常", "NORMAL",
                2L, 3L, "AI 解读正常", null, null, "SUCCESS", null,
                LocalDateTime.now(), null);
        when(examinationService.getResultByOrderId(50L)).thenReturn(result);

        mockMvc.perform(get("/api/examinations/50/result"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(50))
                .andExpect(jsonPath("$.data.resultText").value("未见异常"))
                .andExpect(jsonPath("$.data.abnormalFlag").value("NORMAL"));
    }

    @Test
    @DisplayName("getTrackingByPatient - 返回患者流程追踪")
    void getTrackingByPatient_shouldReturnList() throws Exception {
        ExaminationTrackingResponse t = new ExaminationTrackingResponse(
                1L, 10L, "EXAMINATION", "XRAY_CHEST", "胸部X光", "REVIEWED",
                "张医生", 1L, "内科", "1号楼2层", "已审核，可查看报告",
                null, null, LocalDateTime.now(), LocalDateTime.now(),
                LocalDateTime.now(), LocalDateTime.now(), null, null);
        when(examinationService.getTrackingByPatient(1L)).thenReturn(List.of(t));

        mockMvc.perform(get("/api/examinations/patient/1/tracking"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].orderId").value(1))
                .andExpect(jsonPath("$.data[0].nextAction").value("已审核，可查看报告"));
    }

    // ========== 异常情况测试 ==========

    @Test
    @DisplayName("getById - 不存在申请返回 404（由 GlobalExceptionHandler 处理）")
    void getById_notExist_shouldReturn404() throws Exception {
        when(examinationService.getOrderById(999L))
                .thenThrow(new BusinessException(
                        "EXAMINATION_ORDER_NOT_FOUND", "检查申请不存在", 404));

        mockMvc.perform(get("/api/examinations/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EXAMINATION_ORDER_NOT_FOUND"));
    }

    @Test
    @DisplayName("createOrder - 项目名称为空触发参数校验 400")
    void createOrder_blankItemName_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/examinations")
                        .contentType("application/json")
                        .content("{\"encounterId\":10,\"orderType\":\"EXAMINATION\",\"itemName\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("recordResult - 结果文本为空触发参数校验 400")
    void recordResult_blankResultText_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/examinations/100/result")
                        .contentType("application/json")
                        .content("{\"resultText\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // ========== 边界条件测试 ==========

    @Test
    @DisplayName("getByEncounter - 无申请时返回空数组")
    void getByEncounter_empty_shouldReturnEmptyArray() throws Exception {
        when(examinationService.getOrdersByEncounter(5L)).thenReturn(List.of());

        mockMvc.perform(get("/api/examinations/encounter/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("cancel - 不传 body 时使用默认取消请求")
    void cancel_withoutBody_shouldUseDefaultRequest() throws Exception {
        when(examinationService.cancelOrder(eq(100L), any())).thenReturn(sampleOrder(100L));

        mockMvc.perform(post("/api/examinations/100/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(100));

        verify(examinationService).cancelOrder(eq(100L), any());
    }

    @Test
    @DisplayName("getTrackingByEncounter - 空结果返回空数组")
    void getTrackingByEncounter_empty_shouldReturnEmptyArray() throws Exception {
        when(examinationService.getTrackingByEncounter(5L)).thenReturn(List.of());

        mockMvc.perform(get("/api/examinations/encounter/5/tracking"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    // ========== 补充端点测试 ==========

    @Test
    @DisplayName("reviewResult - 医生审核结果成功")
    void reviewResult_shouldReturnReviewed() throws Exception {
        ExaminationResultResponse result = new ExaminationResultResponse(
                1L, 100L, "未见异常", "正常范围", "未见异常", "NORMAL",
                2L, 3L, "AI 解读正常", null, null, "SUCCESS", null,
                LocalDateTime.now(), null);
        when(examinationService.reviewResult(100L)).thenReturn(result);

        mockMvc.perform(post("/api/examinations/100/review"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(100))
                .andExpect(jsonPath("$.data.abnormalFlag").value("NORMAL"));
    }

    @Test
    @DisplayName("returnForReentry - 退回重录成功")
    void returnForReentry_shouldReturnOrder() throws Exception {
        ExaminationOrderResponse order = new ExaminationOrderResponse(
                100L, 10L, 1L, 2L, "EXAMINATION", "XRAY_CHEST", "胸部X光",
                "IN_PROGRESS", LocalDateTime.now(), null, null, null, null,
                null, null, LocalDateTime.now(), null);
        when(examinationService.returnForReentry(eq(100L), any())).thenReturn(order);

        mockMvc.perform(post("/api/examinations/100/return")
                        .contentType("application/json")
                        .content("{\"reason\":\"需重新检查\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
    }

    @Test
    @DisplayName("getByDoctor - 返回医生检查申请分页")
    void getByDoctor_shouldReturnPage() throws Exception {
        Page<ExaminationOrderResponse> page = new PageImpl<>(
                List.of(sampleOrder(1L)), PageRequest.of(0, 20), 1);
        when(examinationService.getOrdersByDoctor(eq(2L), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/examinations/doctor/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(1))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    @DisplayName("recordResult - 录入检查结果成功")
    void recordResult_shouldReturnResult() throws Exception {
        ExaminationResultResponse result = new ExaminationResultResponse(
                1L, 100L, "未见异常", "正常范围", "未见异常", "NORMAL",
                2L, null, null, null, null, null, null,
                LocalDateTime.now(), null);
        when(examinationService.recordResult(eq(100L), any())).thenReturn(result);

        mockMvc.perform(post("/api/examinations/100/result")
                        .contentType("application/json")
                        .content("{\"resultText\":\"未见异常\",\"normalRange\":\"正常范围\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(100))
                .andExpect(jsonPath("$.data.resultText").value("未见异常"));
    }
}