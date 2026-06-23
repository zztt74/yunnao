package com.neusoft.cloudbrain.drug.repository;

import com.neusoft.cloudbrain.drug.entity.DrugIngredient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 药品成分 Repository
 */
@Repository
public interface DrugIngredientRepository extends JpaRepository<DrugIngredient, Long> {

    List<DrugIngredient> findByDrugId(Long drugId);
}
