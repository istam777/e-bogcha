package uz.oxukids.ebogcha.crm.application.service;

import uz.oxukids.ebogcha.crm.application.port.in.AcceptLeadCommand;
import uz.oxukids.ebogcha.crm.application.port.in.AcceptLeadUseCase;
import uz.oxukids.ebogcha.crm.application.port.in.ChangeLeadStatusCommand;
import uz.oxukids.ebogcha.crm.application.port.in.ChangeLeadStatusUseCase;
import uz.oxukids.ebogcha.crm.application.port.in.CreateLeadCommand;
import uz.oxukids.ebogcha.crm.application.port.in.CreateLeadResult;
import uz.oxukids.ebogcha.crm.application.port.in.CreateLeadUseCase;
import uz.oxukids.ebogcha.crm.application.port.out.DuplicateLeadDiscoveryPort;
import uz.oxukids.ebogcha.crm.application.port.out.LeadRepository;
import uz.oxukids.ebogcha.crm.domain.exception.LeadNotFoundException;
import uz.oxukids.ebogcha.crm.domain.model.Lead;
import uz.oxukids.ebogcha.crm.domain.model.PhoneNumber;
import uz.oxukids.ebogcha.crm.domain.policy.InitialContactDeadlinePolicy;
import uz.oxukids.ebogcha.crm.domain.policy.LeadStatusTransitionPolicy;
import uz.oxukids.ebogcha.crm.domain.time.CrmClock;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class CrmLeadService implements CreateLeadUseCase, AcceptLeadUseCase, ChangeLeadStatusUseCase {

    private final LeadRepository leadRepository;
    private final DuplicateLeadDiscoveryPort duplicateLeadDiscoveryPort;
    private final CrmClock clock;
    private final InitialContactDeadlinePolicy deadlinePolicy;
    private final LeadStatusTransitionPolicy transitionPolicy;

    public CrmLeadService(
            LeadRepository leadRepository,
            DuplicateLeadDiscoveryPort duplicateLeadDiscoveryPort,
            CrmClock clock,
            InitialContactDeadlinePolicy deadlinePolicy,
            LeadStatusTransitionPolicy transitionPolicy
    ) {
        this.leadRepository = Objects.requireNonNull(leadRepository, "leadRepository must not be null");
        this.duplicateLeadDiscoveryPort = Objects.requireNonNull(
                duplicateLeadDiscoveryPort, "duplicateLeadDiscoveryPort must not be null"
        );
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.deadlinePolicy = Objects.requireNonNull(deadlinePolicy, "deadlinePolicy must not be null");
        this.transitionPolicy = Objects.requireNonNull(transitionPolicy, "transitionPolicy must not be null");
    }

    @Override
    public CreateLeadResult createLead(CreateLeadCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        PhoneNumber normalizedPhone = PhoneNumber.of(command.displayPhone());
        Set<UUID> duplicateCandidateIds = Objects.requireNonNull(
                duplicateLeadDiscoveryPort.findCandidateIds(
                        command.organizationId(), normalizedPhone, command.leadId()
                ),
                "duplicate candidate IDs must not be null"
        );
        Lead lead = Lead.create(
                command.leadId(), command.organizationId(), command.branchId(), command.source(),
                command.parentOrGuardianName(), command.displayPhone(), normalizedPhone,
                clock.now(), deadlinePolicy
        );
        leadRepository.saveNew(lead);
        return new CreateLeadResult(lead, duplicateCandidateIds);
    }

    @Override
    public Lead acceptLead(AcceptLeadCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return leadRepository.claimOwnership(command.leadId(), command.operatorId());
    }

    @Override
    public Lead changeLeadStatus(ChangeLeadStatusCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        Lead lead = leadRepository.findById(command.leadId())
                .orElseThrow(() -> new LeadNotFoundException(command.leadId()));
        var previousStatus = lead.status();
        if (lead.changeStatus(command.targetStatus(), command.lostReasonId(), transitionPolicy)) {
            leadRepository.saveStatusChange(
                    lead, previousStatus, clock.now(), command.changedByUserId()
            );
        }
        return lead;
    }
}
