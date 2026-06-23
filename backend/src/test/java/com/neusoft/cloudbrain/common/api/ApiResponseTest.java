package com.neusoft.cloudbrain.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApiResponseTest {

    @Test
    void successUsesTheFrozenResponseEnvelope() {
        ApiResponse<String> response = ApiResponse.success("ready", "trace-001");

        assertThat(response.code()).isEqualTo("SUCCESS");
        assertThat(response.message()).isEqualTo("操作成功");
        assertThat(response.data()).isEqualTo("ready");
        assertThat(response.traceId()).isEqualTo("trace-001");
    }
}
