package com.trucare.claims.repository;

import com.trucare.claims.model.Claim;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.trucare.claims.model.ClaimStatus;

import java.util.Optional;

@Repository
public interface ClaimRepository extends JpaRepository<Claim, Long> {
    // Additional query methods can be added as needed

    Optional<Claim> findByClaimId(String claimId);

    Optional<Claim> findByIdempotencyKey(String idempotencyKey);

    @org.springframework.data.jpa.repository.Query("select c from Claim c where (:status is null or c.claimStatus = :status) and (:memberId is null or c.memberId = :memberId) and (:providerId is null or c.providerId = :providerId) and (:fromDate is null or c.createdAt >= :fromDate) and (:toDate is null or c.createdAt < :toDate) and (:claimId is null or lower(c.claimId) like lower(concat('%', :claimId, '%')))")
    Page<Claim> search(ClaimStatus status, String memberId, String providerId,
                       java.time.LocalDateTime fromDate, java.time.LocalDateTime toDate,
                       String claimId, Pageable pageable);

    @org.springframework.data.jpa.repository.Query("select c.claimStatus, count(c) from Claim c group by c.claimStatus")
    java.util.List<Object[]> countByStatus();

    long countByCreatedAtGreaterThanEqual(java.time.LocalDateTime start);
}
