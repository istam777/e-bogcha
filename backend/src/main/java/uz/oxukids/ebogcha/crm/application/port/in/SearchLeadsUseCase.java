package uz.oxukids.ebogcha.crm.application.port.in;

@FunctionalInterface
public interface SearchLeadsUseCase {

    LeadSearchResult searchLeads(SearchLeadsQuery query);
}
