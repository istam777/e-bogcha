package uz.oxukids.ebogcha.crm.application.port.in;

import uz.oxukids.ebogcha.crm.application.exception.InvalidLeadSearchQueryException;
import uz.oxukids.ebogcha.crm.domain.model.LeadSource;
import uz.oxukids.ebogcha.crm.domain.model.LeadStatus;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record SearchLeadsQuery(
        UUID actorUserId,
        String queryText,
        UUID branchId,
        LeadStatus status,
        LeadSource source,
        UUID ownerOperatorId,
        OwnerState ownerState,
        boolean overdueOnly,
        Instant createdFrom,
        Instant createdTo,
        int page,
        int size
) {
    public SearchLeadsQuery {
        Objects.requireNonNull(actorUserId, "actorUserId must not be null");
        ownerState = ownerState == null ? OwnerState.ALL : ownerState;
        queryText = normalizeQueryText(queryText);
        if (ownerOperatorId != null && ownerState == OwnerState.UNASSIGNED) {
            throw new InvalidLeadSearchQueryException(
                    "ownerOperatorId cannot be combined with ownerState UNASSIGNED"
            );
        }
        if (createdFrom != null && createdTo != null && createdFrom.isAfter(createdTo)) {
            throw new InvalidLeadSearchQueryException(
                    "createdFrom must not be after createdTo"
            );
        }
        if (page < 0) {
            throw new InvalidLeadSearchQueryException("page must be greater than or equal to zero");
        }
        if (size < 1 || size > 100) {
            throw new InvalidLeadSearchQueryException("size must be between 1 and 100");
        }
    }

    private static String normalizeQueryText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        if (normalized.length() < 2 || normalized.length() > 100) {
            throw new InvalidLeadSearchQueryException(
                    "q must contain between 2 and 100 characters after trimming"
            );
        }
        return normalized;
    }

    public long offset() {
        return Math.multiplyFull(page, size);
    }
}
