package uz.oxukids.ebogcha.crm.application.port.out;

import uz.oxukids.ebogcha.crm.application.port.in.LeadListItem;

import java.util.List;
import java.util.Objects;

public record LeadQueryResult(List<LeadListItem> items, long totalElements) {

    public LeadQueryResult {
        items = List.copyOf(Objects.requireNonNull(items, "items must not be null"));
        if (totalElements < 0) {
            throw new IllegalArgumentException("totalElements must not be negative");
        }
    }
}
