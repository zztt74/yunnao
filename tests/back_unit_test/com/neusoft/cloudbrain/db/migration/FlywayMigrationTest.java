package com.neusoft.cloudbrain.db.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flyway 迁移脚本完整性测试
 *
 * 设计说明：
 *   本测试不通过 H2 执行迁移脚本，因为：
 *   1. 目标生产数据库是 MySQL，迁移脚本包含 MySQL 特定语法
 *      （如 V021 的函数索引 CASE WHEN、ENGINE=InnoDB、COLLATE=utf8mb4_0900_ai_ci）
 *   2. 用 H2 验证 MySQL 脚本会产生大量误报，无法反映真实情况
 *   3. Testcontainers MySQL 需要本地 Docker 环境，不适合 CI 轻量验证
 *
 *   本测试改为验证迁移脚本的：
 *   - 文件存在性（V001-V071 全部存在）
 *   - 版本号连续性
 *   - V071 修正脚本内容正确（ON DELETE RESTRICT）
 *   - V070 原始外键约束内容（CASCADE，待 V071 修正）
 *
 *   完整的 MySQL 端到端迁移验证应在部署前使用真实 MySQL 或
 *   Testcontainers 进行（参见 41_质量测试与完成定义.md 11.3）。
 */
@DisplayName("Flyway 迁移脚本完整性测试")
class FlywayMigrationTest {

    private static final String MIGRATION_DIR = "src/main/resources/db/migration";

    @Test
    @DisplayName("所有必需的迁移脚本文件应存在")
    void allMigrationScriptsShouldExist() {
        List<String> expectedVersions = List.of(
                "001", "010", "020", "021", "030", "040", "050", "060", "070", "071");
        List<String> actualFiles = listMigrationFiles();

        for (String version : expectedVersions) {
            assertThat(actualFiles)
                    .as("迁移脚本 V%s__*.sql 应存在", version)
                    .anyMatch(f -> f.startsWith("V" + version + "__"));
        }
    }

    @Test
    @DisplayName("迁移脚本版本号应单调递增且无重复")
    void migrationVersionsShouldBeMonotonicAndUnique() {
        List<String> files = listMigrationFiles();
        assertThat(files).isNotEmpty();

        List<Integer> versions = files.stream()
                .map(f -> f.replaceFirst("^V(\\d+)__.*$", "$1"))
                .map(Integer::parseInt)
                .sorted()
                .toList();

        // 无重复
        assertThat(versions).doesNotHaveDuplicates();
        // 单调递增
        for (int i = 1; i < versions.size(); i++) {
            assertThat(versions.get(i))
                    .as("版本号应单调递增")
                    .isGreaterThan(versions.get(i - 1));
        }
    }

    @Test
    @DisplayName("V070 应包含 CASCADE 外键约束（待 V071 修正）")
    void v070ShouldContainCascadeFk() throws IOException {
        String v070 = readMigration("070");
        assertThat(v070)
                .as("V070 原始约束应为 ON DELETE CASCADE")
                .contains("ON DELETE CASCADE");
    }

    @Test
    @DisplayName("V071 应将外键改为 ON DELETE RESTRICT")
    void v071ShouldChangeFkToRestrict() throws IOException {
        String v071 = readMigration("071");
        assertThat(v071)
                .as("V071 应 DROP 旧约束")
                .containsIgnoringCase("DROP FOREIGN KEY")
                .containsIgnoringCase("fk_ai_attempt_invocation");
        assertThat(v071)
                .as("V071 应创建 ON DELETE RESTRICT 约束")
                .containsIgnoringCase("ADD CONSTRAINT")
                .containsIgnoringCase("fk_ai_attempt_invocation")
                .containsIgnoringCase("ON DELETE RESTRICT");
    }

    @Test
    @DisplayName("V071 不应修改已发布迁移脚本，应为新增脚本")
    void v071ShouldBeNewScriptNotModifyV070() {
        List<String> files = listMigrationFiles();
        long v070Count = files.stream().filter(f -> f.startsWith("V070__")).count();
        long v071Count = files.stream().filter(f -> f.startsWith("V071__")).count();
        assertThat(v070Count).as("V070 应保留原样").isEqualTo(1);
        assertThat(v071Count).as("V071 应为新增独立脚本").isEqualTo(1);
    }

    private List<String> listMigrationFiles() {
        try {
            Path dir = Paths.get(MIGRATION_DIR);
            if (!Files.isDirectory(dir)) {
                dir = Paths.get("backend/" + MIGRATION_DIR);
            }
            return Files.list(dir)
                    .map(p -> p.getFileName().toString())
                    .filter(f -> f.endsWith(".sql"))
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new IllegalStateException("无法读取迁移脚本目录", e);
        }
    }

    private String readMigration(String version) throws IOException {
        Path dir = Paths.get(MIGRATION_DIR);
        if (!Files.isDirectory(dir)) {
            dir = Paths.get("backend/" + MIGRATION_DIR);
        }
        Path script = Files.list(dir)
                .filter(p -> p.getFileName().toString().startsWith("V" + version + "__"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("找不到 V" + version + " 迁移脚本"));
        return Files.readString(script);
    }
}
