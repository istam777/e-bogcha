package uz.oxukids.ebogcha.crm.application.port.in;

import uz.oxukids.ebogcha.crm.domain.model.Lead;

@FunctionalInterface
public interface CreateLeadUseCase {

    Lead createLead(CreateLeadCommand command);
}
