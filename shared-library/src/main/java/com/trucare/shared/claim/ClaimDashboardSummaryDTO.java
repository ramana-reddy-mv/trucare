package com.trucare.shared.claim;

import java.util.List;

public record ClaimDashboardSummaryDTO(long totalClaims, long pendingClaims, long approvedClaims,
                                       long rejectedClaims, long claimsThisMonth,
                                       List<StatusCount> statusDistribution) {
    public record StatusCount(String status, long count) { }
}
