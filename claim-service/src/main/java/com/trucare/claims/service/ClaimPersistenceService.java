package com.trucare.claims.service;

import com.trucare.claims.model.Claim;
import com.trucare.claims.repository.ClaimRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClaimPersistenceService {
    private final ClaimRepository claimRepository;

    public ClaimPersistenceService(ClaimRepository claimRepository) {
        this.claimRepository = claimRepository;
    }

    @Transactional
    public Claim create(Claim claim) {
        return claimRepository.saveAndFlush(claim);
    }
}
