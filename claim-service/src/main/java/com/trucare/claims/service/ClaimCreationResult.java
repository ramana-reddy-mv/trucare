package com.trucare.claims.service;

import com.trucare.shared.claim.ClaimDTO;

public record ClaimCreationResult(ClaimDTO claim, boolean replayed) {
}
