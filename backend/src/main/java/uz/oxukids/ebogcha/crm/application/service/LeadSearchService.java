package uz.oxukids.ebogcha.crm.application.service;

import uz.oxukids.ebogcha.crm.application.exception.LeadSearchBranchAccessDeniedException;
import uz.oxukids.ebogcha.crm.application.port.in.LeadSearchResult;
import uz.oxukids.ebogcha.crm.application.port.in.SearchLeadsQuery;
import uz.oxukids.ebogcha.crm.application.port.in.SearchLeadsUseCase;
import uz.oxukids.ebogcha.crm.application.port.out.LeadQueryPort;
import uz.oxukids.ebogcha.crm.application.port.out.LeadQueryResult;
import uz.oxukids.ebogcha.crm.domain.time.CrmClock;

import java.time.Instant;
import java.util.Objects;

public final class LeadSearchService implements SearchLeadsUseCase {

    private final LeadQueryPort leadQueryPort;
    private final CrmClock clock;

    public LeadSearchService(LeadQueryPort leadQueryPort, CrmClock clock) {
        this.leadQueryPort = Objects.requireNonNull(leadQueryPort, "leadQueryPort must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public LeadSearchResult searchLeads(SearchLeadsQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        if (query.branchId() != null
                && !leadQueryPort.hasBranchAccess(query.actorUserId(), query.branchId())) {
            throw new LeadSearchBranchAccessDeniedException();
        }
        Instant asOf = Objects.requireNonNull(clock.now(), "clock must not return null");
        LeadQueryResult result = leadQueryPort.search(query, asOf);
        long totalPages = result.totalElements() == 0
                ? 0
                : 1 + ((result.totalElements() - 1) / query.size());
        long offset = query.offset();
        boolean hasNext = offset < result.totalElements() - result.items().size();
        return new LeadSearchResult(
                result.items(),
                query.page(),
                query.size(),
                result.totalElements(),
                totalPages,
                query.page() > 0,
                hasNext
        );
    }
}
