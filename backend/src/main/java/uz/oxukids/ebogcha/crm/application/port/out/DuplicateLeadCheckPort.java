package uz.oxukids.ebogcha.crm.application.port.out;

import uz.oxukids.ebogcha.crm.domain.model.PhoneNumber;

import java.util.UUID;

@FunctionalInterface
public interface DuplicateLeadCheckPort {

    boolean existsByOrganizationAndPhone(UUID organizationId, PhoneNumber normalizedPhone);
}
