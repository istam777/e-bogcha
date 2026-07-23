package uz.oxukids.ebogcha.crm.application.port.in;

import uz.oxukids.ebogcha.crm.domain.model.Lead;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record CreateLeadResult(Lead lead, Set<UUID> duplicateCandidateIds) {

    public CreateLeadResult {
        Objects.requireNonNull(lead, "lead must not be null");
        duplicateCandidateIds = Set.copyOf(
                Objects.requireNonNull(duplicateCandidateIds, "duplicateCandidateIds must not be null")
        );
    }
}
