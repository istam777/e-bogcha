package uz.oxukids.ebogcha.crm.infrastructure.web;

import org.springframework.stereotype.Component;
import uz.oxukids.ebogcha.crm.application.port.in.LeadListItem;
import uz.oxukids.ebogcha.crm.application.port.in.LeadSearchResult;

@Component
public class CrmLeadSearchApiMapper {

    public LeadSearchResponse toResponse(LeadSearchResult result) {
        return new LeadSearchResponse(
                result.items().stream().map(this::toItemResponse).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages(),
                result.hasPrevious(),
                result.hasNext()
        );
    }

    private LeadListItemResponse toItemResponse(LeadListItem item) {
        return new LeadListItemResponse(
                item.id(),
                item.organizationId(),
                item.branchId(),
                item.branchName(),
                item.source(),
                item.status(),
                item.parentOrGuardianName(),
                item.displayPhone(),
                item.ownerOperatorId(),
                item.ownerDisplayName(),
                item.createdAt(),
                item.updatedAt(),
                item.firstContactDueAt(),
                item.overdue()
        );
    }
}
