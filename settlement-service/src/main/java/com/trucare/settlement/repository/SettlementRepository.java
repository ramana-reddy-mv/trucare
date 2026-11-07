package com.trucare.settlement.repository;

import com.trucare.settlement.model.Settlement;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SettlementRepository extends JpaRepository<Settlement, Long>,
        JpaSpecificationExecutor<Settlement> {

    Optional<Settlement> findBySettlementNumber(String settlementNumber);

    Optional<Settlement> findByClaimId(String claimId);

    Optional<Settlement> findByIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Settlement s where s.id = :id")
    Optional<Settlement> findByIdForUpdate(@Param("id") Long id);

    Page<Settlement> findAll(Specification<Settlement> specification, Pageable pageable);
}
