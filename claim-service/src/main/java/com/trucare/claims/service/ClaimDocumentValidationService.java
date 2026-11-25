package com.trucare.claims.service;

import com.trucare.claims.exception.InvalidClaimTransitionException;
import com.trucare.shared.client.DocumentClient;
import feign.FeignException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ClaimDocumentValidationService {
    private final DocumentClient documentClient;

    public ClaimDocumentValidationService(DocumentClient documentClient) {
        this.documentClient = documentClient;
    }

    public void requireUploadedDocuments(List<Long> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            throw new InvalidClaimTransitionException(
                    "Claim status transition from PENDING_DOCUMENTS to SUBMITTED requires uploaded documents");
        }
        try {
            List<Long> missing = documentClient.validateDocuments(documentIds);
            if (missing == null || !missing.isEmpty()) {
                throw new InvalidClaimTransitionException(
                        "Claim status transition from PENDING_DOCUMENTS to SUBMITTED requires valid documents");
            }
        } catch (FeignException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Uploaded documents could not be verified; retry the status change");
        }
    }
}
