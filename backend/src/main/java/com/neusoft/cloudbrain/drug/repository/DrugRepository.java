package com.neusoft.cloudbrain.drug.repository;

import com.neusoft.cloudbrain.drug.entity.Drug;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 药品 Repository
 */
@Repository
public interface DrugRepository extends JpaRepository<Drug, Long> {

    Optional<Drug> findByCode(String code);

    List<Drug> findByCategory(String category);

    List<Drug> findByStatus(String status);

    Page<Drug> findByNameContaining(String name, Pageable pageable);

    boolean existsByCode(String code);
}
