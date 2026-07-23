package uz.oxukids.ebogcha.crm.infrastructure.web;

import java.util.List;

public record LeadSearchResponse(
        List<LeadListItemResponse> items,
        int page,
        int size,
        long totalElements,
        long totalPages,
        boolean hasPrevious,
        boolean hasNext
) {
}
