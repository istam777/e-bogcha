package uz.oxukids.ebogcha.crm.infrastructure.web;

import org.springframework.stereotype.Component;
import uz.oxukids.ebogcha.crm.application.port.in.CreateLeadResult;
import uz.oxukids.ebogcha.crm.domain.model.Lead;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Component
public class CrmLeadApiMapper {

    public LeadResponse toResponse(Lead lead) {
        return new LeadResponse(
                lead.id(),
                lead.organizationId(),
                lead.branchId(),
                lead.source(),
                lead.status(),
                lead.parentOrGuardianName(),
                lead.primaryDisplayPhone(),
                lead.ownerOperatorId().orElse(null),
                lead.lostReasonId().orElse(null),
                lead.createdAt(),
                lead.firstContactDueAt()
        );
    }

    public CreateLeadResponse toCreateResponse(CreateLeadResult result) {
        LeadResponse lead = toResponse(result.lead());
        List<UUID> duplicateCandidateIds = result.duplicateCandidateIds().stream()
                .sorted(Comparator.comparing(UUID::toString))
                .toList();
        return new CreateLeadResponse(
                lead.id(),
                lead.organizationId(),
                lead.branchId(),
                lead.source(),
                lead.status(),
                lead.parentOrGuardianName(),
                lead.displayPhone(),
                lead.ownerOperatorId(),
                lead.lostReasonId(),
                lead.createdAt(),
                lead.firstContactDueAt(),
                duplicateCandidateIds
        );
    }
}
