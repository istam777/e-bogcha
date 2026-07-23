package uz.oxukids.ebogcha.crm.infrastructure.web;

import com.google.i18n.phonenumbers.PhoneNumberUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class CrmLeadApiIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17.10-alpine3.23")
                    .withDatabaseName("e_bogcha_crm_api_test")
                    .withUsername("e_bogcha_crm_api_test")
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
    void validCreationReturnsCreatedLeadAndLocation() throws Exception {
        Fixture fixture = insertFixture();
        UUID leadId = UUID.randomUUID();

        mvc.perform(post("/api/v1/crm/leads")
                        .contentType(APPLICATION_JSON)
                        .content(createJson(fixture, leadId, exampleNationalPhone())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/crm/leads/" + leadId))
                .andExpect(jsonPath("$.id").value(leadId.toString()))
                .andExpect(jsonPath("$.organizationId").value(fixture.organizationId().toString()))
                .andExpect(jsonPath("$.branchId").value(fixture.branchId().toString()))
                .andExpect(jsonPath("$.source").value("PHONE"))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.createdAt", endsWith("Z")))
                .andExpect(jsonPath("$.firstContactDueAt", endsWith("Z")))
                .andExpect(jsonPath("$.duplicateCandidateIds", hasSize(0)));
    }

    @Test
    void createdLeadCanBeRetrieved() throws Exception {
        Fixture fixture = insertFixture();
        UUID leadId = createLead(fixture, exampleNationalPhone());

        mvc.perform(get("/api/v1/crm/leads/{leadId}", leadId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(leadId.toString()))
                .andExpect(jsonPath("$.displayPhone").value(exampleNationalPhone()))
                .andExpect(jsonPath("$.ownerOperatorId").doesNotExist())
                .andExpect(jsonPath("$.lostReasonId").doesNotExist());
    }

    @Test
    void invalidPhoneReturnsBadRequestWithoutEchoingPhone() throws Exception {
        Fixture fixture = insertFixture();
        UUID leadId = UUID.randomUUID();
        String invalidPhone = "private-invalid-phone-123";

        mvc.perform(post("/api/v1/crm/leads")
                        .contentType(APPLICATION_JSON)
                        .content(createJson(fixture, leadId, invalidPhone)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CRM_REQUEST_INVALID"))
                .andExpect(content().string(not(containsString(invalidPhone))));
    }

    @Test
    void duplicateCandidatesDoNotBlockCreation() throws Exception {
        Fixture fixture = insertFixture();
        String phone = exampleNationalPhone();
        UUID firstLeadId = createLead(fixture, phone);
        UUID secondLeadId = createLead(fixture, phone);
        UUID thirdLeadId = UUID.randomUUID();
        List<UUID> expectedCandidates = List.of(firstLeadId, secondLeadId).stream()
                .sorted(Comparator.comparing(UUID::toString))
                .toList();

        mvc.perform(post("/api/v1/crm/leads")
                        .contentType(APPLICATION_JSON)
                        .content(createJson(fixture, thirdLeadId, phone)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(thirdLeadId.toString()))
                .andExpect(jsonPath("$.duplicateCandidateIds", hasSize(2)))
                .andExpect(jsonPath("$.duplicateCandidateIds[0]")
                        .value(expectedCandidates.get(0).toString()))
                .andExpect(jsonPath("$.duplicateCandidateIds[1]")
                        .value(expectedCandidates.get(1).toString()));
    }

    @Test
    void duplicateLeadIdReturnsSanitizedConflictWithoutChangingOriginalLead() throws Exception {
        Fixture fixture = insertFixture();
        UUID leadId = UUID.randomUUID();
        String originalPhone = exampleNationalPhone();
        String duplicatePhone = alternateExamplePhone();
        String originalGuardian = "Original Fictional Guardian";
        String duplicateGuardian = "Private Duplicate Guardian";

        mvc.perform(post("/api/v1/crm/leads")
                        .contentType(APPLICATION_JSON)
                        .content(createJson(fixture, leadId, originalPhone, originalGuardian)))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/crm/leads")
                        .contentType(APPLICATION_JSON)
                        .content(createJson(fixture, leadId, duplicatePhone, duplicateGuardian)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CRM_LEAD_DUPLICATE"))
                .andExpect(content().string(not(containsString(leadId.toString()))))
                .andExpect(content().string(not(containsString(originalPhone))))
                .andExpect(content().string(not(containsString(duplicatePhone))))
                .andExpect(content().string(not(containsString(originalGuardian))))
                .andExpect(content().string(not(containsString(duplicateGuardian))));

        org.assertj.core.api.Assertions.assertThat(guardianName(leadId)).isEqualTo(originalGuardian);
        org.assertj.core.api.Assertions.assertThat(displayPhone(leadId)).isEqualTo(originalPhone);
        org.assertj.core.api.Assertions.assertThat(leadPhoneCount(leadId)).isEqualTo(1);
    }

    @Test
    void operatorWithBranchAccessAcceptsLead() throws Exception {
        Fixture fixture = insertFixture();
        UUID leadId = createLead(fixture, exampleNationalPhone());

        mvc.perform(post("/api/v1/crm/leads/{leadId}/accept", leadId)
                        .header("X-Actor-User-Id", fixture.firstUserId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerOperatorId").value(fixture.firstUserId().toString()));

        org.assertj.core.api.Assertions.assertThat(ownerProjection(leadId))
                .isEqualTo(fixture.firstUserId());
    }

    @Test
    void operatorWithoutBranchAccessIsForbiddenAndDoesNotClaimLead() throws Exception {
        Fixture fixture = insertFixture();
        UUID leadId = createLead(fixture, exampleNationalPhone());

        mvc.perform(post("/api/v1/crm/leads/{leadId}/accept", leadId)
                        .header("X-Actor-User-Id", fixture.ungrantedUserId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CRM_BRANCH_ACCESS_DENIED"));

        org.assertj.core.api.Assertions.assertThat(ownerProjection(leadId)).isNull();
        org.assertj.core.api.Assertions.assertThat(activeAssignmentCount(leadId)).isZero();
    }

    @Test
    void competingOperatorReceivesConflict() throws Exception {
        Fixture fixture = insertFixture();
        UUID leadId = createLead(fixture, exampleNationalPhone());
        accept(leadId, fixture.firstUserId(), 200);

        mvc.perform(post("/api/v1/crm/leads/{leadId}/accept", leadId)
                        .header("X-Actor-User-Id", fixture.secondUserId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CRM_LEAD_ALREADY_OWNED"));
    }

    @Test
    void validStatusChangePersistsHistory() throws Exception {
        Fixture fixture = insertFixture();
        UUID leadId = createLead(fixture, exampleNationalPhone());

        mvc.perform(post("/api/v1/crm/leads/{leadId}/status-transitions", leadId)
                        .header("X-Actor-User-Id", fixture.firstUserId())
                        .contentType(APPLICATION_JSON)
                        .content("{\"targetStatus\":\"CONTACTED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONTACTED"));

        org.assertj.core.api.Assertions.assertThat(statusHistoryCount(leadId)).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(currentStatus(leadId)).isEqualTo("CONTACTED");
    }

    @Test
    void lostRequiresReasonAndSucceedsWithValidReason() throws Exception {
        Fixture fixture = insertFixture();
        UUID leadId = createLead(fixture, exampleNationalPhone());

        mvc.perform(post("/api/v1/crm/leads/{leadId}/status-transitions", leadId)
                        .header("X-Actor-User-Id", fixture.firstUserId())
                        .contentType(APPLICATION_JSON)
                        .content("{\"targetStatus\":\"LOST\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CRM_LOST_REASON_REQUIRED"));

        mvc.perform(post("/api/v1/crm/leads/{leadId}/status-transitions", leadId)
                        .header("X-Actor-User-Id", fixture.firstUserId())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"targetStatus":"LOST","lostReasonId":"%s"}
                                """.formatted(fixture.lostReasonId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOST"))
                .andExpect(jsonPath("$.lostReasonId").value(fixture.lostReasonId().toString()));
    }

    @Test
    void alreadyLostRetryWithoutReasonIsAuthorizedAndIdempotent() throws Exception {
        Fixture fixture = insertFixture();
        UUID leadId = createLead(fixture, exampleNationalPhone());
        changeToLost(leadId, fixture.firstUserId(), fixture.lostReasonId());

        mvc.perform(post("/api/v1/crm/leads/{leadId}/status-transitions", leadId)
                        .header("X-Actor-User-Id", fixture.firstUserId())
                        .contentType(APPLICATION_JSON)
                        .content("{\"targetStatus\":\"LOST\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOST"))
                .andExpect(jsonPath("$.lostReasonId").value(fixture.lostReasonId().toString()));

        org.assertj.core.api.Assertions.assertThat(statusHistoryCount(leadId)).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(lostReason(leadId))
                .isEqualTo(fixture.lostReasonId());
    }

    @Test
    void unauthorizedAlreadyLostRetryIsForbiddenAndPreservesHistory() throws Exception {
        Fixture fixture = insertFixture();
        UUID leadId = createLead(fixture, exampleNationalPhone());
        changeToLost(leadId, fixture.firstUserId(), fixture.lostReasonId());

        mvc.perform(post("/api/v1/crm/leads/{leadId}/status-transitions", leadId)
                        .header("X-Actor-User-Id", fixture.ungrantedUserId())
                        .contentType(APPLICATION_JSON)
                        .content("{\"targetStatus\":\"LOST\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CRM_BRANCH_ACCESS_DENIED"));

        org.assertj.core.api.Assertions.assertThat(currentStatus(leadId)).isEqualTo("LOST");
        org.assertj.core.api.Assertions.assertThat(statusHistoryCount(leadId)).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(lostReason(leadId))
                .isEqualTo(fixture.lostReasonId());
    }

    @Test
    void sameStatusRequestDoesNotAppendDuplicateHistory() throws Exception {
        Fixture fixture = insertFixture();
        UUID leadId = createLead(fixture, exampleNationalPhone());

        changeStatus(leadId, fixture.firstUserId(), "CONTACTED");
        changeStatus(leadId, fixture.firstUserId(), "CONTACTED");

        org.assertj.core.api.Assertions.assertThat(statusHistoryCount(leadId)).isEqualTo(1);
    }

    @Test
    void statusChangeRequiresBranchAccessBeforeWritingStatusOrHistory() throws Exception {
        Fixture fixture = insertFixture();
        UUID leadId = createLead(fixture, exampleNationalPhone());

        mvc.perform(post("/api/v1/crm/leads/{leadId}/status-transitions", leadId)
                        .header("X-Actor-User-Id", fixture.ungrantedUserId())
                        .contentType(APPLICATION_JSON)
                        .content("{\"targetStatus\":\"CONTACTED\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CRM_BRANCH_ACCESS_DENIED"));

        org.assertj.core.api.Assertions.assertThat(currentStatus(leadId)).isEqualTo("NEW");
        org.assertj.core.api.Assertions.assertThat(statusHistoryCount(leadId)).isZero();

        grantBranchAccess(
                fixture.ungrantedUserId(),
                fixture.branchId(),
                fixture.firstUserId()
        );
        changeStatus(leadId, fixture.ungrantedUserId(), "CONTACTED");

        org.assertj.core.api.Assertions.assertThat(currentStatus(leadId)).isEqualTo("CONTACTED");
        org.assertj.core.api.Assertions.assertThat(statusHistoryCount(leadId)).isEqualTo(1);
    }

    @Test
    void unauthorizedSameStatusRequestIsForbiddenWithoutHistory() throws Exception {
        Fixture fixture = insertFixture();
        UUID leadId = createLead(fixture, exampleNationalPhone());

        mvc.perform(post("/api/v1/crm/leads/{leadId}/status-transitions", leadId)
                        .header("X-Actor-User-Id", fixture.ungrantedUserId())
                        .contentType(APPLICATION_JSON)
                        .content("{\"targetStatus\":\"NEW\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CRM_BRANCH_ACCESS_DENIED"));

        org.assertj.core.api.Assertions.assertThat(currentStatus(leadId)).isEqualTo("NEW");
        org.assertj.core.api.Assertions.assertThat(statusHistoryCount(leadId)).isZero();
    }

    private Fixture insertFixture() {
        UUID organizationId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        UUID newStatusId = UUID.randomUUID();
        UUID contactedStatusId = UUID.randomUUID();
        UUID lostStatusId = UUID.randomUUID();
        UUID lostReasonId = UUID.randomUUID();
        UUID firstUserId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();
        UUID ungrantedUserId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String suffix = organizationId.toString().substring(0, 8);
        UUID activeStatusId = jdbc.queryForObject(
                "SELECT id FROM user_statuses WHERE code = 'ACTIVE'",
                UUID.class
        );

        jdbc.update(
                "INSERT INTO organizations (id, code, name, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                organizationId, "ORG-" + suffix, "Fictional API Organization", now, now
        );
        jdbc.update(
                """
                INSERT INTO branches (id, organization_id, code, name, created_at, updated_at)
                VALUES (?, ?, 'MAIN', 'Fictional Main Branch', ?, ?)
                """,
                branchId, organizationId, now, now
        );
        jdbc.update(
                """
                INSERT INTO lead_sources (id, organization_id, code, name, source_type, sort_order)
                VALUES (?, ?, 'PHONE', 'Phone', 'PHONE', 1)
                """,
                sourceId, organizationId
        );
        insertStatus(newStatusId, organizationId, "NEW", 1, true, false);
        insertStatus(contactedStatusId, organizationId, "CONTACTED", 2, false, false);
        insertStatus(lostStatusId, organizationId, "LOST", 3, false, true);
        jdbc.update(
                "INSERT INTO lost_reasons (id, organization_id, code, name) VALUES (?, ?, 'OTHER', 'Other')",
                lostReasonId, organizationId
        );
        insertUser(firstUserId, organizationId, activeStatusId, "first-" + suffix, now);
        insertUser(secondUserId, organizationId, activeStatusId, "second-" + suffix, now);
        insertUser(ungrantedUserId, organizationId, activeStatusId, "ungranted-" + suffix, now);
        grantBranchAccess(firstUserId, branchId, firstUserId);
        grantBranchAccess(secondUserId, branchId, firstUserId);
        return new Fixture(
                organizationId, branchId, lostReasonId, firstUserId, secondUserId, ungrantedUserId
        );
    }

    private void insertStatus(
            UUID id,
            UUID organizationId,
            String code,
            int order,
            boolean initial,
            boolean lost
    ) {
        jdbc.update(
                """
                INSERT INTO lead_statuses (
                    id, organization_id, code, name, pipeline_order,
                    is_initial, is_lost, is_archived
                ) VALUES (?, ?, ?, ?, ?, ?, ?, false)
                """,
                id, organizationId, code, code, order, initial, lost
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
                ) VALUES (?, ?, ?, 'Fictional API Operator', ?, ?, ?)
                """,
                id, organizationId, username, statusId, now, now
        );
    }

    private void grantBranchAccess(UUID userId, UUID branchId, UUID grantedBy) {
        jdbc.update(
                """
                INSERT INTO user_branch_access (user_id, branch_id, granted_by, granted_at)
                VALUES (?, ?, ?, ?)
                """,
                userId, branchId, grantedBy, OffsetDateTime.now(ZoneOffset.UTC)
        );
    }

    private UUID createLead(Fixture fixture, String phone) throws Exception {
        UUID leadId = UUID.randomUUID();
        mvc.perform(post("/api/v1/crm/leads")
                        .contentType(APPLICATION_JSON)
                        .content(createJson(fixture, leadId, phone)))
                .andExpect(status().isCreated());
        return leadId;
    }

    private void accept(UUID leadId, UUID actorId, int expectedStatus) throws Exception {
        mvc.perform(post("/api/v1/crm/leads/{leadId}/accept", leadId)
                        .header("X-Actor-User-Id", actorId))
                .andExpect(status().is(expectedStatus));
    }

    private void changeStatus(UUID leadId, UUID actorId, String targetStatus) throws Exception {
        mvc.perform(post("/api/v1/crm/leads/{leadId}/status-transitions", leadId)
                        .header("X-Actor-User-Id", actorId)
                        .contentType(APPLICATION_JSON)
                        .content("{\"targetStatus\":\"" + targetStatus + "\"}"))
                .andExpect(status().isOk());
    }

    private void changeToLost(UUID leadId, UUID actorId, UUID lostReasonId) throws Exception {
        mvc.perform(post("/api/v1/crm/leads/{leadId}/status-transitions", leadId)
                        .header("X-Actor-User-Id", actorId)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"targetStatus":"LOST","lostReasonId":"%s"}
                                """.formatted(lostReasonId)))
                .andExpect(status().isOk());
    }

    private String createJson(Fixture fixture, UUID leadId, String displayPhone) {
        return createJson(
                fixture, leadId, displayPhone, "Fictional API Guardian"
        );
    }

    private String createJson(
            Fixture fixture,
            UUID leadId,
            String displayPhone,
            String guardianName
    ) {
        return """
                {
                  "leadId": "%s",
                  "organizationId": "%s",
                  "branchId": "%s",
                  "source": "PHONE",
                  "parentOrGuardianName": "%s",
                  "displayPhone": "%s"
                }
                """.formatted(
                leadId,
                fixture.organizationId(),
                fixture.branchId(),
                guardianName,
                displayPhone
        );
    }

    private UUID ownerProjection(UUID leadId) {
        return jdbc.queryForObject("SELECT owner_user_id FROM leads WHERE id = ?", UUID.class, leadId);
    }

    private int activeAssignmentCount(UUID leadId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM lead_assignments WHERE lead_id = ? AND ended_at IS NULL",
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

    private int leadPhoneCount(UUID leadId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM lead_phones WHERE lead_id = ?",
                Integer.class,
                leadId
        );
    }

    private String guardianName(UUID leadId) {
        return jdbc.queryForObject(
                "SELECT parent_or_guardian_name FROM leads WHERE id = ?",
                String.class,
                leadId
        );
    }

    private String displayPhone(UUID leadId) {
        return jdbc.queryForObject(
                "SELECT display_phone FROM lead_phones WHERE lead_id = ? AND is_primary",
                String.class,
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

    private String currentStatus(UUID leadId) {
        return jdbc.queryForObject(
                """
                SELECT s.code
                  FROM leads l
                  JOIN lead_statuses s ON s.id = l.status_id
                 WHERE l.id = ?
                """,
                String.class,
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

    private static String alternateExamplePhone() {
        PhoneNumberUtil util = PhoneNumberUtil.getInstance();
        String canonical = util.format(
                util.getExampleNumber("UZ"),
                PhoneNumberUtil.PhoneNumberFormat.E164
        );
        char lastDigit = canonical.charAt(canonical.length() - 1);
        char replacement = lastDigit == '9' ? '8' : '9';
        return canonical.substring(0, canonical.length() - 1) + replacement;
    }

    private record Fixture(
            UUID organizationId,
            UUID branchId,
            UUID lostReasonId,
            UUID firstUserId,
            UUID secondUserId,
            UUID ungrantedUserId
    ) {
    }
}
