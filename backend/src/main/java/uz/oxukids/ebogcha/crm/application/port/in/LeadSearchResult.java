package uz.oxukids.ebogcha.crm.application.port.in;

import java.util.List;
import java.util.Objects;

public record LeadSearchResult(
        List<LeadListItem> items,
        int page,
        int size,
        long totalElements,
        long totalPages,
        boolean hasPrevious,
        boolean hasNext
) {
    public LeadSearchResult {
        items = List.copyOf(Objects.requireNonNull(items, "items must not be null"));
    }
}
