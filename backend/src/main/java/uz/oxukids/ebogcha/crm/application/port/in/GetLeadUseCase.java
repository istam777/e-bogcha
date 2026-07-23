package uz.oxukids.ebogcha.crm.application.port.in;

import uz.oxukids.ebogcha.crm.domain.model.Lead;

import java.util.UUID;

@FunctionalInterface
public interface GetLeadUseCase {

    Lead getLead(UUID leadId);
}
