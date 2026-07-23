package uz.oxukids.ebogcha.crm.application.port.out;

import uz.oxukids.ebogcha.crm.application.port.in.SearchLeadsQuery;

import java.time.Instant;
import java.util.UUID;

public interface LeadQueryPort {

    boolean hasBranchAccess(UUID actorUserId, UUID branchId);

    LeadQueryResult search(SearchLeadsQuery query, Instant asOf);
}
