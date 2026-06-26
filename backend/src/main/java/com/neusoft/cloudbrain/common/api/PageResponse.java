package com.neusoft.cloudbrain.common.api;

import org.springframework.data.domain.Page;

import java.util.List;

public record PageResponse<T>(
        List<T> items,
        int page,
        int pageSize,
        long total,
        int totalPages) {

    public PageResponse {
        items = List.copyOf(items);
        if (page < 1) {
            throw new IllegalArgumentException("page must start from 1");
        }
        if (pageSize < 1) {
            throw new IllegalArgumentException("pageSize must be positive");
        }
        if (total < 0 || totalPages < 0) {
            throw new IllegalArgumentException("pagination totals cannot be negative");
        }
    }

    /**
     * 从 Spring Data Page 构造 PageResponse（items 已映射为 DTO）。
     * Page 的 0-based 页号会自动转换为 1-based 对外页号。
     */
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber() + 1,
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
