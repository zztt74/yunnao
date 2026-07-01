package com.neusoft.cloudbrain.common.api;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * 分页工具
 *
 * 统一新接口的分页参数处理：同时兼容 pageSize 和 size，
 * 内部统一转换。最终主参数名由契约组决定。
 */
public final class PageUtils {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    private PageUtils() {
    }

    /**
     * 解析分页大小，兼容 pageSize 和 size，优先 pageSize。
     */
    public static int resolvePageSize(Integer pageSize, Integer size) {
        int resolved = DEFAULT_PAGE_SIZE;
        if (pageSize != null && pageSize > 0) {
            resolved = pageSize;
        } else if (size != null && size > 0) {
            resolved = size;
        }
        return Math.min(resolved, MAX_PAGE_SIZE);
    }

    /**
     * 将 1-based page 和 pageSize 转为 Spring Data Pageable（0-based）。
     */
    public static Pageable toPageable(int page, int pageSize) {
        return PageRequest.of(Math.max(0, page - 1), pageSize);
    }
}
