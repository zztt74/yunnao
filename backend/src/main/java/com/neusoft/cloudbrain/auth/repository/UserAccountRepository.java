package com.neusoft.cloudbrain.auth.repository;

import com.neusoft.cloudbrain.auth.entity.UserAccount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 用户账号 Repository
 */
@Repository
public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByUsername(String username);

    boolean existsByUsername(String username);

    /**
     * 管理端用户分页查询（多条件，B3）
     *
     * @param role    角色名筛选（可空，按角色名精确匹配）
     * @param enabled 启用状态筛选（可空）
     * @param keyword 用户名模糊关键字（可空，因 user_account 无姓名字段，按 username 模糊）
     */
    @Query("SELECT DISTINCT u FROM UserAccount u LEFT JOIN u.roles r WHERE " +
            "(:role IS NULL OR r.name = :role) " +
            "AND (:enabled IS NULL OR u.enabled = :enabled) " +
            "AND (:keyword IS NULL OR LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<UserAccount> searchUsers(
            @Param("role") String role,
            @Param("enabled") Boolean enabled,
            @Param("keyword") String keyword,
            Pageable pageable);
}
