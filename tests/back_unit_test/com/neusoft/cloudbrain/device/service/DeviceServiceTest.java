package com.neusoft.cloudbrain.device.service;

import com.neusoft.cloudbrain.common.exception.BusinessException;
import com.neusoft.cloudbrain.device.dto.DeviceCreateRequest;
import com.neusoft.cloudbrain.device.dto.DeviceEndUsageRequest;
import com.neusoft.cloudbrain.device.dto.DeviceResponse;
import com.neusoft.cloudbrain.device.dto.DeviceStartUsageRequest;
import com.neusoft.cloudbrain.device.dto.DeviceStatusChangeRequest;
import com.neusoft.cloudbrain.device.dto.DeviceUsageResponse;
import com.neusoft.cloudbrain.device.dto.DeviceUpdateRequest;
import com.neusoft.cloudbrain.device.entity.Device;
import com.neusoft.cloudbrain.device.entity.DeviceStatusHistory;
import com.neusoft.cloudbrain.device.entity.DeviceUsage;
import com.neusoft.cloudbrain.device.repository.DeviceRepository;
import com.neusoft.cloudbrain.device.repository.DeviceStatusHistoryRepository;
import com.neusoft.cloudbrain.device.repository.DeviceUsageRepository;
import com.neusoft.cloudbrain.encounter.entity.Encounter;
import com.neusoft.cloudbrain.department.repository.DepartmentRepository;
import com.neusoft.cloudbrain.encounter.repository.EncounterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DeviceService 单元测试
 *
 * 覆盖文档 11.2 必测场景：
 * - 开始使用设备（AVAILABLE → IN_USE，并发控制）
 * - 结束使用（IN_USE → AVAILABLE）
 * - 状态机流转（异常上报、送修、修复、停用、启用）
 * - 并发冲突
 * - IN_USE → ABNORMAL 自动结束使用记录
 * - 非法状态转换
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DeviceService - 设备服务测试")
class DeviceServiceTest {

    @Mock
    private DeviceRepository deviceRepository;
    @Mock
    private DeviceUsageRepository deviceUsageRepository;
    @Mock
    private DeviceStatusHistoryRepository deviceStatusHistoryRepository;
    @Mock
    private EncounterRepository encounterRepository;
    @Mock
    private DepartmentRepository departmentRepository;

    @InjectMocks
    private DeviceService deviceService;

    private Device testDevice;
    private DeviceUsage testUsage;
    private Encounter testEncounter;

    @BeforeEach
    void setUp() {
        testDevice = Device.builder()
                .id(1L)
                .code("DEV-MON-001")
                .name("多功能监护仪 #1")
                .type("MONITOR")
                .departmentId(1L)
                .status("AVAILABLE")
                .location("内科病房 301")
                .manufacturer("Philips")
                .model("MX800")
                .serialNumber("SN-MON-001")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        testUsage = DeviceUsage.builder()
                .id(1L)
                .deviceId(1L)
                .encounterId(1L)
                .usedBy(20L)
                .startTime(LocalDateTime.now())
                .status("IN_USAGE")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        testEncounter = Encounter.builder()
                .id(1L)
                .patientId(1L)
                .doctorId(1L)
                .status("IN_PROGRESS")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // ============================================================
    // 开始使用设备 AVAILABLE → IN_USE
    // ============================================================

    @Test
    @DisplayName("开始使用 - AVAILABLE → IN_USE")
    void startUsage_shouldTransitionToInUse() {
        DeviceStartUsageRequest request = new DeviceStartUsageRequest(1L, 1L, "常规检查");

        when(deviceRepository.findById(1L)).thenReturn(Optional.of(testDevice));
        when(encounterRepository.findById(1L)).thenReturn(Optional.of(testEncounter));
        when(deviceRepository.updateStatusIfCurrent(
                eq(1L), eq("AVAILABLE"), eq("IN_USE"), any())).thenReturn(1);
        when(deviceUsageRepository.save(any(DeviceUsage.class))).thenAnswer(invocation -> {
            DeviceUsage u = invocation.getArgument(0);
            u.setId(1L);
            return u;
        });
        when(deviceStatusHistoryRepository.save(any(DeviceStatusHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DeviceUsageResponse response = deviceService.startUsage(request);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("IN_USAGE");
        assertThat(response.deviceId()).isEqualTo(1L);
        verify(deviceStatusHistoryRepository).save(any(DeviceStatusHistory.class));
    }

    @Test
    @DisplayName("开始使用 - 设备不存在时抛出 BusinessException(404)")
    void startUsage_shouldThrowWhenDeviceNotFound() {
        DeviceStartUsageRequest request = new DeviceStartUsageRequest(99L, 1L, "检查");

        when(deviceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deviceService.startUsage(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("httpStatus", 404);
    }

    @Test
    @DisplayName("开始使用 - 设备非 AVAILABLE 状态时抛出 BusinessException(409)")
    void startUsage_shouldThrowWhenDeviceNotAvailable() {
        testDevice.setStatus("IN_USE");
        DeviceStartUsageRequest request = new DeviceStartUsageRequest(1L, 1L, "检查");

        when(deviceRepository.findById(1L)).thenReturn(Optional.of(testDevice));

        assertThatThrownBy(() -> deviceService.startUsage(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("httpStatus", 409);
    }

    @Test
    @DisplayName("开始使用 - 并发冲突时抛出 BusinessException(409)")
    void startUsage_shouldThrowWhenConcurrentConflict() {
        DeviceStartUsageRequest request = new DeviceStartUsageRequest(1L, 1L, "检查");

        when(deviceRepository.findById(1L)).thenReturn(Optional.of(testDevice));
        when(encounterRepository.findById(1L)).thenReturn(Optional.of(testEncounter));
        when(deviceRepository.updateStatusIfCurrent(
                eq(1L), eq("AVAILABLE"), eq("IN_USE"), any())).thenReturn(0);

        assertThatThrownBy(() -> deviceService.startUsage(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("httpStatus", 409);
    }

    // ============================================================
    // 结束使用 IN_USE → AVAILABLE
    // ============================================================

    @Test
    @DisplayName("结束使用 - IN_USE → AVAILABLE")
    void endUsage_shouldTransitionToAvailable() {
        testDevice.setStatus("IN_USE");
        DeviceEndUsageRequest request = new DeviceEndUsageRequest("检查完成");

        when(deviceRepository.findById(1L)).thenReturn(Optional.of(testDevice));
        when(deviceUsageRepository.findFirstByDeviceIdAndStatusOrderByStartTimeDesc(1L, "IN_USAGE"))
                .thenReturn(Optional.of(testUsage));
        when(deviceUsageRepository.updateStatusIfCurrent(
                eq(1L), eq("IN_USAGE"), eq("COMPLETED"), any(), any())).thenReturn(1);
        when(deviceRepository.updateStatusIfCurrent(
                eq(1L), eq("IN_USE"), eq("AVAILABLE"), any())).thenReturn(1);
        when(deviceStatusHistoryRepository.save(any(DeviceStatusHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DeviceUsageResponse response = deviceService.endUsage(1L, request);

        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.endTime()).isNotNull();
    }

    @Test
    @DisplayName("结束使用 - 无进行中的使用记录时抛出 BusinessException(409)")
    void endUsage_shouldThrowWhenNoActiveUsage() {
        testDevice.setStatus("IN_USE");

        when(deviceRepository.findById(1L)).thenReturn(Optional.of(testDevice));
        when(deviceUsageRepository.findFirstByDeviceIdAndStatusOrderByStartTimeDesc(1L, "IN_USAGE"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> deviceService.endUsage(1L, new DeviceEndUsageRequest(null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("httpStatus", 409);
    }

    // ============================================================
    // 状态变更：异常上报、送修、修复、停用、启用
    // ============================================================

    @Test
    @DisplayName("异常上报 - IN_USE → ABNORMAL 自动结束使用记录")
    void changeStatus_shouldAutoEndUsageWhenAbnormal() {
        testDevice.setStatus("IN_USE");
        DeviceStatusChangeRequest request = new DeviceStatusChangeRequest("ABNORMAL", "屏幕显示异常");

        when(deviceRepository.findById(1L)).thenReturn(Optional.of(testDevice));
        when(deviceUsageRepository.findFirstByDeviceIdAndStatusOrderByStartTimeDesc(1L, "IN_USAGE"))
                .thenReturn(Optional.of(testUsage));
        when(deviceUsageRepository.updateStatusIfCurrent(
                eq(1L), eq("IN_USAGE"), eq("ABORTED"), any(), any())).thenReturn(1);
        when(deviceRepository.updateStatusIfCurrent(
                eq(1L), eq("IN_USE"), eq("ABNORMAL"), any())).thenReturn(1);
        when(deviceStatusHistoryRepository.save(any(DeviceStatusHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DeviceResponse response = deviceService.changeDeviceStatus(1L, request);

        assertThat(response.status()).isEqualTo("ABNORMAL");
        verify(deviceUsageRepository).updateStatusIfCurrent(
                eq(1L), eq("IN_USAGE"), eq("ABORTED"), any(), any());
    }

    @Test
    @DisplayName("送修 - ABNORMAL → MAINTENANCE")
    void changeStatus_shouldTransitionAbnormalToMaintenance() {
        testDevice.setStatus("ABNORMAL");
        DeviceStatusChangeRequest request = new DeviceStatusChangeRequest("MAINTENANCE", "送修");

        when(deviceRepository.findById(1L)).thenReturn(Optional.of(testDevice));
        when(deviceRepository.updateStatusIfCurrent(
                eq(1L), eq("ABNORMAL"), eq("MAINTENANCE"), any())).thenReturn(1);
        when(deviceStatusHistoryRepository.save(any(DeviceStatusHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DeviceResponse response = deviceService.changeDeviceStatus(1L, request);

        assertThat(response.status()).isEqualTo("MAINTENANCE");
    }

    @Test
    @DisplayName("修复完成 - ABNORMAL → AVAILABLE")
    void changeStatus_shouldTransitionAbnormalToAvailable() {
        testDevice.setStatus("ABNORMAL");
        DeviceStatusChangeRequest request = new DeviceStatusChangeRequest("AVAILABLE", "修复完成");

        when(deviceRepository.findById(1L)).thenReturn(Optional.of(testDevice));
        when(deviceRepository.updateStatusIfCurrent(
                eq(1L), eq("ABNORMAL"), eq("AVAILABLE"), any())).thenReturn(1);
        when(deviceStatusHistoryRepository.save(any(DeviceStatusHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DeviceResponse response = deviceService.changeDeviceStatus(1L, request);

        assertThat(response.status()).isEqualTo("AVAILABLE");
    }

    @Test
    @DisplayName("停用 - AVAILABLE → DISABLED")
    void changeStatus_shouldTransitionAvailableToDisabled() {
        DeviceStatusChangeRequest request = new DeviceStatusChangeRequest("DISABLED", "设备老旧停用");

        when(deviceRepository.findById(1L)).thenReturn(Optional.of(testDevice));
        when(deviceRepository.updateStatusIfCurrent(
                eq(1L), eq("AVAILABLE"), eq("DISABLED"), any())).thenReturn(1);
        when(deviceStatusHistoryRepository.save(any(DeviceStatusHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DeviceResponse response = deviceService.changeDeviceStatus(1L, request);

        assertThat(response.status()).isEqualTo("DISABLED");
    }

    @Test
    @DisplayName("重新启用 - DISABLED → AVAILABLE")
    void changeStatus_shouldTransitionDisabledToAvailable() {
        testDevice.setStatus("DISABLED");
        DeviceStatusChangeRequest request = new DeviceStatusChangeRequest("AVAILABLE", "重新启用");

        when(deviceRepository.findById(1L)).thenReturn(Optional.of(testDevice));
        when(deviceRepository.updateStatusIfCurrent(
                eq(1L), eq("DISABLED"), eq("AVAILABLE"), any())).thenReturn(1);
        when(deviceStatusHistoryRepository.save(any(DeviceStatusHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DeviceResponse response = deviceService.changeDeviceStatus(1L, request);

        assertThat(response.status()).isEqualTo("AVAILABLE");
    }

    @Test
    @DisplayName("非法状态转换 - DISABLED → IN_USE 抛出 BusinessException(400)")
    void changeStatus_shouldThrowWhenInvalidTransition() {
        testDevice.setStatus("DISABLED");
        DeviceStatusChangeRequest request = new DeviceStatusChangeRequest("IN_USE", "尝试使用");

        when(deviceRepository.findById(1L)).thenReturn(Optional.of(testDevice));

        assertThatThrownBy(() -> deviceService.changeDeviceStatus(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("httpStatus", 400);
    }

    @Test
    @DisplayName("状态变更 - 并发冲突时抛出 BusinessException(409)")
    void changeStatus_shouldThrowWhenConcurrentConflict() {
        DeviceStatusChangeRequest request = new DeviceStatusChangeRequest("DISABLED", "停用");

        when(deviceRepository.findById(1L)).thenReturn(Optional.of(testDevice));
        when(deviceRepository.updateStatusIfCurrent(
                eq(1L), eq("AVAILABLE"), eq("DISABLED"), any())).thenReturn(0);

        assertThatThrownBy(() -> deviceService.changeDeviceStatus(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("httpStatus", 409);
    }

    // ============================================================
    // 设备查询
    // ============================================================

    @Test
    @DisplayName("获取设备详情 - 设备不存在时抛出 BusinessException(404)")
    void getDeviceById_shouldThrowWhenNotFound() {
        when(deviceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deviceService.getDeviceById(99L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("httpStatus", 404);
    }

    @Test
    @DisplayName("按状态查询设备")
    void getDevicesByStatus_shouldReturnDevices() {
        when(deviceRepository.findByStatus("AVAILABLE")).thenReturn(Collections.singletonList(testDevice));

        var response = deviceService.getDevicesByStatus("AVAILABLE");

        assertThat(response).hasSize(1);
        assertThat(response.get(0).status()).isEqualTo("AVAILABLE");
    }

    // ============================================================
    // B2：设备档案创建和更新
    // ============================================================

    @Test
    @DisplayName("创建设备 - code 唯一时应创建成功且 status 默认 AVAILABLE")
    void createDevice_shouldSucceedWhenCodeUnique() {
        DeviceCreateRequest request = new DeviceCreateRequest(
                "DEV-NEW-001", "新增监护仪", "MONITOR", 1L,
                "ICU 201", "GE", "CARESCAPE", "SN-NEW-001",
                "新购入", null, null);

        when(deviceRepository.existsByCode("DEV-NEW-001")).thenReturn(false);
        when(departmentRepository.existsById(1L)).thenReturn(true);
        when(deviceRepository.save(any(Device.class))).thenAnswer(inv -> {
            Device d = inv.getArgument(0);
            d.setId(2L);
            return d;
        });

        DeviceResponse response = deviceService.createDevice(request);

        assertThat(response.code()).isEqualTo("DEV-NEW-001");
        assertThat(response.name()).isEqualTo("新增监护仪");
        assertThat(response.type()).isEqualTo("MONITOR");
        assertThat(response.status()).isEqualTo("AVAILABLE");
        verify(deviceRepository).save(any(Device.class));
    }

    @Test
    @DisplayName("创建设备 - code 重复时抛出 BusinessException(409)")
    void createDevice_shouldThrowWhenCodeDuplicated() {
        DeviceCreateRequest request = new DeviceCreateRequest(
                "DEV-MON-001", "重复设备", "MONITOR", null,
                null, null, null, null, null, null, null);

        when(deviceRepository.existsByCode("DEV-MON-001")).thenReturn(true);

        assertThatThrownBy(() -> deviceService.createDevice(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "DEVICE_CODE_DUPLICATED")
                .hasFieldOrPropertyWithValue("httpStatus", 409);
    }

    @Test
    @DisplayName("更新设备 - 应只更新基础档案且不改 status 和 code")
    void updateDevice_shouldUpdateBasicFieldsWithoutStatusOrCode() {
        testDevice.setStatus("IN_USE");
        DeviceUpdateRequest request = new DeviceUpdateRequest(
                "更新监护仪", "MONITOR_V2", 2L, "急诊科 101",
                "Philips", "MX800", "SN-UPD-001", "更新备注",
                java.time.LocalDate.of(2025, 1, 1), null);

        when(deviceRepository.findById(1L)).thenReturn(Optional.of(testDevice));
        when(departmentRepository.existsById(2L)).thenReturn(true);
        when(deviceRepository.save(any(Device.class))).thenAnswer(inv -> inv.getArgument(0));

        DeviceResponse response = deviceService.updateDevice(1L, request);

        assertThat(response.name()).isEqualTo("更新监护仪");
        assertThat(response.type()).isEqualTo("MONITOR_V2");
        assertThat(response.departmentId()).isEqualTo(2L);
        assertThat(response.location()).isEqualTo("急诊科 101");
        // status 和 code 不应被修改
        assertThat(response.status()).isEqualTo("IN_USE");
        assertThat(response.code()).isEqualTo("DEV-MON-001");
    }

    @Test
    @DisplayName("更新设备 - 设备不存在时抛出 BusinessException(404)")
    void updateDevice_shouldThrowWhenNotFound() {
        DeviceUpdateRequest request = new DeviceUpdateRequest(
                "名称", "MONITOR", null, null, null, null, null, null, null, null);

        when(deviceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deviceService.updateDevice(99L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("httpStatus", 404);
    }

    @Test
    @DisplayName("创建设备 - departmentId 不存在时抛出 BusinessException(404)")
    void createDevice_shouldThrowWhenDepartmentNotFound() {
        DeviceCreateRequest request = new DeviceCreateRequest(
                "DEV-NEW-002", "监护仪", "MONITOR", 999L,
                null, null, null, null, null, null, null);

        when(deviceRepository.existsByCode("DEV-NEW-002")).thenReturn(false);
        when(departmentRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> deviceService.createDevice(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "DEVICE_DEPARTMENT_NOT_FOUND")
                .hasFieldOrPropertyWithValue("httpStatus", 404);
    }
}
