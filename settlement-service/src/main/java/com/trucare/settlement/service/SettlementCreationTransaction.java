package com.trucare.settlement.service;

import com.trucare.settlement.dto.SettlementResponse;
import com.trucare.settlement.mapper.SettlementMapper;
import com.trucare.settlement.model.Settlement;
import com.trucare.settlement.model.SettlementAudit;
import com.trucare.settlement.model.SettlementStatus;
import com.trucare.settlement.repository.SettlementAuditRepository;
import com.trucare.settlement.repository.SettlementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettlementCreationTransaction {

    private final SettlementRepository settlementRepository;
    private final SettlementAuditRepository auditRepository;
    private final SettlementMapper mapper;

    public SettlementCreationTransaction(SettlementRepository settlementRepository,
                                         SettlementAuditRepository auditRepository,
                                         SettlementMapper mapper) {
        this.settlementRepository = settlementRepository;
        this.auditRepository = auditRepository;
        this.mapper = mapper;
    }

    @Transactional
    public SettlementResponse persist(Settlement settlement, String actor) {
        Settlement saved = settlementRepository.saveAndFlush(settlement);
        SettlementAudit audit = new SettlementAudit();
        audit.setSettlement(saved);
        audit.setPreviousStatus(null);
        audit.setNewStatus(SettlementStatus.PENDING);
        audit.setAction("CREATED");
        audit.setRemarks("Settlement created for approved claim");
        audit.setPerformedBy(actor);
        saved.getAuditHistory().add(audit);
        auditRepository.saveAndFlush(audit);
        return mapper.toResponse(saved);
    }
}
