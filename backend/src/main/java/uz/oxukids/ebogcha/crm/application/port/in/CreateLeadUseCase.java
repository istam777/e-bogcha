package uz.oxukids.ebogcha.crm.application.port.in;

@FunctionalInterface
public interface CreateLeadUseCase {

    CreateLeadResult createLead(CreateLeadCommand command);
}
