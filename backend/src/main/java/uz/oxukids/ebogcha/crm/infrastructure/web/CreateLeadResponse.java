package uz.oxukids.ebogcha.crm.infrastructure.web;

import uz.oxukids.ebogcha.crm.domain.model.LeadSource;
import uz.oxukids.ebogcha.crm.domain.model.LeadStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CreateLeadResponse(
        UUID id,
        UUID organizationId,
        UUID branchId,
        LeadSource source,
        LeadStatus status,
        String parentOrGuardianName,
        String displayPhone,
        UUID ownerOperatorId,
        UUID lostReasonId,
        Instant createdAt,
        Instant firstContactDueAt,
        List<UUID> duplicateCandidateIds
) {
}
