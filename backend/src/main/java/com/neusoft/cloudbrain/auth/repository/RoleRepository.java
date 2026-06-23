package com.neusoft.cloudbrain.auth.repository;

import com.neusoft.cloudbrain.auth.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 角色 Repository
 */
@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    /**
     * 根据角色名称查询角色
     */
    Optional<Role> findByName(String name);
}
