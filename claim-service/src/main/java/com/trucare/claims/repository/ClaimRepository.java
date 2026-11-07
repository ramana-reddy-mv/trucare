package com.trucare.claims.repository;

import com.trucare.claims.model.Claim;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClaimRepository extends JpaRepository<Claim, Long> {
    // Additional query methods can be added as needed

    Optional<Claim> findByClaimId(String claimId);

    Optional<Claim> findByIdempotencyKey(String idempotencyKey);
}
