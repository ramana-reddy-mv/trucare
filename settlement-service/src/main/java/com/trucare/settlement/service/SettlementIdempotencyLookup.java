package com.trucare.settlement.service;

import com.trucare.settlement.dto.SettlementResponse;
import com.trucare.settlement.mapper.SettlementMapper;
import com.trucare.settlement.repository.SettlementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class SettlementIdempotencyLookup {

    private final SettlementRepository settlementRepository;
    private final SettlementMapper mapper;

    public SettlementIdempotencyLookup(SettlementRepository settlementRepository, SettlementMapper mapper) {
        this.settlementRepository = settlementRepository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public Optional<ExistingSettlement> find(String idempotencyKey) {
        return settlementRepository.findByIdempotencyKey(idempotencyKey)
                .map(settlement -> new ExistingSettlement(settlement.getRequestHash(), mapper.toResponse(settlement)));
    }

    public record ExistingSettlement(String requestHash, SettlementResponse response) {
    }
}
