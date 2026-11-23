package com.trucare.shared.claim;

import java.util.List;

public record ClaimPageDTO<T>(List<T> content, int page, int size, long totalElements, int totalPages,
                              boolean first, boolean last) { }
