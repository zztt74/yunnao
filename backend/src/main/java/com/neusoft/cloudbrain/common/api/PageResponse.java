package com.neusoft.cloudbrain.common.api;

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
}
