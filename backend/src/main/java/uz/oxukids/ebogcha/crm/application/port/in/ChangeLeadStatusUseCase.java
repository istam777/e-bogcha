package uz.oxukids.ebogcha.crm.application.port.in;

import uz.oxukids.ebogcha.crm.domain.model.Lead;

@FunctionalInterface
public interface ChangeLeadStatusUseCase {

    Lead changeLeadStatus(ChangeLeadStatusCommand command);
}
