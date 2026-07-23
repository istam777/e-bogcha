package uz.oxukids.ebogcha.crm.application.port.out;

import uz.oxukids.ebogcha.crm.domain.model.PhoneNumber;

import java.util.Set;
import java.util.UUID;

@FunctionalInterface
public interface DuplicateLeadDiscoveryPort {

    Set<UUID> findCandidateIds(
            UUID organizationId,
            PhoneNumber normalizedPhone,
            UUID excludedLeadId
    );
}
