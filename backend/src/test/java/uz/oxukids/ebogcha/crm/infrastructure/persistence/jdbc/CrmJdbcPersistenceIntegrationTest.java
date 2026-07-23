package uz.oxukids.ebogcha.crm.infrastructure.persistence.jdbc;

import com.google.i18n.phonenumbers.PhoneNumberUtil;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uz.oxukids.ebogcha.crm.application.port.in.AcceptLeadCommand;
import uz.oxukids.ebogcha.crm.application.port.in.ChangeLeadStatusCommand;
import uz.oxukids.ebogcha.crm.application.port.in.CreateLeadCommand;
import uz.oxukids.ebogcha.crm.application.port.in.CreateLeadResult;
import uz.oxukids.ebogcha.crm.application.port.out.DuplicateLeadDiscoveryPort;
import uz.oxukids.ebogcha.crm.application.port.out.LeadRepository;
import uz.oxukids.ebogcha.crm.application.service.CrmLeadService;
import uz.oxukids.ebogcha.crm.domain.exception.LeadAlreadyOwnedException;
import uz.oxukids.ebogcha.crm.domain.model.Lead;
import uz.oxukids.ebogcha.crm.domain.model.LeadSource;
import uz.oxukids.ebogcha.crm.domain.model.LeadStatus;
import uz.oxukids.ebogcha.crm.domain.model.PhoneNumber;
import uz.oxukids.ebogcha.crm.domain.policy.InitialContactDeadlinePolicy;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CrmJdbcPersistenceIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17.10-alpine3.23")
                    .withDatabaseName("e_bogcha_crm_test")
                    .withUsername("e_bogcha_crm_test")
                    .withPassword("test-only-password");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.default-schema", () -> "public");
        registry.add("spring.flyway.schemas", () -> "public");
    }

    @Autowired
    private CrmLeadService service;

    @Autowired
    private LeadRepository leadRepository;

    @Autowired
    private DuplicateLeadDiscoveryPort duplicateDiscovery;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Flyway flyway;

    @Test
    void createsAndReloadsLeadWithNormalizedAndDisplayPhone() {
        Fixture fixture = insertFixture();
        String displayPhone = exampleNationalPhone();
        UUID leadId = UUID.randomUUID();

        CreateLeadResult created = service.createLead(command(
                fixture, leadId, displayPhone, "Integration Guardian"
        ));
        Lead reloaded = leadRepository.findById(leadId).orElseThrow();

        assertThat(created.duplicateCandidateIds()).isEmpty();
        assertThat(reloaded.id()).isEqualTo(leadId);
        assertThat(reloaded.organizationId()).isEqualTo(fixture.organizationId());
        assertThat(reloaded.branchId()).isEqualTo(fixture.branchId());
        assertThat(reloaded.source()).isEqualTo(LeadSource.PHONE);
        assertThat(reloaded.status()).isEqualTo(LeadStatus.NEW);
        assertThat(reloaded.parentOrGuardianName()).isEqualTo("Integration Guardian");
        assertThat(reloaded.primaryDisplayPhone()).isEqualTo(displayPhone);
        assertThat(reloaded.primaryPhone().canonicalValue())
                .isEqualTo(PhoneNumber.of(displayPhone).canonicalValue());
        assertThat(reloaded.firstContactDueAt())
                .isEqualTo(reloaded.createdAt().plusSeconds(24 * 60 * 60));
    }

    @Test
    void discoversDuplicatesWithinOrganizationWithoutBlockingCreation() {
        Fixture firstOrganization = insertFixture();
        Fixture secondOrganization = insertFixture();
        String phone = exampleNationalPhone();
        UUID firstLeadId = UUID.randomUUID();
        UUID secondLeadId = UUID.randomUUID();
        UUID otherOrganizationLeadId = UUID.randomUUID();

        service.createLead(command(firstOrganization, firstLeadId, phone, "First Guardian"));
        service.createLead(command(
                secondOrganization, otherOrganizationLeadId, phone, "Other Guardian"
        ));
        CreateLeadResult second = service.createLead(command(
                firstOrganization, secondLeadId, phone, "Second Guardian"
        ));

        assertThat(second.duplicateCandidateIds()).containsExactly(firstLeadId);
        assertThat(leadRepository.findById(secondLeadId)).isPresent();
        assertThat(duplicateDiscovery.findCandidateIds(
                firstOrganization.organizationId(), PhoneNumber.of(phone), firstLeadId
        )).containsExactly(secondLeadId);
    }

    @Test
    void ownershipClaimIsAtomicAndIdempotent() {
        Fixture fixture = insertFixture();
        UUID leadId = createLead(fixture);

        Lead firstClaim = service.acceptLead(new AcceptLeadCommand(leadId, fixture.firstUserId()));
        Lead repeatedClaim = service.acceptLead(
                new AcceptLeadCommand(leadId, fixture.firstUserId())
        );

        assertThat(firstClaim.ownerOperatorId()).contains(fixture.firstUserId());
        assertThat(repeatedClaim.ownerOperatorId()).contains(fixture.firstUserId());
        assertThat(activeAssignmentCount(leadId)).isEqualTo(1);
        assertThatThrownBy(() -> service.acceptLead(
                new AcceptLeadCommand(leadId, fixture.secondUserId())
        )).isInstanceOf(LeadAlreadyOwnedException.class);
        assertThat(ownerProjection(leadId)).isEqualTo(fixture.firstUserId());
    }

    @Test
    void ownershipRequiresExplicitBranchAccess() {
        Fixture fixture = insertFixture();
        UUID leadId = createLead(fixture);

        assertThatThrownBy(() -> service.acceptLead(
                new AcceptLeadCommand(leadId, fixture.ungrantedUserId())
        )).isInstanceOf(UserBranchAccessDeniedException.class);
        assertThat(activeAssignmentCount(leadId)).isZero();
        assertThat(ownerProjection(leadId)).isNull();

        grantBranchAccess(
                fixture.ungrantedUserId(),
                fixture.branchId(),
                fixture.firstUserId()
        );
        Lead claimed = service.acceptLead(
                new AcceptLeadCommand(leadId, fixture.ungrantedUserId())
        );

        assertThat(claimed.ownerOperatorId()).contains(fixture.ungrantedUserId());
        assertThat(activeAssignmentCount(leadId)).isEqualTo(1);
        assertThat(ownerProjection(leadId)).isEqualTo(fixture.ungrantedUserId());
    }

    @Test
    void concurrentOwnershipClaimsProduceOneWinner() throws Exception {
        Fixture fixture = insertFixture();
        UUID leadId = createLead(fixture);
        CyclicBarrier start = new CyclicBarrier(2);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<ClaimOutcome> first = executor.submit(
                    () -> claimAfterBarrier(start, leadId, fixture.firstUserId())
            );
            Future<ClaimOutcome> second = executor.submit(
                    () -> claimAfterBarrier(start, leadId, fixture.secondUserId())
            );

            assertThat(List.of(first.get(), second.get()))
                    .containsExactlyInAnyOrder(ClaimOutcome.CLAIMED, ClaimOutcome.REJECTED);
        }
        assertThat(activeAssignmentCount(leadId)).isEqualTo(1);
        UUID persistedOwner = ownerProjection(leadId);
        assertThat(persistedOwner)
                .isIn(fixture.firstUserId(), fixture.secondUserId());
        assertThat(leadRepository.findById(leadId).orElseThrow().ownerOperatorId())
                .contains(persistedOwner);
    }

    @Test
    void statusChangeUpdatesLeadAndAppendsActorHistory() {
        Fixture fixture = insertFixture();
        UUID leadId = createLead(fixture);

        Lead contacted = service.changeLeadStatus(new ChangeLeadStatusCommand(
                leadId, LeadStatus.CONTACTED, null, fixture.firstUserId()
        ));

        assertThat(contacted.status()).isEqualTo(LeadStatus.CONTACTED);
        StatusHistory history = jdbc.queryForObject(
                """
                SELECT fs.code AS from_code,
                       ts.code AS to_code,
                       h.changed_by,
                       h.changed_at,
                       l.updated_at
                  FROM lead_status_history h
                  JOIN lead_statuses fs ON fs.id = h.from_status_id
                  JOIN lead_statuses ts ON ts.id = h.to_status_id
                  JOIN leads l ON l.id = h.lead_id
                 WHERE h.lead_id = ?
                """,
                (resultSet, rowNumber) -> new StatusHistory(
                        resultSet.getString("from_code"),
                        resultSet.getString("to_code"),
                        resultSet.getObject("changed_by", UUID.class),
                        resultSet.getTimestamp("changed_at").toInstant(),
                        resultSet.getTimestamp("updated_at").toInstant()
                ),
                leadId
        );
        assertThat(history).isNotNull();
        assertThat(history.fromCode()).isEqualTo("NEW");
        assertThat(history.toCode()).isEqualTo("CONTACTED");
        assertThat(history.changedBy()).isEqualTo(fixture.firstUserId());
        assertThat(history.changedAt()).isEqualTo(history.updatedAt());
    }

    @Test
    void lostReasonIsStoredAndClearedWhenLeadReopens() {
        Fixture fixture = insertFixture();
        UUID leadId = createLead(fixture);

        service.changeLeadStatus(new ChangeLeadStatusCommand(
                leadId, LeadStatus.LOST, fixture.lostReasonId(), fixture.firstUserId()
        ));
        assertThat(lostReason(leadId)).isEqualTo(fixture.lostReasonId());

        Lead reopened = service.changeLeadStatus(new ChangeLeadStatusCommand(
                leadId, LeadStatus.NEW, null, fixture.firstUserId()
        ));
        assertThat(reopened.status()).isEqualTo(LeadStatus.NEW);
        assertThat(lostReason(leadId)).isNull();
        assertThat(statusHistoryCount(leadId)).isEqualTo(2);
    }

    @Test
    void archiveTimestampIsSetAndClearedWhenLeadReopens() {
        Fixture fixture = insertFixture();
        UUID leadId = createLead(fixture);

        service.changeLeadStatus(new ChangeLeadStatusCommand(
                leadId, LeadStatus.ARCHIVED, null, fixture.firstUserId()
        ));
        assertThat(archivedAt(leadId)).isNotNull();

        service.changeLeadStatus(new ChangeLeadStatusCommand(
                leadId, LeadStatus.NEW, null, fixture.firstUserId()
        ));
        assertThat(archivedAt(leadId)).isNull();
        assertThat(statusHistoryCount(leadId)).isEqualTo(2);
    }

    @Test
    void failedPhoneInsertRollsBackLeadInsert() {
        Fixture fixture = insertFixture();
        UUID leadId = UUID.randomUUID();
        String validPhone = PhoneNumber.of(exampleNationalPhone()).canonicalValue();
        Lead invalidForSchema = Lead.create(
                leadId,
                fixture.organizationId(),
                fixture.branchId(),
                LeadSource.PHONE,
                "Rollback Guardian",
                validPhone + "x".repeat(50),
                PhoneNumber.of(validPhone),
                Instant.now(),
                new InitialContactDeadlinePolicy()
        );

        assertThatThrownBy(() -> leadRepository.saveNew(invalidForSchema))
                .isInstanceOf(CrmPersistenceException.class);
        assertThat(count("leads", leadId)).isZero();
        assertThat(count("lead_phones", leadId)).isZero();
    }

    @Test
    void migrationInventoryRemainsExactlyV1ThroughV13() {
        assertThat(flyway.info().applied())
                .extracting(migration -> migration.getVersion().getVersion())
                .containsExactly(
                        "1", "2", "3", "4", "5", "6", "7",
                        "8", "9", "10", "11", "12", "13"
                );
    }

    private Fixture insertFixture() {
        UUID organizationId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        UUID newStatusId = UUID.randomUUID();
        UUID contactedStatusId = UUID.randomUUID();
        UUID lostStatusId = UUID.randomUUID();
        UUID archivedStatusId = UUID.randomUUID();
        UUID lostReasonId = UUID.randomUUID();
        UUID firstUserId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();
        UUID ungrantedUserId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String suffix = organizationId.toString().substring(0, 8);
        UUID activeUserStatusId = jdbc.queryForObject(
                "SELECT id FROM user_statuses WHERE code = 'ACTIVE'",
                UUID.class
        );

        jdbc.update(
                """
                INSERT INTO organizations (id, code, name, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                organizationId, "ORG-" + suffix, "CRM Integration Organization", now, now
        );
        jdbc.update(
                """
                INSERT INTO branches (
                    id, organization_id, code, name, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
                branchId, organizationId, "MAIN", "Main Branch", now, now
        );
        jdbc.update(
                """
                INSERT INTO lead_sources (
                    id, organization_id, code, name, source_type, sort_order
                ) VALUES (?, ?, 'PHONE', 'Phone', 'PHONE', 1)
                """,
                sourceId, organizationId
        );
        insertStatus(newStatusId, organizationId, "NEW", 1, true, false, false);
        insertStatus(contactedStatusId, organizationId, "CONTACTED", 2, false, false, false);
        insertStatus(lostStatusId, organizationId, "LOST", 3, false, true, false);
        insertStatus(archivedStatusId, organizationId, "ARCHIVED", 4, false, false, true);
        jdbc.update(
                """
                INSERT INTO lost_reasons (id, organization_id, code, name)
                VALUES (?, ?, 'OTHER', 'Other')
                """,
                lostReasonId, organizationId
        );
        insertUser(firstUserId, organizationId, activeUserStatusId, "first-" + suffix, now);
        insertUser(secondUserId, organizationId, activeUserStatusId, "second-" + suffix, now);
        insertUser(
                ungrantedUserId,
                organizationId,
                activeUserStatusId,
                "ungranted-" + suffix,
                now
        );
        grantBranchAccess(firstUserId, branchId, firstUserId);
        grantBranchAccess(secondUserId, branchId, firstUserId);

        return new Fixture(
                organizationId,
                branchId,
                lostReasonId,
                firstUserId,
                secondUserId,
                ungrantedUserId
        );
    }

    private void insertStatus(
            UUID id,
            UUID organizationId,
            String code,
            int order,
            boolean initial,
            boolean lost,
            boolean archived
    ) {
        jdbc.update(
                """
                INSERT INTO lead_statuses (
                    id, organization_id, code, name, pipeline_order,
                    is_initial, is_lost, is_archived
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id, organizationId, code, code, order, initial, lost, archived
        );
    }

    private void insertUser(
            UUID id,
            UUID organizationId,
            UUID statusId,
            String username,
            OffsetDateTime now
    ) {
        jdbc.update(
                """
                INSERT INTO users (
                    id, organization_id, username_normalized, display_name,
                    status_id, created_at, updated_at
                ) VALUES (?, ?, ?, 'CRM Test Operator', ?, ?, ?)
                """,
                id, organizationId, username, statusId, now, now
        );
    }

    private void grantBranchAccess(UUID userId, UUID branchId, UUID grantedBy) {
        jdbc.update(
                """
                INSERT INTO user_branch_access (
                    user_id, branch_id, granted_by, granted_at
                ) VALUES (?, ?, ?, ?)
                """,
                userId,
                branchId,
                grantedBy,
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }

    private UUID createLead(Fixture fixture) {
        UUID leadId = UUID.randomUUID();
        service.createLead(command(
                fixture, leadId, exampleNationalPhone(), "Workflow Guardian"
        ));
        return leadId;
    }

    private CreateLeadCommand command(
            Fixture fixture,
            UUID leadId,
            String displayPhone,
            String guardianName
    ) {
        return new CreateLeadCommand(
                leadId,
                fixture.organizationId(),
                fixture.branchId(),
                LeadSource.PHONE,
                guardianName,
                displayPhone
        );
    }

    private ClaimOutcome claimAfterBarrier(
            CyclicBarrier barrier,
            UUID leadId,
            UUID userId
    ) throws Exception {
        barrier.await();
        try {
            service.acceptLead(new AcceptLeadCommand(leadId, userId));
            return ClaimOutcome.CLAIMED;
        } catch (LeadAlreadyOwnedException exception) {
            return ClaimOutcome.REJECTED;
        }
    }

    private int activeAssignmentCount(UUID leadId) {
        return jdbc.queryForObject(
                """
                SELECT count(*)
                  FROM lead_assignments
                 WHERE lead_id = ?
                   AND ended_at IS NULL
                """,
                Integer.class,
                leadId
        );
    }

    private int statusHistoryCount(UUID leadId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM lead_status_history WHERE lead_id = ?",
                Integer.class,
                leadId
        );
    }

    private UUID ownerProjection(UUID leadId) {
        return jdbc.queryForObject(
                "SELECT owner_user_id FROM leads WHERE id = ?",
                UUID.class,
                leadId
        );
    }

    private UUID lostReason(UUID leadId) {
        return jdbc.queryForObject(
                "SELECT lost_reason_id FROM leads WHERE id = ?",
                UUID.class,
                leadId
        );
    }

    private Instant archivedAt(UUID leadId) {
        return jdbc.query(
                "SELECT archived_at FROM leads WHERE id = ?",
                resultSet -> resultSet.next() && resultSet.getTimestamp(1) != null
                        ? resultSet.getTimestamp(1).toInstant()
                        : null,
                leadId
        );
    }

    private int count(String table, UUID leadId) {
        String column = table.equals("leads") ? "id" : "lead_id";
        return jdbc.queryForObject(
                "SELECT count(*) FROM " + table + " WHERE " + column + " = ?",
                Integer.class,
                leadId
        );
    }

    private static String exampleNationalPhone() {
        PhoneNumberUtil util = PhoneNumberUtil.getInstance();
        return util.format(
                util.getExampleNumber("UZ"),
                PhoneNumberUtil.PhoneNumberFormat.NATIONAL
        );
    }

    private enum ClaimOutcome {
        CLAIMED,
        REJECTED
    }

    private record Fixture(
            UUID organizationId,
            UUID branchId,
            UUID lostReasonId,
            UUID firstUserId,
            UUID secondUserId,
            UUID ungrantedUserId
    ) {}

    private record StatusHistory(
            String fromCode,
            String toCode,
            UUID changedBy,
            Instant changedAt,
            Instant updatedAt
    ) {}
}
