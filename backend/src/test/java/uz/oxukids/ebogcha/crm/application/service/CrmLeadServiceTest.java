package uz.oxukids.ebogcha.crm.application.service;

import com.google.i18n.phonenumbers.PhoneNumberUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.oxukids.ebogcha.crm.application.port.in.AcceptLeadCommand;
import uz.oxukids.ebogcha.crm.application.port.in.ChangeLeadStatusCommand;
import uz.oxukids.ebogcha.crm.application.port.in.CreateLeadCommand;
import uz.oxukids.ebogcha.crm.application.port.out.LeadRepository;
import uz.oxukids.ebogcha.crm.domain.exception.DuplicateLeadException;
import uz.oxukids.ebogcha.crm.domain.exception.LeadAlreadyOwnedException;
import uz.oxukids.ebogcha.crm.domain.exception.LeadNotFoundException;
import uz.oxukids.ebogcha.crm.domain.model.Lead;
import uz.oxukids.ebogcha.crm.domain.model.LeadSource;
import uz.oxukids.ebogcha.crm.domain.model.LeadStatus;
import uz.oxukids.ebogcha.crm.domain.policy.InitialContactDeadlinePolicy;
import uz.oxukids.ebogcha.crm.domain.policy.LeadStatusTransitionPolicy;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CrmLeadServiceTest {

    private static final UUID LEAD_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID ORGANIZATION_ID = UUID.fromString("20000000-0000-4000-8000-000000000001");
    private static final UUID OPERATOR_ONE = UUID.fromString("30000000-0000-4000-8000-000000000001");
    private static final UUID OPERATOR_TWO = UUID.fromString("30000000-0000-4000-8000-000000000002");
    private static final Instant NOW = Instant.parse("2026-07-22T08:00:00Z");

    private InMemoryLeadRepository repository;
    private CrmLeadService service;
    private boolean duplicateExists;

    @BeforeEach
    void setUp() {
        repository = new InMemoryLeadRepository();
        duplicateExists = false;
        service = new CrmLeadService(
                repository,
                (organizationId, phoneNumber) -> duplicateExists,
                () -> NOW,
                new InitialContactDeadlinePolicy(),
                LeadStatusTransitionPolicy.standard()
        );
    }

    @Test
    void createsNormalizedLeadAndChecksDuplicateWithinOrganization() {
        Lead lead = service.createLead(new CreateLeadCommand(
                LEAD_ID, ORGANIZATION_ID, LeadSource.INCOMING_CALL, exampleNationalPhone("UZ")
        ));

        assertThat(lead.status()).isEqualTo(LeadStatus.NEW);
        assertThat(lead.firstContactDueAt()).isEqualTo(NOW.plusSeconds(24 * 60 * 60));
        assertThat(repository.findById(LEAD_ID)).containsSame(lead);
    }

    @Test
    void refusesDuplicateWithoutSavingAnotherLead() {
        duplicateExists = true;

        assertThatThrownBy(() -> service.createLead(new CreateLeadCommand(
                LEAD_ID, ORGANIZATION_ID, LeadSource.WALK_IN, exampleNationalPhone("UZ")
        ))).isInstanceOf(DuplicateLeadException.class);
        assertThat(repository.findById(LEAD_ID)).isEmpty();
    }

    @Test
    void repositoryClaimContractMakesFirstOperatorWinAtomically() {
        service.createLead(new CreateLeadCommand(
                LEAD_ID, ORGANIZATION_ID, LeadSource.SOCIAL_MEDIA, exampleNationalPhone("UZ")
        ));

        Lead first = service.acceptLead(new AcceptLeadCommand(LEAD_ID, OPERATOR_ONE));
        Lead repeated = service.acceptLead(new AcceptLeadCommand(LEAD_ID, OPERATOR_ONE));

        assertThat(first.ownerOperatorId()).contains(OPERATOR_ONE);
        assertThat(repeated.ownerOperatorId()).contains(OPERATOR_ONE);
        assertThatThrownBy(() -> service.acceptLead(new AcceptLeadCommand(LEAD_ID, OPERATOR_TWO)))
                .isInstanceOf(LeadAlreadyOwnedException.class);
    }

    @Test
    void changesStatusThroughExplicitPolicy() {
        service.createLead(new CreateLeadCommand(
                LEAD_ID, ORGANIZATION_ID, LeadSource.SOCIAL_MEDIA, exampleNationalPhone("UZ")
        ));

        Lead contacted = service.changeLeadStatus(new ChangeLeadStatusCommand(LEAD_ID, LeadStatus.CONTACTED));

        assertThat(contacted.status()).isEqualTo(LeadStatus.CONTACTED);
        assertThat(repository.statusChangeSaveCount).isEqualTo(1);
    }

    @Test
    void sameStatusIsIdempotentWithoutPersistingStatusHistory() {
        service.createLead(new CreateLeadCommand(
                LEAD_ID, ORGANIZATION_ID, LeadSource.SOCIAL_MEDIA, exampleNationalPhone("UZ")
        ));

        Lead unchanged = service.changeLeadStatus(new ChangeLeadStatusCommand(LEAD_ID, LeadStatus.NEW));

        assertThat(unchanged.status()).isEqualTo(LeadStatus.NEW);
        assertThat(repository.statusChangeSaveCount).isZero();
    }

    private static String exampleNationalPhone(String region) {
        PhoneNumberUtil util = PhoneNumberUtil.getInstance();
        return util.format(util.getExampleNumber(region), PhoneNumberUtil.PhoneNumberFormat.NATIONAL);
    }

    private static final class InMemoryLeadRepository implements LeadRepository {

        private final Map<UUID, Lead> leads = new HashMap<>();
        private int statusChangeSaveCount;

        @Override
        public synchronized Optional<Lead> findById(UUID leadId) {
            return Optional.ofNullable(leads.get(leadId));
        }

        @Override
        public synchronized void saveNew(Lead lead) {
            leads.put(lead.id(), lead);
        }

        @Override
        public synchronized void saveStatusChange(Lead lead, LeadStatus previousStatus, Instant changedAt) {
            statusChangeSaveCount++;
            leads.put(lead.id(), lead);
        }

        @Override
        public synchronized Lead claimOwnership(UUID leadId, UUID operatorId) {
            Lead lead = Optional.ofNullable(leads.get(leadId))
                    .orElseThrow(() -> new LeadNotFoundException(leadId));
            lead.accept(operatorId);
            return lead;
        }
    }
}
