package com.team6.backend.address.domain.repository;

import com.team6.backend.address.domain.entity.Address;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AddressRepository extends JpaRepository<Address, UUID> {

    Page<Address> findByUserId(UUID userId, Pageable pageable);

    Page<Address> findByUserIdAndAliasContainingIgnoreCase(UUID id, String alias, Pageable pageable);

    Optional<Address> findByAdIdAndUser_Id(UUID id, UUID userId);

    Optional<Address> findByUserIdAndIsDefaultTrue(UUID userId);
}
