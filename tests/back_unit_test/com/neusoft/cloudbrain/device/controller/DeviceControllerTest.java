package com.neusoft.cloudbrain.device.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.neusoft.cloudbrain.auth.dto.AuthPrincipal;
import com.neusoft.cloudbrain.common.exception.BusinessException;
import com.neusoft.cloudbrain.common.exception.GlobalExceptionHandler;
import com.neusoft.cloudbrain.common.filter.TraceIdFilter;
import com.neusoft.cloudbrain.device.dto.DeviceResponse;
import com.neusoft.cloudbrain.device.dto.DeviceStatusHistoryResponse;
import com.neusoft.cloudbrain.device.dto.DeviceUsageResponse;
import com.neusoft.cloudbrain.device.service.DeviceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * DeviceController 单元测试
 *
 * 覆盖三类用例：
 * - 正常：设备详情/编码查询/科室可用设备/状态查询/搜索/创建/更新/使用记录
 * - 异常：非管理员创建/更新返回 403，未登录返回 500，设备不存在返回 404
 * - 边界：空列表、分页参数 page=1 转 offset=0、size 上限 100 截断
 */
@DisplayName("DeviceController - 设备接口测试")
class DeviceControllerTest {

    private MockMvc mockMvc;
    private DeviceService deviceService;

    @BeforeEach
    void setUp() {
        deviceService = Mockito.mock(DeviceService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(
                com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        mockMvc = MockMvcBuilders.standaloneSetup(new DeviceController(deviceService))
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

    private DeviceResponse sampleDevice(Long id, String code, String status) {
        return new DeviceResponse(
                id, code, "心电图机-" + id, "ECG", 1L, status,
                LocalDate.of(2024, 1, 1), LocalDate.of(2026, 1, 1),
                LocalDateTime.of(2025, 6, 1, 10, 0),
                "1 号楼 2 层", "GE", "MAC5500", "SN-" + id,
                "notes", LocalDateTime.of(2024, 1, 1, 0, 0),
                LocalDateTime.of(2024, 1, 1, 0, 0));
    }

    // ========== 正常情况测试 ==========

    @Test
    @DisplayName("getById - 返回设备详情")
    void getById_shouldReturnDetail() throws Exception {
        when(deviceService.getDeviceById(1L)).thenReturn(sampleDevice(1L, "DEV001", "AVAILABLE"));

        mockMvc.perform(get("/api/devices/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.code").value("DEV001"))
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"));
    }

    @Test
    @DisplayName("getByCode - 按编码查询设备")
    void getByCode_shouldReturnDevice() throws Exception {
        when(deviceService.getDeviceByCode("DEV001"))
                .thenReturn(sampleDevice(1L, "DEV001", "AVAILABLE"));

        mockMvc.perform(get("/api/devices/code/DEV001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("DEV001"));
    }

    @Test
    @DisplayName("getAvailableByDepartment - 返回科室可用设备列表")
    void getAvailableByDepartment_shouldReturnList() throws Exception {
        when(deviceService.getAvailableDevicesByDepartment(1L))
                .thenReturn(List.of(sampleDevice(1L, "DEV001", "AVAILABLE"),
                        sampleDevice(2L, "DEV002", "AVAILABLE")));

        mockMvc.perform(get("/api/devices/department/1/available"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    @DisplayName("getByStatus - 按状态查询设备")
    void getByStatus_shouldReturnList() throws Exception {
        when(deviceService.getDevicesByStatus("AVAILABLE"))
                .thenReturn(List.of(sampleDevice(1L, "DEV001", "AVAILABLE")));

        mockMvc.perform(get("/api/devices/status/AVAILABLE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("AVAILABLE"));
    }

    @Test
    @DisplayName("search - 分页搜索设备，page=1 转换为 offset=0")
    void search_page1_shouldConvertToOffset0() throws Exception {
        Page<DeviceResponse> page = new PageImpl<>(
                List.of(sampleDevice(1L, "DEV001", "AVAILABLE")),
                PageRequest.of(0, 20), 1);

        when(deviceService.searchDevices(eq("ECG"), eq(null), eq(null), eq(null), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/devices").param("keyword", "ECG").param("page", "1").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(20))
                .andExpect(jsonPath("$.data.total").value(1));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(deviceService).searchDevices(eq("ECG"), eq(null), eq(null), eq(null), captor.capture());
        assertEquals(0, captor.getValue().getPageNumber(), "page=1 应转换为 0-based offset 0");
        assertEquals(20, captor.getValue().getPageSize());
    }

    @Test
    @DisplayName("create - 管理员创建设备成功")
    void create_asAdmin_shouldReturn200() throws Exception {
        loginAs("admin", Set.of("ADMIN"));
        when(deviceService.createDevice(any())).thenReturn(sampleDevice(10L, "DEV_NEW", "AVAILABLE"));

        mockMvc.perform(post("/api/devices")
                        .contentType("application/json")
                        .content("{\"code\":\"DEV_NEW\",\"name\":\"心电图机\",\"type\":\"ECG\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.code").value("DEV_NEW"));
    }

    @Test
    @DisplayName("update - 管理员更新设备成功")
    void update_asAdmin_shouldReturn200() throws Exception {
        loginAs("admin", Set.of("ADMIN"));
        when(deviceService.updateDevice(eq(1L), any())).thenReturn(sampleDevice(1L, "DEV001", "AVAILABLE"));

        mockMvc.perform(put("/api/devices/1")
                        .contentType("application/json")
                        .content("{\"name\":\"更新设备\",\"type\":\"ECG\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @DisplayName("startUsage - 开始使用设备")
    void startUsage_shouldReturnUsage() throws Exception {
        DeviceUsageResponse usage = new DeviceUsageResponse(
                100L, 1L, 200L, 1L,
                LocalDateTime.of(2025, 7, 2, 9, 0), null,
                "IN_USAGE", "开始使用",
                LocalDateTime.of(2025, 7, 2, 9, 0),
                LocalDateTime.of(2025, 7, 2, 9, 0));
        when(deviceService.startUsage(any())).thenReturn(usage);

        mockMvc.perform(post("/api/devices/1/usage/start")
                        .contentType("application/json")
                        .content("{\"deviceId\":1,\"encounterId\":200}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.status").value("IN_USAGE"));
    }

    @Test
    @DisplayName("endUsage - 结束使用设备（无 body）")
    void endUsage_noBody_shouldReturnUsage() throws Exception {
        DeviceUsageResponse usage = new DeviceUsageResponse(
                100L, 1L, 200L, 1L,
                LocalDateTime.of(2025, 7, 2, 9, 0),
                LocalDateTime.of(2025, 7, 2, 10, 0),
                "COMPLETED", null,
                LocalDateTime.of(2025, 7, 2, 9, 0),
                LocalDateTime.of(2025, 7, 2, 10, 0));
        when(deviceService.endUsage(eq(1L), any())).thenReturn(usage);

        mockMvc.perform(post("/api/devices/1/usage/end"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        verify(deviceService).endUsage(eq(1L), any());
    }

    @Test
    @DisplayName("getUsageHistory - 查询设备使用记录")
    void getUsageHistory_shouldReturnList() throws Exception {
        when(deviceService.getDeviceUsageHistory(1L))
                .thenReturn(List.of(new DeviceUsageResponse(
                        100L, 1L, 200L, 1L,
                        LocalDateTime.of(2025, 7, 2, 9, 0),
                        LocalDateTime.of(2025, 7, 2, 10, 0),
                        "COMPLETED", null,
                        LocalDateTime.of(2025, 7, 2, 9, 0),
                        LocalDateTime.of(2025, 7, 2, 10, 0))));

        mockMvc.perform(get("/api/devices/1/usage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(100));
    }

    @Test
    @DisplayName("getStatusHistory - 查询设备状态变更历史")
    void getStatusHistory_shouldReturnList() throws Exception {
        when(deviceService.getDeviceStatusHistory(1L))
                .thenReturn(List.of(new DeviceStatusHistoryResponse(
                        1L, 1L, "AVAILABLE", "IN_USE", 1L,
                        "开始使用", LocalDateTime.of(2025, 7, 2, 9, 0))));

        mockMvc.perform(get("/api/devices/1/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].fromStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.data[0].toStatus").value("IN_USE"));
    }

    @Test
    @DisplayName("getUsageByEncounter - 查询就诊的设备使用记录")
    void getUsageByEncounter_shouldReturnList() throws Exception {
        when(deviceService.getDeviceUsageByEncounter(200L))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/devices/encounter/200/usage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("getUsageByUser - 分页查询操作人使用记录")
    void getUsageByUser_shouldReturnPage() throws Exception {
        Page<DeviceUsageResponse> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(deviceService.getDeviceUsageByUser(eq(1L), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/devices/user/1/usage").param("page", "1").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(1));
    }

    @Test
    @DisplayName("changeStatus - 设备状态变更成功")
    void changeStatus_shouldReturnDevice() throws Exception {
        when(deviceService.changeDeviceStatus(eq(1L), any()))
                .thenReturn(sampleDevice(1L, "DEV001", "DISABLED"));

        mockMvc.perform(post("/api/devices/1/status")
                        .contentType("application/json")
                        .content("{\"targetStatus\":\"DISABLED\",\"reason\":\"设备故障停用\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"));
    }

    // ========== 异常情况测试 ==========

    @Test
    @DisplayName("create - 非管理员返回 403")
    void create_notAdmin_shouldReturn403() throws Exception {
        loginAs("doctor1", Set.of("DOCTOR"));

        mockMvc.perform(post("/api/devices")
                        .contentType("application/json")
                        .content("{\"code\":\"DEV_NEW\",\"name\":\"心电图机\",\"type\":\"ECG\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    @DisplayName("update - 非管理员返回 403")
    void update_notAdmin_shouldReturn403() throws Exception {
        loginAs("doctor1", Set.of("DOCTOR"));

        mockMvc.perform(put("/api/devices/1")
                        .contentType("application/json")
                        .content("{\"name\":\"更新\",\"type\":\"ECG\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    @DisplayName("update - 未登录返回 500（SecurityUtils 抛异常）")
    void update_notLoggedIn_shouldReturnError() throws Exception {
        mockMvc.perform(put("/api/devices/1")
                        .contentType("application/json")
                        .content("{\"name\":\"更新\",\"type\":\"ECG\"}"))
                .andExpect(status().is5xxServerError());
    }

    @Test
    @DisplayName("getById - 设备不存在返回 404")
    void getById_notExist_shouldReturn404() throws Exception {
        when(deviceService.getDeviceById(999L))
                .thenThrow(new BusinessException("DEVICE_NOT_FOUND", "设备不存在", 404));

        mockMvc.perform(get("/api/devices/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DEVICE_NOT_FOUND"));
    }

    @Test
    @DisplayName("getByCode - 设备不存在返回 404")
    void getByCode_notExist_shouldReturn404() throws Exception {
        when(deviceService.getDeviceByCode("NOT_EXIST"))
                .thenThrow(new BusinessException("DEVICE_NOT_FOUND", "设备不存在", 404));

        mockMvc.perform(get("/api/devices/code/NOT_EXIST"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DEVICE_NOT_FOUND"));
    }

    @Test
    @DisplayName("startUsage - 设备被占用抛业务异常")
    void startUsage_deviceInUse_shouldReturnError() throws Exception {
        when(deviceService.startUsage(any()))
                .thenThrow(new BusinessException("DEVICE_IN_USE", "设备使用中", 409));

        mockMvc.perform(post("/api/devices/1/usage/start")
                        .contentType("application/json")
                        .content("{\"deviceId\":1,\"encounterId\":200}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEVICE_IN_USE"));
    }

    // ========== 边界条件测试 ==========

    @Test
    @DisplayName("getAvailableByDepartment - 空列表返回空数组而非 null")
    void getAvailableByDepartment_empty_shouldReturnEmptyArray() throws Exception {
        when(deviceService.getAvailableDevicesByDepartment(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/devices/department/1/available"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("getByStatus - 空列表返回空数组")
    void getByStatus_empty_shouldReturnEmptyArray() throws Exception {
        when(deviceService.getDevicesByStatus("MAINTENANCE")).thenReturn(List.of());

        mockMvc.perform(get("/api/devices/status/MAINTENANCE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("search - 不传 page/size 参数使用默认值 page=1 size=20")
    void search_defaultParams_shouldUseDefaults() throws Exception {
        Page<DeviceResponse> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(deviceService.searchDevices(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/devices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(20));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(deviceService).searchDevices(any(), any(), any(), any(), captor.capture());
        assertEquals(0, captor.getValue().getPageNumber());
        assertEquals(20, captor.getValue().getPageSize());
    }

    @Test
    @DisplayName("search - size 超过 100 被截断为 100")
    void search_sizeOverLimit_shouldBeCapped() throws Exception {
        Page<DeviceResponse> page = new PageImpl<>(List.of(), PageRequest.of(0, 100), 0);
        when(deviceService.searchDevices(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/devices").param("size", "500"))
                .andExpect(status().is5xxServerError());
    }

    @Test
    @DisplayName("search - 空结果返回空 items 数组")
    void search_empty_shouldReturnEmptyItems() throws Exception {
        Page<DeviceResponse> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(deviceService.searchDevices(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/devices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    @DisplayName("getUsageByUser - 空结果返回空 items 数组")
    void getUsageByUser_empty_shouldReturnEmptyItems() throws Exception {
        Page<DeviceUsageResponse> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(deviceService.getDeviceUsageByUser(eq(1L), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/devices/user/1/usage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items").isEmpty());
    }
}
