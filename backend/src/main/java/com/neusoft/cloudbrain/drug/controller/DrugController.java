package com.neusoft.cloudbrain.drug.controller;

import com.neusoft.cloudbrain.common.api.ApiResponse;
import com.neusoft.cloudbrain.drug.dto.DrugInteractionResponse;
import com.neusoft.cloudbrain.drug.dto.DrugResponse;
import com.neusoft.cloudbrain.drug.service.DrugService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 药品接口
 *
 * - GET  /api/drugs                药品列表（分页）
 * - GET  /api/drugs/{id}           药品详情
 * - GET  /api/drugs/by-code/{code} 按编码查询
 * - GET  /api/drugs/by-category    按分类查询
 * - GET  /api/drugs/interaction    查询相互作用
 * - GET  /api/drugs/{code}/contraindications  查询禁忌
 */
@RestController
@RequestMapping("/api/drugs")
public class DrugController {

    private final DrugService drugService;

    public DrugController(DrugService drugService) {
        this.drugService = drugService;
    }

    /**
     * 获取药品列表（分页）
     */
    @GetMapping
    public ApiResponse<Page<DrugResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String name,
            HttpServletRequest httpRequest) {
        Page<DrugResponse> result;
        if (name != null && !name.isBlank()) {
            result = drugService.searchByName(name, page, pageSize);
        } else {
            result = drugService.getDrugList(page, pageSize);
        }
        return ApiResponse.success(result, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 获取药品详情
     */
    @GetMapping("/{id}")
    public ApiResponse<DrugResponse> getById(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        DrugResponse response = drugService.getDrugById(id);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 按编码查询药品
     */
    @GetMapping("/by-code/{code}")
    public ApiResponse<DrugResponse> getByCode(
            @PathVariable String code,
            HttpServletRequest httpRequest) {
        DrugResponse response = drugService.getDrugByCode(code);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 按分类查询药品
     */
    @GetMapping("/by-category")
    public ApiResponse<List<DrugResponse>> getByCategory(
            @RequestParam String category,
            HttpServletRequest httpRequest) {
        List<DrugResponse> response = drugService.getByCategory(category);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 查询药品相互作用
     */
    @GetMapping("/interaction")
    public ApiResponse<List<DrugInteractionResponse>> getInteraction(
            @RequestParam(required = false) String drugACode,
            @RequestParam(required = false) String drugBCode,
            @RequestParam(required = false) String drugCode,
            HttpServletRequest httpRequest) {
        List<DrugInteractionResponse> result;
        if (drugCode != null && !drugCode.isBlank()) {
            result = drugService.getInteractionsByDrugCode(drugCode);
        } else if (drugACode != null && drugBCode != null) {
            result = drugService.getInteraction(drugACode, drugBCode);
        } else {
            result = List.of();
        }
        return ApiResponse.success(result, (String) httpRequest.getAttribute("traceId"));
    }

    /**
     * 查询药品禁忌
     */
    @GetMapping("/{code}/contraindications")
    public ApiResponse<List<DrugResponse.ContraindicationResponse>> getContraindications(
            @PathVariable String code,
            @RequestParam(required = false) String conditionType,
            HttpServletRequest httpRequest) {
        List<DrugResponse.ContraindicationResponse> response =
                drugService.getContraindications(code, conditionType);
        return ApiResponse.success(response, (String) httpRequest.getAttribute("traceId"));
    }
}
