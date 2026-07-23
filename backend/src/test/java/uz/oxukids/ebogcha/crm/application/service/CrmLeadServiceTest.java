package uz.oxukids.ebogcha.crm.application.service;

import com.google.i18n.phonenumbers.PhoneNumberUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.oxukids.ebogcha.crm.application.port.in.AcceptLeadCommand;
import uz.oxukids.ebogcha.crm.application.port.in.ChangeLeadStatusCommand;
import uz.oxukids.ebogcha.crm.application.port.in.CreateLeadCommand;
import uz.oxukids.ebogcha.crm.application.port.in.CreateLeadResult;
import uz.oxukids.ebogcha.crm.application.port.out.LeadRepository;
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
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CrmLeadServiceTest {

    private static final UUID LEAD_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID DUPLICATE_CANDIDATE_ID =
            UUID.fromString("10000000-0000-4000-8000-000000000002");
    private static final UUID ORGANIZATION_ID = UUID.fromString("20000000-0000-4000-8000-000000000001");
    private static final UUID BRANCH_ID = UUID.fromString("25000000-0000-4000-8000-000000000001");
    private static final UUID OPERATOR_ONE = UUID.fromString("30000000-0000-4000-8000-000000000001");
    private static final UUID OPERATOR_TWO = UUID.fromString("30000000-0000-4000-8000-000000000002");
    private static final UUID LOST_REASON_ID = UUID.fromString("40000000-0000-4000-8000-000000000001");
    private static final String GUARDIAN_NAME = "Test Guardian";
    private static final Instant NOW = Instant.parse("2026-07-22T08:00:00Z");

    private InMemoryLeadRepository repository;
    private CrmLeadService service;
    private Set<UUID> duplicateCandidateIds;

    @BeforeEach
    void setUp() {
        repository = new InMemoryLeadRepository();
        duplicateCandidateIds = Set.of();
        service = new CrmLeadService(
                repository,
                (organizationId, phoneNumber, excludedLeadId) -> duplicateCandidateIds,
                () -> NOW,
                new InitialContactDeadlinePolicy(),
                LeadStatusTransitionPolicy.standard()
        );
    }

    @Test
    void createsNormalizedLeadAndChecksDuplicateWithinOrganization() {
        String displayPhone = exampleNationalPhone("UZ");

        CreateLeadResult result = service.createLead(createCommand(LeadSource.PHONE, displayPhone));
        Lead lead = result.lead();

        assertThat(lead.status()).isEqualTo(LeadStatus.NEW);
        assertThat(lead.branchId()).isEqualTo(BRANCH_ID);
        assertThat(lead.parentOrGuardianName()).isEqualTo(GUARDIAN_NAME);
        assertThat(lead.primaryDisplayPhone()).isEqualTo(displayPhone);
        assertThat(lead.primaryPhone().canonicalValue()).startsWith("+");
        assertThat(lead.primaryPhone().canonicalValue()).isNotEqualTo(displayPhone);
        assertThat(lead.firstContactDueAt()).isEqualTo(NOW.plusSeconds(24 * 60 * 60));
        assertThat(result.duplicateCandidateIds()).isEmpty();
        assertThat(repository.findById(LEAD_ID)).containsSame(lead);
    }

    @Test
    void createsLeadAndReturnsDiscoveredDuplicateCandidates() {
        duplicateCandidateIds = Set.of(DUPLICATE_CANDIDATE_ID);

        CreateLeadResult result = service.createLead(
                createCommand(LeadSource.WALK_IN, exampleNationalPhone("UZ"))
        );

        assertThat(result.duplicateCandidateIds()).containsExactly(DUPLICATE_CANDIDATE_ID);
        assertThat(repository.findById(LEAD_ID)).containsSame(result.lead());
    }

    @Test
    void createCommandDoesNotExposePersonalDataInStringRepresentation() {
        String displayPhone = exampleNationalPhone("UZ");
        CreateLeadCommand command = createCommand(LeadSource.PHONE, displayPhone);

        assertThat(command.toString())
                .doesNotContain(displayPhone)
                .doesNotContain(GUARDIAN_NAME);
    }

    @Test
    void createCommandRequiresBranchAndGuardianName() {
        String displayPhone = exampleNationalPhone("UZ");

        assertThatThrownBy(() -> new CreateLeadCommand(
                LEAD_ID, ORGANIZATION_ID, null, LeadSource.PHONE, GUARDIAN_NAME, displayPhone
        )).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CreateLeadCommand(
                LEAD_ID, ORGANIZATION_ID, BRANCH_ID, LeadSource.PHONE, "  ", displayPhone
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void repositoryClaimContractMakesFirstOperatorWinAtomically() {
        service.createLead(createCommand(LeadSource.SOCIAL_MEDIA, exampleNationalPhone("UZ")));

        Lead first = service.acceptLead(new AcceptLeadCommand(LEAD_ID, OPERATOR_ONE));
        Lead repeated = service.acceptLead(new AcceptLeadCommand(LEAD_ID, OPERATOR_ONE));

        assertThat(first.ownerOperatorId()).contains(OPERATOR_ONE);
        assertThat(repeated.ownerOperatorId()).contains(OPERATOR_ONE);
        assertThatThrownBy(() -> service.acceptLead(new AcceptLeadCommand(LEAD_ID, OPERATOR_TWO)))
                .isInstanceOf(LeadAlreadyOwnedException.class);
    }

    @Test
    void changesStatusThroughExplicitPolicy() {
        service.createLead(createCommand(LeadSource.SOCIAL_MEDIA, exampleNationalPhone("UZ")));

        Lead contacted = service.changeLeadStatus(new ChangeLeadStatusCommand(
                LEAD_ID, LeadStatus.CONTACTED, null, OPERATOR_ONE
        ));

        assertThat(contacted.status()).isEqualTo(LeadStatus.CONTACTED);
        assertThat(repository.statusChangeSaveCount).isEqualTo(1);
        assertThat(repository.previousStatus).isEqualTo(LeadStatus.NEW);
        assertThat(repository.statusChangedAt).isEqualTo(NOW);
        assertThat(repository.statusChangedBy).isEqualTo(OPERATOR_ONE);
        assertThat(repository.findById(LEAD_ID)).containsSame(contacted);
    }

    @Test
    void sameStatusIsIdempotentWithoutPersistingStatusHistory() {
        service.createLead(createCommand(LeadSource.SOCIAL_MEDIA, exampleNationalPhone("UZ")));

        Lead unchanged = service.changeLeadStatus(new ChangeLeadStatusCommand(
                LEAD_ID, LeadStatus.NEW, null, OPERATOR_ONE
        ));

        assertThat(unchanged.status()).isEqualTo(LeadStatus.NEW);
        assertThat(repository.statusChangeSaveCount).isZero();
    }

    @Test
    void statusChangeCommandRequiresActor() {
        assertThatThrownBy(() -> new ChangeLeadStatusCommand(
                LEAD_ID, LeadStatus.CONTACTED, null, null
        )).isInstanceOf(NullPointerException.class);
    }

    private static CreateLeadCommand createCommand(LeadSource source, String displayPhone) {
        return new CreateLeadCommand(
                LEAD_ID,
                ORGANIZATION_ID,
                BRANCH_ID,
                source,
                GUARDIAN_NAME,
                displayPhone
        );
    }

    private static String exampleNationalPhone(String region) {
        PhoneNumberUtil util = PhoneNumberUtil.getInstance();
        return util.format(util.getExampleNumber(region), PhoneNumberUtil.PhoneNumberFormat.NATIONAL);
    }

    private static final class InMemoryLeadRepository implements LeadRepository {

        private final Map<UUID, Lead> leads = new HashMap<>();
        private int statusChangeSaveCount;
        private LeadStatus previousStatus;
        private Instant statusChangedAt;
        private UUID statusChangedBy;

        @Override
        public synchronized Optional<Lead> findById(UUID leadId) {
            return Optional.ofNullable(leads.get(leadId));
        }

        @Override
        public synchronized void saveNew(Lead lead) {
            leads.put(lead.id(), lead);
        }

        @Override
        public synchronized void saveStatusChange(
                Lead lead,
                LeadStatus previousStatus,
                Instant changedAt,
                UUID changedByUserId
        ) {
            statusChangeSaveCount++;
            this.previousStatus = previousStatus;
            this.statusChangedAt = changedAt;
            this.statusChangedBy = changedByUserId;
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
