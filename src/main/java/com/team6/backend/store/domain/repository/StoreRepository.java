package com.team6.backend.store.domain.repository;

import com.team6.backend.store.domain.entity.Store;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StoreRepository extends JpaRepository<Store, UUID>, StoreRepositoryCustom {
    boolean existsByCategory_CategoryId(UUID categoryId);
}
