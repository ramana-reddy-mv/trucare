package com.trucare.settlement.repository;

import com.trucare.settlement.model.SettlementAudit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettlementAuditRepository extends JpaRepository<SettlementAudit, Long> {
}
