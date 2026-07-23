package uz.oxukids.ebogcha.crm.application.port.in;

import uz.oxukids.ebogcha.crm.domain.model.Lead;

@FunctionalInterface
public interface AcceptLeadUseCase {

    Lead acceptLead(AcceptLeadCommand command);
}
