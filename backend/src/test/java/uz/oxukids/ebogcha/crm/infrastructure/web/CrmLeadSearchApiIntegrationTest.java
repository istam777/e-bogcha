package uz.oxukids.ebogcha.crm.infrastructure.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uz.oxukids.ebogcha.crm.domain.time.CrmClock;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Import(CrmLeadSearchApiIntegrationTest.FixedClockConfiguration.class)
class CrmLeadSearchApiIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-07-23T12:00:00Z");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17.10-alpine3.23")
                    .withDatabaseName("e_bogcha_crm_search_test")
                    .withUsername("e_bogcha_crm_search_test")
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
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void defaultPageIncludesOnlyAccessibleBranchesAndProjectsListFields() throws Exception {
        Fixture fixture = insertFixture();
        UUID assignedLead = insertLead(
                fixture,
                fixture.branchAId(),
                fixture.phoneSourceId(),
                fixture.newStatusId(),
                fixture.ownerUserId(),
                "Accessible Assigned Guardian",
                "+998901234561",
                "+998 90 123 45 61",
                NOW.minusSeconds(60),
                NOW.plusSeconds(3600)
        );
        UUID unassignedLead = insertLead(
                fixture,
                fixture.branchBId(),
                fixture.walkInSourceId(),
                fixture.contactedStatusId(),
                null,
                "Accessible Unassigned Guardian",
                "+998901234562",
                "+998 90 123 45 62",
                NOW.minusSeconds(120),
                NOW.minusSeconds(3600)
        );
        insertLead(
                fixture,
                fixture.inaccessibleBranchId(),
                fixture.phoneSourceId(),
                fixture.newStatusId(),
                null,
                "Inaccessible Guardian",
                "+998901234563",
                "+998 90 123 45 63",
                NOW,
                NOW.minusSeconds(1)
        );
        UUID otherOrganizationLead = insertOtherOrganizationLead(fixture);
        grantAccess(fixture.actorUserId(), fixture.otherOrganizationBranchId(), fixture.actorUserId());

        mvc.perform(get("/api/v1/crm/leads")
                        .header("X-Actor-User-Id", fixture.actorUserId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.hasPrevious").value(false))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].id").value(assignedLead.toString()))
                .andExpect(jsonPath("$.items[0].branchName").value("Search Branch A"))
                .andExpect(jsonPath("$.items[0].ownerOperatorId")
                        .value(fixture.ownerUserId().toString()))
                .andExpect(jsonPath("$.items[0].ownerDisplayName").value("Search Owner"))
                .andExpect(jsonPath("$.items[0].overdue").value(false))
                .andExpect(jsonPath("$.items[1].id").value(unassignedLead.toString()))
                .andExpect(jsonPath("$.items[1].branchName").value("Search Branch B"))
                .andExpect(jsonPath("$.items[1].ownerOperatorId").doesNotExist())
                .andExpect(jsonPath("$.items[1].ownerDisplayName").doesNotExist())
                .andExpect(content().string(not(containsString("normalizedPhone"))))
                .andExpect(content().string(not(containsString(otherOrganizationLead.toString()))))
                .andExpect(content().string(not(containsString("Inaccessible Guardian"))));
    }

    @Test
    void explicitBranchFilterRequiresAccess() throws Exception {
        Fixture fixture = insertFixture();
        UUID branchALead = insertStandardLead(fixture, fixture.branchAId(), "Branch A Guardian");
        insertStandardLead(fixture, fixture.branchBId(), "Branch B Guardian");

        mvc.perform(get("/api/v1/crm/leads")
                        .header("X-Actor-User-Id", fixture.actorUserId())
                        .queryParam("branchId", fixture.branchAId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(branchALead.toString()));

        mvc.perform(get("/api/v1/crm/leads")
                        .header("X-Actor-User-Id", fixture.actorUserId())
                        .queryParam("branchId", fixture.inaccessibleBranchId().toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CRM_BRANCH_ACCESS_DENIED"));
    }

    @Test
    void statusSourceAndOwnerFiltersCombineWithAnd() throws Exception {
        Fixture fixture = insertFixture();
        UUID matching = insertLead(
                fixture,
                fixture.branchAId(),
                fixture.phoneSourceId(),
                fixture.newStatusId(),
                fixture.ownerUserId(),
                "Matching Guardian",
                "+998901234564",
                "+998 90 123 45 64",
                NOW,
                NOW.plusSeconds(1)
        );
        insertLead(
                fixture,
                fixture.branchAId(),
                fixture.walkInSourceId(),
                fixture.newStatusId(),
                fixture.ownerUserId(),
                "Wrong Source Guardian",
                "+998901234565",
                "+998 90 123 45 65",
                NOW.minusSeconds(1),
                NOW.plusSeconds(1)
        );
        insertLead(
                fixture,
                fixture.branchAId(),
                fixture.phoneSourceId(),
                fixture.contactedStatusId(),
                null,
                "Wrong Status Guardian",
                "+998901234566",
                "+998 90 123 45 66",
                NOW.minusSeconds(2),
                NOW.plusSeconds(1)
        );

        mvc.perform(get("/api/v1/crm/leads")
                        .header("X-Actor-User-Id", fixture.actorUserId())
                        .queryParam("status", "NEW")
                        .queryParam("source", "PHONE")
                        .queryParam("ownerOperatorId", fixture.ownerUserId().toString())
                        .queryParam("ownerState", "ASSIGNED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(matching.toString()));

        mvc.perform(get("/api/v1/crm/leads")
                        .header("X-Actor-User-Id", fixture.actorUserId())
                        .queryParam("ownerState", "UNASSIGNED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].parentOrGuardianName")
                        .value("Wrong Status Guardian"));

        mvc.perform(get("/api/v1/crm/leads")
                        .header("X-Actor-User-Id", fixture.actorUserId())
                        .queryParam("ownerOperatorId", fixture.ownerUserId().toString())
                        .queryParam("ownerState", "UNASSIGNED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CRM_REQUEST_INVALID"));
    }

    @Test
    void guardianSearchIsTrimmedCaseInsensitivePartialAndWildcardSafe() throws Exception {
        Fixture fixture = insertFixture();
        UUID matching = insertStandardLead(
                fixture, fixture.branchAId(), "Fictional Search Guardian"
        );
        insertStandardLead(fixture, fixture.branchAId(), "Unrelated Family");

        mvc.perform(get("/api/v1/crm/leads")
                        .header("X-Actor-User-Id", fixture.actorUserId())
                        .queryParam("q", "  tIoNaL SeArCh  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(matching.toString()));

        mvc.perform(get("/api/v1/crm/leads")
                        .header("X-Actor-User-Id", fixture.actorUserId())
                        .queryParam("q", "No Match"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void phoneSearchIsPartialFormattingInsensitiveButNamesWithDigitsAreNotPhoneQueries() throws Exception {
        Fixture fixture = insertFixture();
        UUID phoneMatch = insertLead(
                fixture,
                fixture.branchAId(),
                fixture.phoneSourceId(),
                fixture.newStatusId(),
                null,
                "Phone Match Guardian",
                "+998901234567",
                "+998 (90) 123-45-67",
                NOW,
                NOW.plusSeconds(1)
        );

        mvc.perform(get("/api/v1/crm/leads")
                        .header("X-Actor-User-Id", fixture.actorUserId())
                        .queryParam("q", "(90) 123-45"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(phoneMatch.toString()));

        mvc.perform(get("/api/v1/crm/leads")
                        .header("X-Actor-User-Id", fixture.actorUserId())
                        .queryParam("q", "Family2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void overdueFilterUsesApplicationClockAndOnlyNewStatus() throws Exception {
        Fixture fixture = insertFixture();
        UUID overdueNew = insertLead(
                fixture,
                fixture.branchAId(),
                fixture.phoneSourceId(),
                fixture.newStatusId(),
                null,
                "Overdue New Guardian",
                "+998901234568",
                "+998 90 123 45 68",
                NOW.minusSeconds(7200),
                NOW.minusSeconds(1)
        );
        insertLead(
                fixture,
                fixture.branchAId(),
                fixture.phoneSourceId(),
                fixture.newStatusId(),
                null,
                "Due Exactly Now Guardian",
                "+998901234569",
                "+998 90 123 45 69",
                NOW.minusSeconds(3600),
                NOW
        );
        insertLead(
                fixture,
                fixture.branchAId(),
                fixture.phoneSourceId(),
                fixture.contactedStatusId(),
                null,
                "Expired Contacted Guardian",
                "+998901234570",
                "+998 90 123 45 70",
                NOW.minusSeconds(3600),
                NOW.minusSeconds(1)
        );

        mvc.perform(get("/api/v1/crm/leads")
                        .header("X-Actor-User-Id", fixture.actorUserId())
                        .queryParam("overdueOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(overdueNew.toString()))
                .andExpect(jsonPath("$.items[0].overdue").value(true));
    }

    @Test
    void createdDateBoundsAreInclusiveThenExclusive() throws Exception {
        Fixture fixture = insertFixture();
        Instant boundary = NOW.minusSeconds(3600);
        UUID boundaryLead = insertLead(
                fixture,
                fixture.branchAId(),
                fixture.phoneSourceId(),
                fixture.newStatusId(),
                null,
                "Boundary Guardian",
                "+998901234571",
                "+998 90 123 45 71",
                boundary,
                NOW
        );
        insertLead(
                fixture,
                fixture.branchAId(),
                fixture.phoneSourceId(),
                fixture.newStatusId(),
                null,
                "Upper Boundary Guardian",
                "+998901234572",
                "+998 90 123 45 72",
                NOW,
                NOW.plusSeconds(1)
        );

        mvc.perform(get("/api/v1/crm/leads")
                        .header("X-Actor-User-Id", fixture.actorUserId())
                        .queryParam("createdFrom", boundary.toString())
                        .queryParam("createdTo", NOW.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(boundaryLead.toString()));

        mvc.perform(get("/api/v1/crm/leads")
                        .header("X-Actor-User-Id", fixture.actorUserId())
                        .queryParam("createdFrom", NOW.toString())
                        .queryParam("createdTo", boundary.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CRM_REQUEST_INVALID"));
    }

    @Test
    void paginationMetadataAndStableTieOrderingAreCorrect() throws Exception {
        Fixture fixture = insertFixture();
        UUID lowerId = UUID.fromString("11111111-1111-4111-8111-111111111111");
        UUID higherId = UUID.fromString("ffffffff-ffff-4fff-bfff-ffffffffffff");
        insertLeadWithId(
                fixture, lowerId, fixture.branchAId(), "Lower ID Guardian", NOW
        );
        insertLeadWithId(
                fixture, higherId, fixture.branchAId(), "Higher ID Guardian", NOW
        );
        UUID olderId = insertLead(
                fixture,
                fixture.branchAId(),
                fixture.phoneSourceId(),
                fixture.newStatusId(),
                null,
                "Older Guardian",
                "+998901234575",
                "+998 90 123 45 75",
                NOW.minusSeconds(1),
                NOW.plusSeconds(1)
        );
        insertSecondaryPhone(olderId, "+998901234576");

        mvc.perform(get("/api/v1/crm/leads")
                        .header("X-Actor-User-Id", fixture.actorUserId())
                        .queryParam("page", "0")
                        .queryParam("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.hasPrevious").value(false))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].id").value(higherId.toString()))
                .andExpect(jsonPath("$.items[1].id").value(lowerId.toString()));

        mvc.perform(get("/api/v1/crm/leads")
                        .header("X-Actor-User-Id", fixture.actorUserId())
                        .queryParam("page", "4")
                        .queryParam("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.hasPrevious").value(true))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void invalidPaginationAndTypedParametersReturnStructuredBadRequest() throws Exception {
        Fixture fixture = insertFixture();

        for (String query : new String[]{
                "?page=-1",
                "?size=0",
                "?size=101",
                "?branchId=not-a-uuid",
                "?status=UNKNOWN",
                "?source=UNKNOWN",
                "?createdFrom=not-an-instant",
                "?q=x"
        }) {
            mvc.perform(get("/api/v1/crm/leads" + query)
                            .header("X-Actor-User-Id", fixture.actorUserId()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CRM_REQUEST_INVALID"))
                    .andExpect(jsonPath("$.timestamp").isString());
        }
    }

    @Test
    void validActorWithoutBranchAccessReceivesEmptyPage() throws Exception {
        Fixture fixture = insertFixture();

        mvc.perform(get("/api/v1/crm/leads")
                        .header("X-Actor-User-Id", fixture.noAccessUserId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    private Fixture insertFixture() {
        UUID organizationId = UUID.randomUUID();
        UUID branchAId = UUID.randomUUID();
        UUID branchBId = UUID.randomUUID();
        UUID inaccessibleBranchId = UUID.randomUUID();
        UUID phoneSourceId = UUID.randomUUID();
        UUID walkInSourceId = UUID.randomUUID();
        UUID newStatusId = UUID.randomUUID();
        UUID contactedStatusId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        UUID noAccessUserId = UUID.randomUUID();
        UUID otherOrganizationId = UUID.randomUUID();
        UUID otherOrganizationBranchId = UUID.randomUUID();
        UUID otherSourceId = UUID.randomUUID();
        UUID otherStatusId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
        String suffix = organizationId.toString().substring(0, 8);
        UUID activeStatusId = jdbc.queryForObject(
                "SELECT id FROM user_statuses WHERE code = 'ACTIVE'",
                UUID.class
        );

        insertOrganization(organizationId, "SEARCH-" + suffix, "Search Organization", now);
        insertBranch(branchAId, organizationId, "A", "Search Branch A", now);
        insertBranch(branchBId, organizationId, "B", "Search Branch B", now);
        insertBranch(
                inaccessibleBranchId, organizationId, "C", "Inaccessible Branch", now
        );
        insertSource(phoneSourceId, organizationId, "PHONE", "PHONE");
        insertSource(walkInSourceId, organizationId, "WALK_IN", "WALK_IN");
        insertStatus(newStatusId, organizationId, "NEW", 1, true);
        insertStatus(contactedStatusId, organizationId, "CONTACTED", 2, false);
        insertUser(
                actorUserId, organizationId, activeStatusId, "actor-" + suffix, "Search Actor", now
        );
        insertUser(
                ownerUserId, organizationId, activeStatusId, "owner-" + suffix, "Search Owner", now
        );
        insertUser(
                noAccessUserId,
                organizationId,
                activeStatusId,
                "no-access-" + suffix,
                "No Access User",
                now
        );
        grantAccess(actorUserId, branchAId, actorUserId);
        grantAccess(actorUserId, branchBId, actorUserId);

        String otherSuffix = otherOrganizationId.toString().substring(0, 8);
        insertOrganization(
                otherOrganizationId,
                "OTHER-" + otherSuffix,
                "Other Search Organization",
                now
        );
        insertBranch(
                otherOrganizationBranchId,
                otherOrganizationId,
                "OTHER",
                "Other Organization Branch",
                now
        );
        insertSource(otherSourceId, otherOrganizationId, "PHONE", "PHONE");
        insertStatus(otherStatusId, otherOrganizationId, "NEW", 1, true);
        insertUser(
                otherUserId,
                otherOrganizationId,
                activeStatusId,
                "other-" + otherSuffix,
                "Other Organization User",
                now
        );

        return new Fixture(
                organizationId,
                branchAId,
                branchBId,
                inaccessibleBranchId,
                phoneSourceId,
                walkInSourceId,
                newStatusId,
                contactedStatusId,
                actorUserId,
                ownerUserId,
                noAccessUserId,
                otherOrganizationId,
                otherOrganizationBranchId,
                otherSourceId,
                otherStatusId
        );
    }

    private void insertOrganization(
            UUID id,
            String code,
            String name,
            OffsetDateTime now
    ) {
        jdbc.update(
                "INSERT INTO organizations (id, code, name, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                id, code, name, now, now
        );
    }

    private void insertBranch(
            UUID id,
            UUID organizationId,
            String code,
            String name,
            OffsetDateTime now
    ) {
        jdbc.update(
                """
                INSERT INTO branches (id, organization_id, code, name, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                id, organizationId, code, name, now, now
        );
    }

    private void insertSource(UUID id, UUID organizationId, String code, String sourceType) {
        jdbc.update(
                """
                INSERT INTO lead_sources (
                    id, organization_id, code, name, source_type, sort_order
                ) VALUES (?, ?, ?, ?, ?, 1)
                """,
                id, organizationId, code, code, sourceType
        );
    }

    private void insertStatus(
            UUID id,
            UUID organizationId,
            String code,
            int order,
            boolean initial
    ) {
        jdbc.update(
                """
                INSERT INTO lead_statuses (
                    id, organization_id, code, name, pipeline_order,
                    is_initial, is_lost, is_archived
                ) VALUES (?, ?, ?, ?, ?, ?, false, false)
                """,
                id, organizationId, code, code, order, initial
        );
    }

    private void insertUser(
            UUID id,
            UUID organizationId,
            UUID statusId,
            String username,
            String displayName,
            OffsetDateTime now
    ) {
        jdbc.update(
                """
                INSERT INTO users (
                    id, organization_id, username_normalized, display_name,
                    status_id, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                id, organizationId, username, displayName, statusId, now, now
        );
    }

    private void grantAccess(UUID userId, UUID branchId, UUID grantedBy) {
        jdbc.update(
                """
                INSERT INTO user_branch_access (user_id, branch_id, granted_by, granted_at)
                VALUES (?, ?, ?, ?)
                """,
                userId, branchId, grantedBy, OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC)
        );
    }

    private UUID insertStandardLead(Fixture fixture, UUID branchId, String guardianName) {
        return insertLead(
                fixture,
                branchId,
                fixture.phoneSourceId(),
                fixture.newStatusId(),
                null,
                guardianName,
                "+99890" + randomSevenDigits(),
                "+998 90 " + randomSevenDigits(),
                NOW.minusSeconds(30),
                NOW.plusSeconds(3600)
        );
    }

    private UUID insertLead(
            Fixture fixture,
            UUID branchId,
            UUID sourceId,
            UUID statusId,
            UUID ownerUserId,
            String guardianName,
            String normalizedPhone,
            String displayPhone,
            Instant createdAt,
            Instant dueAt
    ) {
        UUID leadId = UUID.randomUUID();
        insertLeadWithValues(
                leadId,
                branchId,
                sourceId,
                statusId,
                ownerUserId,
                guardianName,
                normalizedPhone,
                displayPhone,
                createdAt,
                dueAt
        );
        return leadId;
    }

    private void insertLeadWithId(
            Fixture fixture,
            UUID leadId,
            UUID branchId,
            String guardianName,
            Instant createdAt
    ) {
        insertLeadWithValues(
                leadId,
                branchId,
                fixture.phoneSourceId(),
                fixture.newStatusId(),
                null,
                guardianName,
                "+99890" + randomSevenDigits(),
                "+998 90 " + randomSevenDigits(),
                createdAt,
                NOW.plusSeconds(3600)
        );
    }

    private void insertLeadWithValues(
            UUID leadId,
            UUID branchId,
            UUID sourceId,
            UUID statusId,
            UUID ownerUserId,
            String guardianName,
            String normalizedPhone,
            String displayPhone,
            Instant createdAt,
            Instant dueAt
    ) {
        OffsetDateTime created = OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC);
        jdbc.update(
                """
                INSERT INTO leads (
                    id, branch_id, source_id, status_id, owner_user_id,
                    parent_or_guardian_name, first_contact_due_at,
                    created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                leadId,
                branchId,
                sourceId,
                statusId,
                ownerUserId,
                guardianName,
                OffsetDateTime.ofInstant(dueAt, ZoneOffset.UTC),
                created,
                created
        );
        jdbc.update(
                """
                INSERT INTO lead_phones (
                    id, lead_id, normalized_phone, display_phone, is_primary, created_at
                ) VALUES (?, ?, ?, ?, true, ?)
                """,
                UUID.randomUUID(),
                leadId,
                normalizedPhone,
                displayPhone,
                created
        );
    }

    private UUID insertOtherOrganizationLead(Fixture fixture) {
        return insertLead(
                fixture,
                fixture.otherOrganizationBranchId(),
                fixture.otherSourceId(),
                fixture.otherStatusId(),
                null,
                "Other Organization Guardian",
                "+998901234599",
                "+998 90 123 45 99",
                NOW.plusSeconds(60),
                NOW.plusSeconds(3600)
        );
    }

    private void insertSecondaryPhone(UUID leadId, String normalizedPhone) {
        jdbc.update(
                """
                INSERT INTO lead_phones (
                    id, lead_id, normalized_phone, display_phone, is_primary, created_at
                ) VALUES (?, ?, ?, ?, false, ?)
                """,
                UUID.randomUUID(),
                leadId,
                normalizedPhone,
                normalizedPhone,
                OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC)
        );
    }

    private static String randomSevenDigits() {
        int value = Math.floorMod(UUID.randomUUID().hashCode(), 10_000_000);
        return "%07d".formatted(value);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        CrmClock fixedCrmClock() {
            return () -> NOW;
        }
    }

    private record Fixture(
            UUID organizationId,
            UUID branchAId,
            UUID branchBId,
            UUID inaccessibleBranchId,
            UUID phoneSourceId,
            UUID walkInSourceId,
            UUID newStatusId,
            UUID contactedStatusId,
            UUID actorUserId,
            UUID ownerUserId,
            UUID noAccessUserId,
            UUID otherOrganizationId,
            UUID otherOrganizationBranchId,
            UUID otherSourceId,
            UUID otherStatusId
    ) {
    }
}
