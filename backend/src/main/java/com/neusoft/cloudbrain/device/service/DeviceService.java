package com.neusoft.cloudbrain.device.service;

import com.neusoft.cloudbrain.auth.dto.AuthPrincipal;
import com.neusoft.cloudbrain.auth.security.SecurityUtils;
import com.neusoft.cloudbrain.department.repository.DepartmentRepository;
import com.neusoft.cloudbrain.device.dto.DeviceCreateRequest;
import com.neusoft.cloudbrain.device.dto.DeviceEndUsageRequest;
import com.neusoft.cloudbrain.device.dto.DeviceResponse;
import com.neusoft.cloudbrain.device.dto.DeviceStartUsageRequest;
import com.neusoft.cloudbrain.device.dto.DeviceStatusChangeRequest;
import com.neusoft.cloudbrain.device.dto.DeviceStatusHistoryResponse;
import com.neusoft.cloudbrain.device.dto.DeviceUsageResponse;
import com.neusoft.cloudbrain.device.dto.DeviceUpdateRequest;
import com.neusoft.cloudbrain.device.entity.Device;
import com.neusoft.cloudbrain.device.entity.DeviceStatusHistory;
import com.neusoft.cloudbrain.device.entity.DeviceUsage;
import com.neusoft.cloudbrain.device.exception.DeviceErrorCode;
import com.neusoft.cloudbrain.device.repository.DeviceRepository;
import com.neusoft.cloudbrain.device.repository.DeviceStatusHistoryRepository;
import com.neusoft.cloudbrain.device.repository.DeviceUsageRepository;
import com.neusoft.cloudbrain.encounter.entity.Encounter;
import com.neusoft.cloudbrain.encounter.repository.EncounterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 设备 Service
 *
 * 核心职责（来自 11_功能需求.md 第13节 和 12_业务流程与状态机.md 第10节）：
 *
 * 1. 设备状态机（来自 12_业务流程与状态机.md 第11节）：
 *    AVAILABLE → IN_USE        开始使用
 *    IN_USE → AVAILABLE         使用结束
 *    IN_USE → ABNORMAL          发现异常（必须先结束使用记录）
 *    AVAILABLE → ABNORMAL       直接异常（设备自检发现缺陷）
 *    ABNORMAL → MAINTENANCE     送修（扩展版本）
 *    ABNORMAL → AVAILABLE       修复完成
 *    ABNORMAL → DISABLED        报废/永久停用
 *    MAINTENANCE → AVAILABLE    维护完成
 *    MAINTENANCE → DISABLED     维护判定报废
 *    AVAILABLE → DISABLED       停用
 *    DISABLED → AVAILABLE       重新启用
 *
 * 2. 并发控制：
 *    - 使用条件更新（CAS 模式）确保设备只能被一个就诊同时占用
 *    - updateStatusIfCurrent 返回 0 表示并发冲突
 *
 * 3. 状态历史：
 *    - 每次设备状态变更都记录操作人、原因、变更前后状态
 *
 * 关键规则：
 * - 设备异常（ABNORMAL）必须先结束使用记录
 * - 已停用设备不能直接使用，需先重新启用
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final DeviceUsageRepository deviceUsageRepository;
    private final DeviceStatusHistoryRepository deviceStatusHistoryRepository;
    private final EncounterRepository encounterRepository;
    private final DepartmentRepository departmentRepository;

    /**
     * 合法状态转换映射
     * key: 当前状态, value: 允许转换的目标状态集合
     */
    private static final Map<String, Set<String>> VALID_TRANSITIONS = new HashMap<>();

    static {
        VALID_TRANSITIONS.put("AVAILABLE", Set.of("IN_USE", "ABNORMAL", "DISABLED"));
        VALID_TRANSITIONS.put("IN_USE", Set.of("AVAILABLE", "ABNORMAL"));
        VALID_TRANSITIONS.put("ABNORMAL", Set.of("MAINTENANCE", "AVAILABLE", "DISABLED"));
        VALID_TRANSITIONS.put("MAINTENANCE", Set.of("AVAILABLE", "DISABLED"));
        VALID_TRANSITIONS.put("DISABLED", Set.of("AVAILABLE"));
    }

    // ============================================================
    // 设备查询
    // ============================================================

    /**
     * 获取设备详情
     */
    @Transactional(readOnly = true)
    public DeviceResponse getDeviceById(Long id) {
        Device device = deviceRepository.findById(id)
                .orElseThrow(DeviceErrorCode.DEVICE_NOT_FOUND::toException);
        return toDeviceResponse(device);
    }

    /**
     * 按编码获取设备
     */
    @Transactional(readOnly = true)
    public DeviceResponse getDeviceByCode(String code) {
        Device device = deviceRepository.findByCode(code)
                .orElseThrow(DeviceErrorCode.DEVICE_NOT_FOUND::toException);
        return toDeviceResponse(device);
    }

    /**
     * 按科室查询可用设备
     */
    @Transactional(readOnly = true)
    public List<DeviceResponse> getAvailableDevicesByDepartment(Long departmentId) {
        return deviceRepository.findByDepartmentIdAndStatus(departmentId, "AVAILABLE").stream()
                .map(this::toDeviceResponse)
                .collect(Collectors.toList());
    }

    /**
     * 按状态查询设备
     */
    @Transactional(readOnly = true)
    public List<DeviceResponse> getDevicesByStatus(String status) {
        return deviceRepository.findByStatus(status).stream()
                .map(this::toDeviceResponse)
                .collect(Collectors.toList());
    }

    /**
     * 搜索设备（关键字 + 状态 + 类型 + 科室）
     */
    @Transactional(readOnly = true)
    public Page<DeviceResponse> searchDevices(String keyword, String status, String type,
                                              Long departmentId, Pageable pageable) {
        return deviceRepository.searchDevices(keyword, status, type, departmentId, pageable)
                .map(this::toDeviceResponse);
    }

    // ============================================================
    // 设备档案：创建和更新
    // ============================================================

    /**
     * 创建设备档案
     *
     * - code 必须唯一
     * - 创建后 status 默认 AVAILABLE，不在此接口设置 status
     */
    @Transactional
    public DeviceResponse createDevice(DeviceCreateRequest request) {
        if (deviceRepository.existsByCode(request.code())) {
            throw DeviceErrorCode.DEVICE_CODE_DUPLICATED.toException();
        }
        validateDepartment(request.departmentId());

        LocalDateTime now = LocalDateTime.now();
        Device device = Device.builder()
                .code(request.code())
                .name(request.name())
                .type(request.type())
                .departmentId(request.departmentId())
                .status("AVAILABLE")
                .location(request.location())
                .manufacturer(request.manufacturer())
                .model(request.model())
                .serialNumber(request.serialNumber())
                .notes(request.notes())
                .purchaseDate(request.purchaseDate())
                .warrantyUntil(request.warrantyUntil())
                .createdAt(now)
                .updatedAt(now)
                .build();
        try {
            device = deviceRepository.save(device);
        } catch (DataIntegrityViolationException e) {
            // 并发下 code 唯一约束兜底（existsByCode 预检查与 save 之间存在竞态）
            throw DeviceErrorCode.DEVICE_CODE_DUPLICATED.toException();
        }

        log.info("创建设备档案: id={}, code={}, name={}", device.getId(), device.getCode(), device.getName());
        return toDeviceResponse(device);
    }

    /**
     * 更新设备档案基础信息
     *
     * - 只更新基础档案字段，不修改 status（状态变更走 changeDeviceStatus）
     * - 不修改 code（业务唯一标识，避免破坏历史关联）
     * - IN_USE 设备也可更新基础信息（不破坏占用状态）
     */
    @Transactional
    public DeviceResponse updateDevice(Long id, DeviceUpdateRequest request) {
        Device device = deviceRepository.findById(id)
                .orElseThrow(DeviceErrorCode.DEVICE_NOT_FOUND::toException);
        validateDepartment(request.departmentId());

        device.setName(request.name());
        device.setType(request.type());
        device.setDepartmentId(request.departmentId());
        device.setLocation(request.location());
        device.setManufacturer(request.manufacturer());
        device.setModel(request.model());
        device.setSerialNumber(request.serialNumber());
        device.setNotes(request.notes());
        device.setPurchaseDate(request.purchaseDate());
        device.setWarrantyUntil(request.warrantyUntil());
        device.setUpdatedAt(LocalDateTime.now());
        device = deviceRepository.save(device);

        log.info("更新设备档案: id={}, code={}, name={}", device.getId(), device.getCode(), device.getName());
        return toDeviceResponse(device);
    }

    // ============================================================
    // 设备使用：开始使用 AVAILABLE → IN_USE
    // ============================================================

    /**
     * 开始使用设备
     *
     * 业务流程（来自 11_功能需求.md 第13.4节）：
     * 1. 校验设备存在
     * 2. 状态校验：必须为 AVAILABLE
     * 3. 并发控制：条件更新 AVAILABLE → IN_USE
     *    - updateStatusIfCurrent 返回 0 表示并发冲突
     * 4. 创建使用记录
     * 5. 记录状态历史
     */
    @Transactional
    public DeviceUsageResponse startUsage(DeviceStartUsageRequest request) {
        // 1. 校验设备存在
        Device device = deviceRepository.findById(request.deviceId())
                .orElseThrow(DeviceErrorCode.DEVICE_NOT_FOUND::toException);

        // 2. 状态校验
        if (!"AVAILABLE".equals(device.getStatus())) {
            throw statusConflictException(device.getStatus());
        }

        // 3. 校验就诊存在
        encounterRepository.findById(request.encounterId())
                .orElseThrow(() -> new com.neusoft.cloudbrain.common.exception.BusinessException(
                        "ENCOUNTER_NOT_FOUND", "就诊不存在", 404));

        // 4. 并发控制：条件更新（CAS 模式）
        LocalDateTime now = LocalDateTime.now();
        int updated = deviceRepository.updateStatusIfCurrent(
                request.deviceId(), "AVAILABLE", "IN_USE", now);
        if (updated == 0) {
            // 并发冲突：设备已被其他人占用
            throw DeviceErrorCode.DEVICE_CONCURRENT_CONFLICT.toException();
        }

        // 5. 创建使用记录
        Long currentUserId = getCurrentUserId();
        DeviceUsage usage = DeviceUsage.builder()
                .deviceId(request.deviceId())
                .encounterId(request.encounterId())
                .usedBy(currentUserId)
                .startTime(now)
                .status("IN_USAGE")
                .notes(request.notes())
                .createdAt(now)
                .updatedAt(now)
                .build();
        usage = deviceUsageRepository.save(usage);

        // 6. 记录状态历史
        saveStatusHistory(request.deviceId(), "AVAILABLE", "IN_USE", currentUserId,
                "开始使用，就诊 ID：" + request.encounterId());

        log.info("设备开始使用: deviceId={}, usageId={}, encounterId={}, usedBy={}",
                request.deviceId(), usage.getId(), request.encounterId(), currentUserId);

        return toUsageResponse(usage);
    }

    // ============================================================
    // 设备使用：结束使用 IN_USE → AVAILABLE
    // ============================================================

    /**
     * 结束设备使用
     *
     * 业务流程：
     * 1. 查找设备当前进行中的使用记录
     * 2. 更新使用记录状态 IN_USAGE → COMPLETED
     * 3. 更新设备状态 IN_USE → AVAILABLE（CAS）
     * 4. 记录状态历史
     */
    @Transactional
    public DeviceUsageResponse endUsage(Long deviceId, DeviceEndUsageRequest request) {
        // 1. 校验设备存在
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(DeviceErrorCode.DEVICE_NOT_FOUND::toException);

        // 2. 查找当前进行中的使用记录
        DeviceUsage usage = deviceUsageRepository
                .findFirstByDeviceIdAndStatusOrderByStartTimeDesc(deviceId, "IN_USAGE")
                .orElseThrow(DeviceErrorCode.DEVICE_NO_ACTIVE_USAGE::toException);

        // 3. 更新使用记录状态 IN_USAGE → COMPLETED
        LocalDateTime now = LocalDateTime.now();
        int usageUpdated = deviceUsageRepository.updateStatusIfCurrent(
                usage.getId(), "IN_USAGE", "COMPLETED", now, now);
        if (usageUpdated == 0) {
            throw DeviceErrorCode.DEVICE_USAGE_ALREADY_COMPLETED.toException();
        }

        // 4. 更新设备状态 IN_USE → AVAILABLE（CAS）
        int deviceUpdated = deviceRepository.updateStatusIfCurrent(
                deviceId, "IN_USE", "AVAILABLE", now);
        if (deviceUpdated == 0) {
            // 设备状态已被其他事务修改（例如已上报异常）
            log.warn("结束使用时设备状态冲突: deviceId={}, currentStatus={}",
                    deviceId, device.getStatus());
            throw DeviceErrorCode.DEVICE_STATUS_CONFLICT.toException();
        }

        // 5. 记录状态历史
        Long currentUserId = getCurrentUserId();
        saveStatusHistory(deviceId, "IN_USE", "AVAILABLE", currentUserId,
                "使用结束" + (request.notes() != null ? "，备注：" + request.notes() : ""));

        usage.setStatus("COMPLETED");
        usage.setEndTime(now);

        log.info("设备结束使用: deviceId={}, usageId={}", deviceId, usage.getId());

        return toUsageResponse(usage);
    }

    // ============================================================
    // 设备状态变更：异常上报、送修、修复、停用、启用
    // ============================================================

    /**
     * 设备状态变更
     *
     * 状态转换规则：
     * AVAILABLE → IN_USE        开始使用（通过 startUsage）
     * IN_USE → AVAILABLE         使用结束（通过 endUsage）
     * IN_USE → ABNORMAL          发现异常（必须先结束使用记录）
     * ABNORMAL → MAINTENANCE     送修
     * ABNORMAL → AVAILABLE       修复完成
     * AVAILABLE → DISABLED       停用
     * DISABLED → AVAILABLE       重新启用
     */
    @Transactional
    public DeviceResponse changeDeviceStatus(Long deviceId, DeviceStatusChangeRequest request) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(DeviceErrorCode.DEVICE_NOT_FOUND::toException);

        String currentStatus = device.getStatus();
        String targetStatus = request.targetStatus();

        // 校验状态转换合法性
        validateStatusTransition(currentStatus, targetStatus);

        // 特殊规则：IN_USE → ABNORMAL 必须先结束使用记录
        if ("IN_USE".equals(currentStatus) && "ABNORMAL".equals(targetStatus)) {
            DeviceUsage activeUsage = deviceUsageRepository
                    .findFirstByDeviceIdAndStatusOrderByStartTimeDesc(deviceId, "IN_USAGE")
                    .orElse(null);
            if (activeUsage != null) {
                // 先结束使用记录（异常中止）
                LocalDateTime now = LocalDateTime.now();
                deviceUsageRepository.updateStatusIfCurrent(
                        activeUsage.getId(), "IN_USAGE", "ABORTED", now, now);
                log.info("设备异常上报，自动结束使用记录: deviceId={}, usageId={}",
                        deviceId, activeUsage.getId());
            }
        }

        // CAS 更新设备状态
        LocalDateTime now = LocalDateTime.now();
        int updated = deviceRepository.updateStatusIfCurrent(
                deviceId, currentStatus, targetStatus, now);
        if (updated == 0) {
            throw DeviceErrorCode.DEVICE_CONCURRENT_CONFLICT.toException();
        }

        // 记录状态历史
        Long currentUserId = getCurrentUserId();
        saveStatusHistory(deviceId, currentStatus, targetStatus, currentUserId, request.reason());

        device.setStatus(targetStatus);
        device.setUpdatedAt(now);

        log.info("设备状态变更: deviceId={}, {} → {}, reason={}",
                deviceId, currentStatus, targetStatus, request.reason());

        return toDeviceResponse(device);
    }

    // ============================================================
    // 设备使用记录查询
    // ============================================================

    /**
     * 查询设备使用记录
     */
    @Transactional(readOnly = true)
    public List<DeviceUsageResponse> getDeviceUsageHistory(Long deviceId) {
        return deviceUsageRepository.findByDeviceIdOrderByStartTimeDesc(deviceId).stream()
                .map(this::toUsageResponse)
                .collect(Collectors.toList());
    }

    /**
     * 查询就诊的设备使用记录
     */
    @Transactional(readOnly = true)
    public List<DeviceUsageResponse> getDeviceUsageByEncounter(Long encounterId) {
        return deviceUsageRepository.findByEncounterId(encounterId).stream()
                .map(this::toUsageResponse)
                .collect(Collectors.toList());
    }

    /**
     * 查询操作人的设备使用记录（分页）
     */
    @Transactional(readOnly = true)
    public Page<DeviceUsageResponse> getDeviceUsageByUser(Long userId, Pageable pageable) {
        return deviceUsageRepository.findByUsedBy(userId, pageable)
                .map(this::toUsageResponse);
    }

    // ============================================================
    // 设备状态历史查询
    // ============================================================

    /**
     * 查询设备状态变更历史
     */
    @Transactional(readOnly = true)
    public List<DeviceStatusHistoryResponse> getDeviceStatusHistory(Long deviceId) {
        return deviceStatusHistoryRepository.findByDeviceIdOrderByChangedAtDesc(deviceId).stream()
                .map(this::toHistoryResponse)
                .collect(Collectors.toList());
    }

    // ============================================================
    // 辅助方法
    // ============================================================

    /**
     * 校验科室存在性（departmentId 为空时跳过，允许设备暂不归属科室）
     */
    private void validateDepartment(Long departmentId) {
        if (departmentId != null && !departmentRepository.existsById(departmentId)) {
            throw DeviceErrorCode.DEVICE_DEPARTMENT_NOT_FOUND.toException();
        }
    }

    /**
     * 校验状态转换合法性
     */
    private void validateStatusTransition(String currentStatus, String targetStatus) {
        Set<String> allowedTargets = VALID_TRANSITIONS.get(currentStatus);
        if (allowedTargets == null || !allowedTargets.contains(targetStatus)) {
            log.warn("非法状态转换: {} → {}", currentStatus, targetStatus);
            throw DeviceErrorCode.DEVICE_INVALID_STATUS_TRANSITION.toException();
        }
    }

    /**
     * 根据当前状态生成具体的冲突异常
     */
    private com.neusoft.cloudbrain.common.exception.BusinessException statusConflictException(String currentStatus) {
        return switch (currentStatus) {
            case "IN_USE" -> DeviceErrorCode.DEVICE_IN_USE.toException();
            case "ABNORMAL" -> DeviceErrorCode.DEVICE_ABNORMAL.toException();
            case "MAINTENANCE" -> DeviceErrorCode.DEVICE_MAINTENANCE.toException();
            case "DISABLED" -> DeviceErrorCode.DEVICE_DISABLED.toException();
            default -> DeviceErrorCode.DEVICE_NOT_AVAILABLE.toException();
        };
    }

    /**
     * 保存状态变更历史
     */
    private void saveStatusHistory(Long deviceId, String fromStatus, String toStatus,
                                   Long operatorId, String reason) {
        DeviceStatusHistory history = DeviceStatusHistory.builder()
                .deviceId(deviceId)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .operatorId(operatorId)
                .reason(reason)
                .changedAt(LocalDateTime.now())
                .build();
        deviceStatusHistoryRepository.save(history);
    }

    private Long getCurrentUserId() {
        if (!SecurityUtils.isAuthenticated()) {
            return null;
        }
        AuthPrincipal currentUser = SecurityUtils.getCurrentUser();
        return currentUser.userId();
    }

    // ============================================================
    // 响应转换
    // ============================================================

    private DeviceResponse toDeviceResponse(Device device) {
        return new DeviceResponse(
                device.getId(),
                device.getCode(),
                device.getName(),
                device.getType(),
                device.getDepartmentId(),
                device.getStatus(),
                device.getPurchaseDate(),
                device.getWarrantyUntil(),
                device.getLastMaintenance(),
                device.getLocation(),
                device.getManufacturer(),
                device.getModel(),
                device.getSerialNumber(),
                device.getNotes(),
                device.getCreatedAt(),
                device.getUpdatedAt());
    }

    private DeviceUsageResponse toUsageResponse(DeviceUsage usage) {
        return new DeviceUsageResponse(
                usage.getId(),
                usage.getDeviceId(),
                usage.getEncounterId(),
                usage.getUsedBy(),
                usage.getStartTime(),
                usage.getEndTime(),
                usage.getStatus(),
                usage.getNotes(),
                usage.getCreatedAt(),
                usage.getUpdatedAt());
    }

    private DeviceStatusHistoryResponse toHistoryResponse(DeviceStatusHistory history) {
        return new DeviceStatusHistoryResponse(
                history.getId(),
                history.getDeviceId(),
                history.getFromStatus(),
                history.getToStatus(),
                history.getOperatorId(),
                history.getReason(),
                history.getChangedAt());
    }
}
