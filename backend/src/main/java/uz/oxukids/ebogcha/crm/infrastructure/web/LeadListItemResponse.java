package uz.oxukids.ebogcha.crm.infrastructure.web;

import uz.oxukids.ebogcha.crm.domain.model.LeadSource;
import uz.oxukids.ebogcha.crm.domain.model.LeadStatus;

import java.time.Instant;
import java.util.UUID;

public record LeadListItemResponse(
        UUID id,
        UUID organizationId,
        UUID branchId,
        String branchName,
        LeadSource source,
        LeadStatus status,
        String parentOrGuardianName,
        String displayPhone,
        UUID ownerOperatorId,
        String ownerDisplayName,
        Instant createdAt,
        Instant updatedAt,
        Instant firstContactDueAt,
        boolean overdue
) {
}
