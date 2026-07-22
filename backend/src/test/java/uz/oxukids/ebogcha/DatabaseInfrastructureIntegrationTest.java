package uz.oxukids.ebogcha;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DatabaseInfrastructureIntegrationTest {

    private static final UUID ACTIVE_STATUS_ID = UUID.fromString("a27394d3-a644-4db1-a84f-eb7a3ac5fc47");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17.10-alpine3.23")
                    .withDatabaseName("e_bogcha_test")
                    .withUsername("e_bogcha_test")
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
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Flyway flyway;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void appliesOnlyTheApprovedSchemasAndSeedsThroughV13() {
        Integer connectivityCheck = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        assertThat(connectivityCheck).isEqualTo(1);

        Integer postgresMajorVersion = jdbcTemplate.queryForObject(
                "SELECT current_setting('server_version_num')::INTEGER / 10000", Integer.class);
        assertThat(postgresMajorVersion).isEqualTo(17);

        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.info().applied())
                .extracting(migration -> migration.getVersion().getVersion())
                .containsExactly(
                        "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13");

        List<String> migrationScripts = jdbcTemplate.queryForList(
                """
                SELECT script
                  FROM flyway_schema_history
                 WHERE version IS NOT NULL
                 ORDER BY installed_rank
                """,
                String.class);
        assertThat(migrationScripts).containsExactly(
                "V1__create_foundation_schema.sql",
                "V2__enforce_audit_log_immutability.sql",
                "V3__seed_foundation_reference_data.sql",
                "V4__create_core_reference_schema.sql",
                "V5__create_identity_and_staff_schema.sql",
                "V6__create_file_and_settings_schema.sql",
                "V7__create_crm_reference_schema.sql",
                "V8__create_crm_lead_core_schema.sql",
                "V9__create_crm_workflow_schema.sql",
                "V10__create_telephony_configuration_schema.sql",
                "V11__create_telephony_calls_schema.sql",
                "V12__seed_crm_and_telephony_reference_data.sql",
                "V13__seed_core_reference_data.sql");

        Integer failedMigrationCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE NOT success", Integer.class);
        assertThat(failedMigrationCount).isZero();

        Integer repeatableMigrationCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version IS NULL", Integer.class);
        assertThat(repeatableMigrationCount).isZero();

        String latestMigrationVersion = jdbcTemplate.queryForObject(
                """
                SELECT version
                  FROM flyway_schema_history
                 WHERE success AND version IS NOT NULL
                 ORDER BY installed_rank DESC
                 LIMIT 1
                """,
                String.class);
        assertThat(latestMigrationVersion).isEqualTo("13");

        List<String> foundationTables = jdbcTemplate.queryForList(
                """
                SELECT table_name
                  FROM information_schema.tables
                 WHERE table_schema = 'public'
                   AND table_type = 'BASE TABLE'
                   AND table_name <> 'flyway_schema_history'
                 ORDER BY table_name
                """,
                String.class);

        assertThat(foundationTables).containsExactly(
                "application_settings",
                "audit_logs",
                "branches",
                "call_directions",
                "call_dispositions",
                "call_event_types",
                "call_events",
                "call_participants",
                "call_recordings",
                "call_sessions",
                "departments",
                "document_types",
                "document_verification_statuses",
                "employees",
                "extensions",
                "gender_types",
                "languages",
                "lead_activities",
                "lead_activity_types",
                "lead_assignments",
                "lead_calls",
                "lead_duplicates",
                "lead_notes",
                "lead_phones",
                "lead_sources",
                "lead_status_history",
                "lead_statuses",
                "lead_task_statuses",
                "lead_tasks",
                "leads",
                "lost_reasons",
                "nationalities",
                "number_sequences",
                "organizations",
                "pbx_configs",
                "permissions",
                "phone_numbers",
                "positions",
                "prospective_children",
                "refresh_tokens",
                "relationship_types",
                "role_permissions",
                "roles",
                "sip_accounts",
                "stored_files",
                "tour_outcomes",
                "tours",
                "user_branch_access",
                "user_credentials",
                "user_roles",
                "user_statuses",
                "users",
                "webhook_events",
                "webhook_statuses");

        List<String> crmReferenceTables = jdbcTemplate.queryForList(
                """
                SELECT table_name
                  FROM information_schema.tables
                 WHERE table_schema = 'public'
                   AND table_name IN (
                       'lead_sources', 'lead_statuses', 'lost_reasons',
                       'tour_outcomes', 'lead_task_statuses', 'lead_activity_types')
                 ORDER BY table_name
                """,
                String.class);
        assertThat(crmReferenceTables).containsExactly(
                "lead_activity_types",
                "lead_sources",
                "lead_statuses",
                "lead_task_statuses",
                "lost_reasons",
                "tour_outcomes");

        List<String> crmLeadCoreTables = jdbcTemplate.queryForList(
                """
                SELECT table_name
                  FROM information_schema.tables
                 WHERE table_schema = 'public'
                   AND table_name IN ('leads', 'lead_phones', 'prospective_children')
                 ORDER BY table_name
                """,
                String.class);
        assertThat(crmLeadCoreTables)
                .containsExactly("lead_phones", "leads", "prospective_children");

        List<String> crmWorkflowTables = jdbcTemplate.queryForList(
                """
                SELECT table_name
                  FROM information_schema.tables
                 WHERE table_schema = 'public'
                   AND table_name IN (
                       'lead_assignments', 'lead_status_history', 'lead_activities',
                       'lead_notes', 'lead_tasks', 'tours', 'lead_duplicates')
                 ORDER BY table_name
                """,
                String.class);
        assertThat(crmWorkflowTables).containsExactly(
                "lead_activities",
                "lead_assignments",
                "lead_duplicates",
                "lead_notes",
                "lead_status_history",
                "lead_tasks",
                "tours");

        List<String> telephonyConfigurationTables = jdbcTemplate.queryForList(
                """
                SELECT table_name
                  FROM information_schema.tables
                 WHERE table_schema = 'public'
                   AND table_name IN (
                       'call_directions', 'call_dispositions', 'call_event_types',
                       'webhook_statuses', 'pbx_configs', 'extensions',
                       'sip_accounts', 'phone_numbers')
                 ORDER BY table_name
                """,
                String.class);
        assertThat(telephonyConfigurationTables).containsExactly(
                "call_directions",
                "call_dispositions",
                "call_event_types",
                "extensions",
                "pbx_configs",
                "phone_numbers",
                "sip_accounts",
                "webhook_statuses");

        List<String> telephonyCallTables = jdbcTemplate.queryForList(
                """
                SELECT table_name
                  FROM information_schema.tables
                 WHERE table_schema = 'public'
                   AND table_name IN (
                       'call_sessions', 'call_participants', 'call_events',
                       'call_recordings', 'lead_calls', 'webhook_events')
                 ORDER BY table_name
                """,
                String.class);
        assertThat(telephonyCallTables).containsExactly(
                "call_events",
                "call_participants",
                "call_recordings",
                "call_sessions",
                "lead_calls",
                "webhook_events");

        Integer prohibitedTableCount = jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                  FROM information_schema.tables
                 WHERE table_schema = 'public'
                   AND table_name IN (
                       'lead_conversions', 'children', 'admission_applications', 'contracts')
                """,
                Integer.class);
        assertThat(prohibitedTableCount).isZero();

        List<UserStatusSeed> userStatuses = jdbcTemplate.query(
                "SELECT id, code FROM user_statuses ORDER BY code",
                (resultSet, rowNumber) -> new UserStatusSeed(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getString("code")));

        assertThat(userStatuses).containsExactly(
                new UserStatusSeed(ACTIVE_STATUS_ID, "ACTIVE"),
                new UserStatusSeed(UUID.fromString("e199ab44-e328-4d72-84ad-807213345872"), "INACTIVE"),
                new UserStatusSeed(UUID.fromString("c41bcf72-f77b-4a26-b331-1a37b22c2166"), "LOCKED"),
                new UserStatusSeed(UUID.fromString("1f46311c-31cc-4f72-a542-075fad28373a"), "SUSPENDED"));

        Integer unseededReferenceRowCount = jdbcTemplate.queryForObject(
                """
                SELECT (SELECT count(*) FROM nationalities)
                     + (SELECT count(*) FROM lead_sources)
                     + (SELECT count(*) FROM lead_statuses)
                     + (SELECT count(*) FROM lost_reasons)
                     + (SELECT count(*) FROM tour_outcomes)
                     + (SELECT count(*) FROM leads)
                     + (SELECT count(*) FROM lead_phones)
                     + (SELECT count(*) FROM prospective_children)
                     + (SELECT count(*) FROM lead_assignments)
                     + (SELECT count(*) FROM lead_status_history)
                     + (SELECT count(*) FROM lead_activities)
                     + (SELECT count(*) FROM lead_notes)
                     + (SELECT count(*) FROM lead_tasks)
                     + (SELECT count(*) FROM tours)
                     + (SELECT count(*) FROM lead_duplicates)
                     + (SELECT count(*) FROM pbx_configs)
                     + (SELECT count(*) FROM extensions)
                     + (SELECT count(*) FROM sip_accounts)
                     + (SELECT count(*) FROM phone_numbers)
                     + (SELECT count(*) FROM call_sessions)
                     + (SELECT count(*) FROM call_participants)
                     + (SELECT count(*) FROM call_events)
                     + (SELECT count(*) FROM call_recordings)
                     + (SELECT count(*) FROM lead_calls)
                     + (SELECT count(*) FROM webhook_events)
                """,
                Integer.class);
        assertThat(unseededReferenceRowCount).isZero();

        assertThat(applicationContext.containsBean("entityManagerFactory")).isFalse();
    }

    @Test
    void recordsV12AndV13ExactlyOnceAsSuccessfulFlywayMigrations() {
        List<MigrationHistory> history = jdbcTemplate.query(
                """
                SELECT version, description, script, success
                  FROM flyway_schema_history
                 WHERE version IN ('12', '13')
                 ORDER BY version::INTEGER
                """,
                (resultSet, rowNumber) -> new MigrationHistory(
                        resultSet.getString("version"),
                        resultSet.getString("description"),
                        resultSet.getString("script"),
                        resultSet.getBoolean("success")));

        assertThat(history).containsExactly(
                new MigrationHistory(
                        "12",
                        "seed crm and telephony reference data",
                        "V12__seed_crm_and_telephony_reference_data.sql",
                        true),
                new MigrationHistory(
                        "13",
                        "seed core reference data",
                        "V13__seed_core_reference_data.sql",
                        true));
    }

    @Test
    void seedsExactlyTheApprovedGlobalReferenceData() {
        List<TaskStatusSeed> taskStatuses = jdbcTemplate.query(
                """
                SELECT id, code, name, is_closed, is_active
                  FROM lead_task_statuses
                 ORDER BY code
                """,
                (resultSet, rowNumber) -> new TaskStatusSeed(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getString("code"),
                        resultSet.getString("name"),
                        resultSet.getBoolean("is_closed"),
                        resultSet.getBoolean("is_active")));
        assertThat(taskStatuses).containsExactly(
                new TaskStatusSeed(
                        UUID.fromString("e8d7cdc6-6e26-4d7b-b995-f80a46d148eb"),
                        "CANCELLED", "Cancelled", true, true),
                new TaskStatusSeed(
                        UUID.fromString("396c77c7-c5d3-4e56-9f9a-b64019872b91"),
                        "COMPLETED", "Completed", true, true),
                new TaskStatusSeed(
                        UUID.fromString("a711f203-49d7-43fe-ae64-6b3b2b662702"),
                        "IN_PROGRESS", "In Progress", false, true),
                new TaskStatusSeed(
                        UUID.fromString("e17553cb-84e0-4f6b-9d89-daf1171800c6"),
                        "OPEN", "Open", false, true));

        List<ActiveReferenceSeed> activityTypes = jdbcTemplate.query(
                """
                SELECT id, code, name, is_active
                  FROM lead_activity_types
                 ORDER BY code
                """,
                (resultSet, rowNumber) -> new ActiveReferenceSeed(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getString("code"),
                        resultSet.getString("name"),
                        resultSet.getBoolean("is_active")));
        assertThat(activityTypes).containsExactly(
                new ActiveReferenceSeed(
                        UUID.fromString("80455b96-6b6f-4ae8-a6d8-414c5773cf55"),
                        "ASSIGNMENT", "Assignment", true),
                new ActiveReferenceSeed(
                        UUID.fromString("72af8200-7d11-448b-a40c-fd22f64dc69c"),
                        "CALL", "Call", true),
                new ActiveReferenceSeed(
                        UUID.fromString("de43e843-46cf-43d8-9e1e-0930bd3f2b79"),
                        "NOTE", "Note", true),
                new ActiveReferenceSeed(
                        UUID.fromString("7a1c856c-c25a-461d-8b21-aae646911d51"),
                        "STATUS_CHANGE", "Status Change", true),
                new ActiveReferenceSeed(
                        UUID.fromString("5a8ad0a2-8683-489f-b71f-788887c65744"),
                        "SYSTEM", "System", true),
                new ActiveReferenceSeed(
                        UUID.fromString("9928bbba-0aba-4205-b9de-6fef84ff5865"),
                        "TOUR", "Tour", true));

        assertThat(queryReferenceSeeds("call_directions")).containsExactly(
                referenceSeed("12cdc1a3-4e6a-4f6d-a825-ff8bc5fa9391", "INBOUND", "Inbound"),
                referenceSeed("440c2104-1afc-4d3d-bf60-f25f3609e8ba", "OUTBOUND", "Outbound"));

        List<FlaggedReferenceSeed> dispositions = queryFlaggedReferenceSeeds(
                "call_dispositions", "is_missed");
        assertThat(dispositions).containsExactly(
                flaggedReferenceSeed(
                        "6553de3a-4c44-485f-93b3-fb767866cb4b", "ANSWERED", "Answered", false),
                flaggedReferenceSeed(
                        "7160c287-62ae-4522-823f-74c37e3fb454", "BUSY", "Busy", false),
                flaggedReferenceSeed(
                        "c196bc93-f295-4edf-80e8-5a6a08e34f2e", "FAILED", "Failed", false),
                flaggedReferenceSeed(
                        "5f73f656-69fe-4076-a922-e66f0a09be4a", "MISSED", "Missed", true),
                flaggedReferenceSeed(
                        "e55118ed-1e56-440e-9dfb-415c96ae2533", "NO_ANSWER", "No Answer", false),
                flaggedReferenceSeed(
                        "472be4fc-cf50-4dc2-98ea-e1d51e6d141b", "REJECTED", "Rejected", false));

        assertThat(queryReferenceSeeds("call_event_types")).containsExactly(
                referenceSeed("ac5d2745-01b3-45e8-8e5b-bd55fea40889", "ANSWERED", "Answered"),
                referenceSeed("23d85d99-98db-4ff4-8fee-96573bb36579", "ENDED", "Ended"),
                referenceSeed(
                        "0123d4ef-f3a3-4efc-8d76-cfea8c345830",
                        "RECORDING_AVAILABLE", "Recording Available"),
                referenceSeed("eabb3ab9-78fb-4b04-9222-9443c8883f12", "RINGING", "Ringing"),
                referenceSeed("9fbd9a57-37ff-40bb-aaab-fa184c5260bc", "STARTED", "Started"));

        List<FlaggedReferenceSeed> webhookStatuses = queryFlaggedReferenceSeeds(
                "webhook_statuses", "is_final");
        assertThat(webhookStatuses).containsExactly(
                flaggedReferenceSeed(
                        "cc8204f8-f7a9-4db8-8cb4-87e5d53aacd8", "FAILED", "Failed", true),
                flaggedReferenceSeed(
                        "e6c3c3ae-eb91-44c2-ae6b-af1a9f67bf85", "IGNORED", "Ignored", true),
                flaggedReferenceSeed(
                        "6ec1db81-3e04-481c-b4e1-aaffeacf58d0", "PROCESSED", "Processed", true),
                flaggedReferenceSeed(
                        "cbb743e8-4f1a-4776-96fe-2523ef0c4638", "PROCESSING", "Processing", false),
                flaggedReferenceSeed(
                        "182f569e-d993-4ed8-b26c-1c4d3c333bb8", "RECEIVED", "Received", false));

        Integer totalSeedCount = jdbcTemplate.queryForObject(
                """
                SELECT (SELECT count(*) FROM lead_task_statuses)
                     + (SELECT count(*) FROM lead_activity_types)
                     + (SELECT count(*) FROM call_directions)
                     + (SELECT count(*) FROM call_dispositions)
                     + (SELECT count(*) FROM call_event_types)
                     + (SELECT count(*) FROM webhook_statuses)
                """,
                Integer.class);
        assertThat(totalSeedCount).isEqualTo(28);

        List<CoreReferenceSeed> coreReferenceSeeds = queryCoreReferenceSeeds();
        assertThat(coreReferenceSeeds).containsExactly(
                coreReferenceSeed(
                        "languages",
                        "fb5c59da-9ce9-4b1c-8093-708df6dff228",
                        "UZ",
                        "Uzbek",
                        true,
                        10,
                        null,
                        null),
                coreReferenceSeed(
                        "languages",
                        "a5743740-acbf-4356-b079-b6b9fe75f517",
                        "RU",
                        "Russian",
                        true,
                        20,
                        null,
                        null),
                coreReferenceSeed(
                        "gender_types",
                        "82cc170a-af63-4aff-8777-b59b86fd79a4",
                        "MALE",
                        "Male",
                        true,
                        null,
                        null,
                        null),
                coreReferenceSeed(
                        "gender_types",
                        "5433e6c3-5497-4d02-9390-1a0617a0cba8",
                        "FEMALE",
                        "Female",
                        true,
                        null,
                        null,
                        null),
                coreReferenceSeed(
                        "relationship_types",
                        "0021c600-8837-4088-91d6-a8d664c4101f",
                        "FATHER",
                        "Father",
                        true,
                        null,
                        null,
                        null),
                coreReferenceSeed(
                        "relationship_types",
                        "de3ccd45-2fa6-4edc-bfde-bba909cd9d82",
                        "MOTHER",
                        "Mother",
                        true,
                        null,
                        null,
                        null),
                coreReferenceSeed(
                        "relationship_types",
                        "48dba5fe-77b8-4df7-a691-3a6b77614ed8",
                        "GUARDIAN",
                        "Guardian",
                        true,
                        null,
                        null,
                        null),
                coreReferenceSeed(
                        "document_types",
                        "6c75f6d0-6ec3-4092-b859-94ee08425967",
                        "BIRTH_CERTIFICATE",
                        "Birth Certificate",
                        true,
                        null,
                        "CHILD",
                        null),
                coreReferenceSeed(
                        "document_types",
                        "a77a89b3-371f-4caf-bdcb-6d502bc3b720",
                        "PASSPORT",
                        "Passport",
                        true,
                        null,
                        "PERSON",
                        null),
                coreReferenceSeed(
                        "document_types",
                        "dd214a9e-daaa-42a6-887f-a2f107cf3e2f",
                        "ID_CARD",
                        "ID Card",
                        true,
                        null,
                        "PERSON",
                        null),
                coreReferenceSeed(
                        "document_types",
                        "8905ff1c-b3e2-406b-85bc-eed1e27dad02",
                        "PINFL",
                        "PINFL",
                        true,
                        null,
                        "PERSON",
                        null),
                coreReferenceSeed(
                        "document_types",
                        "7642acca-0b62-4a06-b9be-dcc573391ba5",
                        "MEDICAL_CERTIFICATE",
                        "Medical Certificate",
                        true,
                        null,
                        "CHILD",
                        null),
                coreReferenceSeed(
                        "document_types",
                        "804ce20a-6653-4ac5-8e4a-86f8bbcc544d",
                        "PHOTO",
                        "Photo",
                        true,
                        null,
                        "PERSON",
                        null),
                coreReferenceSeed(
                        "document_verification_statuses",
                        "0c412202-9976-49d0-96e5-c010c5caf731",
                        "PENDING",
                        "Pending",
                        true,
                        null,
                        null,
                        false),
                coreReferenceSeed(
                        "document_verification_statuses",
                        "40e8acf6-186a-4bc5-b050-0ab076032f20",
                        "VERIFIED",
                        "Verified",
                        true,
                        null,
                        null,
                        true),
                coreReferenceSeed(
                        "document_verification_statuses",
                        "90a8cec4-b402-466d-a6d5-4474517d9312",
                        "REJECTED",
                        "Rejected",
                        true,
                        null,
                        null,
                        true));

        assertThat(coreReferenceSeeds).hasSize(16).allSatisfy(seed -> {
            assertThat(seed.id().version()).isEqualTo(4);
            assertThat(seed.id().variant()).isEqualTo(2);
            assertThat(seed.active()).isTrue();
        });
        assertThat(coreReferenceSeeds)
                .extracting(CoreReferenceSeed::id)
                .doesNotHaveDuplicates();
        assertThat(coreReferenceSeeds)
                .extracting(seed -> seed.tableName() + "|" + seed.code())
                .doesNotHaveDuplicates();
        assertThat(coreReferenceSeeds).filteredOn(seed -> seed.tableName().equals("languages"))
                .hasSize(2);
        assertThat(coreReferenceSeeds).filteredOn(seed -> seed.tableName().equals("gender_types"))
                .hasSize(2);
        assertThat(coreReferenceSeeds)
                .filteredOn(seed -> seed.tableName().equals("relationship_types"))
                .hasSize(3);
        assertThat(coreReferenceSeeds)
                .filteredOn(seed -> seed.tableName().equals("document_types"))
                .hasSize(6);
        assertThat(coreReferenceSeeds)
                .filteredOn(seed -> seed.tableName().equals("document_verification_statuses"))
                .hasSize(3);
    }

    @Test
    void rejectsConflictsWithEveryApprovedReferenceCodeAndAllowsNewCodes() {
        for (ApprovedReference approvedReference : approvedReferences()) {
            String additionalCode = "V12_"
                    + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
            try {
                assertSqlState(
                        "23505",
                        () -> insertGlobalReference(
                                approvedReference.tableName(),
                                UUID.randomUUID(),
                                approvedReference.code()));
                insertGlobalReference(
                        approvedReference.tableName(), UUID.randomUUID(), additionalCode);

                Integer additionalRowCount = jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM "
                                + validatedGlobalReferenceTable(approvedReference.tableName())
                                + " WHERE code = ?",
                        Integer.class,
                        additionalCode);
                assertThat(additionalRowCount).isEqualTo(1);
            } finally {
                jdbcTemplate.update(
                        "DELETE FROM "
                                + validatedGlobalReferenceTable(approvedReference.tableName())
                                + " WHERE code = ?",
                        additionalCode);
            }
        }
    }

    @Test
    void v12AndV13UseOnlyStrictExplicitInsertStatementsAndFixedUuids() throws Exception {
        String migration = new ClassPathResource(
                        "db/migration/V12__seed_crm_and_telephony_reference_data.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        List<String> statements = Arrays.stream(migration.split(";"))
                .map(String::trim)
                .filter(statement -> !statement.isEmpty())
                .toList();

        assertThat(statements).hasSize(6).allMatch(statement ->
                statement.regionMatches(true, 0, "INSERT INTO", 0, "INSERT INTO".length()));
        assertThat(migration).contains(
                "INSERT INTO lead_task_statuses (id, code, name, is_closed, is_active)",
                "INSERT INTO lead_activity_types (id, code, name, is_active)",
                "INSERT INTO call_directions (id, code, name)",
                "INSERT INTO call_dispositions (id, code, name, is_missed)",
                "INSERT INTO call_event_types (id, code, name)",
                "INSERT INTO webhook_statuses (id, code, name, is_final)");
        List<String> targetTables = Pattern.compile("(?i)INSERT\\s+INTO\\s+([a-z_]+)")
                .matcher(migration)
                .results()
                .map(result -> result.group(1).toLowerCase())
                .toList();
        assertThat(targetTables).containsExactly(
                "lead_task_statuses",
                "lead_activity_types",
                "call_directions",
                "call_dispositions",
                "call_event_types",
                "webhook_statuses");
        assertThat(migration.toUpperCase()).doesNotContain(
                "ON CONFLICT",
                "WHERE NOT EXISTS",
                "CREATE ",
                "ALTER ",
                "DROP ",
                "TRUNCATE ",
                "UPDATE ",
                "DELETE ",
                "GEN_RANDOM_UUID",
                "UUID_GENERATE");
        assertThat(migration.toLowerCase()).doesNotContain(
                "organization_id",
                "branch_id",
                "credential",
                "endpoint",
                "phone_number",
                "username",
                "token",
                "secret",
                "signature",
                "recording_url",
                "http://",
                "https://");

        Matcher uuidMatcher = Pattern.compile(
                        "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
                .matcher(migration);
        List<String> uuids = uuidMatcher.results().map(result -> result.group()).toList();
        assertThat(uuids).hasSize(28).doesNotHaveDuplicates();

        String v13Migration = new ClassPathResource(
                        "db/migration/V13__seed_core_reference_data.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        List<String> v13Statements = Arrays.stream(v13Migration.split(";"))
                .map(String::trim)
                .filter(statement -> !statement.isEmpty())
                .toList();

        assertThat(v13Statements).hasSize(5).allMatch(statement ->
                statement.regionMatches(true, 0, "INSERT INTO", 0, "INSERT INTO".length()));
        List<String> v13TargetTables = Pattern.compile("(?i)INSERT\\s+INTO\\s+([a-z_]+)")
                .matcher(v13Migration)
                .results()
                .map(result -> result.group(1).toLowerCase())
                .toList();
        assertThat(v13TargetTables).containsExactly(
                "languages",
                "gender_types",
                "relationship_types",
                "document_types",
                "document_verification_statuses");

        String normalizedV13Migration = v13Migration.replaceAll("\\s+", " ");
        assertThat(normalizedV13Migration).contains(
                "INSERT INTO languages ( id, code, name, is_active, sort_order )",
                "INSERT INTO gender_types ( id, code, name, is_active )",
                "INSERT INTO relationship_types ( id, code, name, is_active )",
                "INSERT INTO document_types ( id, code, name, applies_to, is_active )",
                "INSERT INTO document_verification_statuses ( id, code, name, is_final, is_active )");
        assertThat(v13Migration.toUpperCase()).doesNotContain(
                "ON CONFLICT",
                "MERGE ",
                "WHERE NOT EXISTS",
                "CREATE ",
                "ALTER ",
                "DROP ",
                "TRUNCATE ",
                "UPDATE ",
                "DELETE ",
                "SELECT ",
                "DO ",
                "BEGIN",
                "EXCEPTION",
                "TEMPORARY ",
                "GEN_RANDOM_UUID",
                "UUID_GENERATE");
        assertThat(v13Migration.toLowerCase()).doesNotContain(
                "nationalities",
                "organization_id",
                "branch_id",
                "lead_sources",
                "lead_statuses",
                "lost_reasons",
                "tour_outcomes",
                "pbx_configs",
                "sip_accounts");

        Matcher v13UuidMatcher = Pattern.compile(
                        "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
                .matcher(v13Migration);
        List<String> v13Uuids = v13UuidMatcher.results().map(result -> result.group()).toList();
        assertThat(v13Uuids).hasSize(16).doesNotHaveDuplicates();
    }

    @Test
    void v12FailsStrictlyWhenAnApprovedCodeAlreadyHasAnotherIdentity() {
        String schemaName = "v12_conflict_" + UUID.randomUUID().toString().replace("-", "");
        Flyway throughV11 = flywayForSchema(schemaName, MigrationVersion.fromVersion("11"));

        try {
            throughV11.migrate();
            UUID conflictingId = UUID.randomUUID();
            jdbcTemplate.update(
                    "INSERT INTO " + schemaName
                            + ".lead_task_statuses (id, code, name)"
                            + " VALUES (?, 'OPEN', 'Conflicting pre-V12 identity')",
                    conflictingId);

            Flyway throughV12 = flywayForSchema(schemaName, MigrationVersion.fromVersion("12"));
            assertThatThrownBy(throughV12::migrate)
                    .isInstanceOfSatisfying(
                            FlywayException.class,
                            exception -> assertThat(causeChainContainsSqlState(exception, "23505"))
                                    .as("Flyway failure cause chain contains PostgreSQL unique-violation SQLSTATE")
                                    .isTrue());

            Map<String, Object> preservedConflict = jdbcTemplate.queryForMap(
                    "SELECT id, name FROM " + schemaName
                            + ".lead_task_statuses WHERE code = 'OPEN'");
            assertThat(preservedConflict.get("id")).isEqualTo(conflictingId);
            assertThat(preservedConflict.get("name")).isEqualTo("Conflicting pre-V12 identity");

            Integer appliedV12Count = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM " + schemaName
                            + ".flyway_schema_history WHERE version = '12' AND success",
                    Integer.class);
            assertThat(appliedV12Count).isZero();
        } finally {
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schemaName + " CASCADE");
        }
    }

    @Test
    void v13FailsStrictlyWhenApprovedLanguageCodeAlreadyHasAnotherIdentity() {
        String schemaName = "v13_conflict_" + UUID.randomUUID().toString().replace("-", "");
        Flyway throughV12 = flywayForSchema(schemaName, MigrationVersion.fromVersion("12"));

        try {
            throughV12.migrate();
            Integer appliedV12Count = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM " + schemaName
                            + ".flyway_schema_history WHERE version = '12' AND success",
                    Integer.class);
            assertThat(appliedV12Count).isEqualTo(1);

            UUID conflictingId = UUID.randomUUID();
            jdbcTemplate.update(
                    "INSERT INTO " + schemaName
                            + ".languages (id, code, name, is_active, sort_order)"
                            + " VALUES (?, 'UZ', 'Disposable conflicting pre-V13 language', FALSE, 999)",
                    conflictingId);

            Flyway throughV13 = flywayForSchema(schemaName, MigrationVersion.fromVersion("13"));
            assertThatThrownBy(throughV13::migrate)
                    .isInstanceOfSatisfying(
                            FlywayException.class,
                            exception -> assertThat(causeChainContainsSqlState(exception, "23505"))
                                    .as("Flyway failure cause chain contains PostgreSQL unique-violation SQLSTATE")
                                    .isTrue());

            Map<String, Object> preservedConflict = jdbcTemplate.queryForMap(
                    "SELECT id, name, is_active, sort_order FROM " + schemaName
                            + ".languages WHERE code = 'UZ'");
            assertThat(preservedConflict.get("id")).isEqualTo(conflictingId);
            assertThat(preservedConflict.get("name"))
                    .isEqualTo("Disposable conflicting pre-V13 language");
            assertThat(preservedConflict.get("is_active")).isEqualTo(false);
            assertThat(preservedConflict.get("sort_order")).isEqualTo(999);

            Integer approvedUzCount = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM " + schemaName
                            + ".languages WHERE id = 'fb5c59da-9ce9-4b1c-8093-708df6dff228'",
                    Integer.class);
            assertThat(approvedUzCount).isZero();

            Integer appliedV13Count = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM " + schemaName
                            + ".flyway_schema_history WHERE version = '13' AND success",
                    Integer.class);
            assertThat(appliedV13Count).isZero();

            Map<String, Object> primarySchemaUz = jdbcTemplate.queryForMap(
                    "SELECT id, name, is_active, sort_order FROM languages WHERE code = 'UZ'");
            assertThat(primarySchemaUz.get("id"))
                    .isEqualTo(UUID.fromString("fb5c59da-9ce9-4b1c-8093-708df6dff228"));
            assertThat(primarySchemaUz.get("name")).isEqualTo("Uzbek");
            assertThat(primarySchemaUz.get("is_active")).isEqualTo(true);
            assertThat(primarySchemaUz.get("sort_order")).isEqualTo(10);
        } finally {
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schemaName + " CASCADE");
        }
    }

    @Test
    void definesExactlyTheApprovedRestrictiveForeignKeys() {
        List<ForeignKeyMetadata> actualForeignKeys = jdbcTemplate.query(
                """
                SELECT source_table.relname AS source_table,
                       source_attribute.attname AS source_column,
                       target_table.relname AS target_table,
                       target_attribute.attname AS target_column,
                       CASE constraint_definition.confdeltype
                           WHEN 'r' THEN 'RESTRICT'
                           WHEN 'a' THEN 'NO ACTION'
                           WHEN 'c' THEN 'CASCADE'
                           WHEN 'n' THEN 'SET NULL'
                           WHEN 'd' THEN 'SET DEFAULT'
                       END AS delete_action,
                       CASE constraint_definition.confupdtype
                           WHEN 'r' THEN 'RESTRICT'
                           WHEN 'a' THEN 'NO ACTION'
                           WHEN 'c' THEN 'CASCADE'
                           WHEN 'n' THEN 'SET NULL'
                           WHEN 'd' THEN 'SET DEFAULT'
                       END AS update_action
                  FROM pg_constraint constraint_definition
                  JOIN pg_class source_table
                    ON source_table.oid = constraint_definition.conrelid
                  JOIN pg_namespace source_schema
                    ON source_schema.oid = source_table.relnamespace
                  JOIN pg_class target_table
                    ON target_table.oid = constraint_definition.confrelid
                  JOIN LATERAL unnest(constraint_definition.conkey) WITH ORDINALITY
                       AS source_key(attribute_number, ordinal_position) ON TRUE
                  JOIN LATERAL unnest(constraint_definition.confkey) WITH ORDINALITY
                       AS target_key(attribute_number, ordinal_position)
                    ON target_key.ordinal_position = source_key.ordinal_position
                  JOIN pg_attribute source_attribute
                    ON source_attribute.attrelid = source_table.oid
                   AND source_attribute.attnum = source_key.attribute_number
                  JOIN pg_attribute target_attribute
                    ON target_attribute.attrelid = target_table.oid
                   AND target_attribute.attnum = target_key.attribute_number
                 WHERE constraint_definition.contype = 'f'
                   AND source_schema.nspname = 'public'
                   AND source_table.relname IN (
                       'organizations', 'branches', 'user_statuses', 'users', 'roles',
                       'permissions', 'role_permissions', 'user_roles', 'audit_logs',
                       'departments', 'positions', 'user_credentials', 'employees',
                       'user_branch_access', 'refresh_tokens', 'languages', 'nationalities',
                       'gender_types', 'relationship_types', 'document_types',
                       'document_verification_statuses', 'stored_files',
                       'application_settings', 'number_sequences', 'lead_sources',
                       'lead_statuses', 'lost_reasons', 'tour_outcomes',
                       'lead_task_statuses', 'lead_activity_types', 'leads',
                       'lead_phones', 'prospective_children', 'lead_assignments',
                       'lead_status_history', 'lead_activities', 'lead_notes',
                       'lead_tasks', 'tours', 'lead_duplicates', 'pbx_configs',
                       'extensions', 'sip_accounts', 'phone_numbers',
                       'call_directions', 'call_dispositions', 'call_event_types',
                       'webhook_statuses', 'call_sessions', 'call_participants',
                       'call_events', 'call_recordings', 'lead_calls', 'webhook_events')
                """,
                (resultSet, rowNumber) -> new ForeignKeyMetadata(
                        resultSet.getString("source_table"),
                        resultSet.getString("source_column"),
                        resultSet.getString("target_table"),
                        resultSet.getString("target_column"),
                        resultSet.getString("delete_action"),
                        resultSet.getString("update_action")));

        assertThat(actualForeignKeys).containsExactlyInAnyOrderElementsOf(expectedForeignKeys());
    }

    @Test
    void providesLeadingBtreeIndexCoverageForEveryForeignKey() {
        List<ForeignKeyIndexCoverage> actualCoverage = jdbcTemplate.query(
                """
                SELECT DISTINCT source_table.relname AS source_table,
                                source_attribute.attname AS source_column,
                       EXISTS (
                           SELECT 1
                             FROM pg_index index_definition
                             JOIN pg_class index_relation
                               ON index_relation.oid = index_definition.indexrelid
                             JOIN pg_am access_method
                               ON access_method.oid = index_relation.relam
                            WHERE index_definition.indrelid = source_table.oid
                              AND index_definition.indisvalid
                              AND index_definition.indisready
                              AND index_definition.indpred IS NULL
                              AND index_definition.indnkeyatts > 0
                              AND index_definition.indkey[0] = source_attribute.attnum
                              AND access_method.amname = 'btree'
                       ) AS covered
                  FROM pg_constraint constraint_definition
                  JOIN pg_class source_table
                    ON source_table.oid = constraint_definition.conrelid
                  JOIN pg_namespace source_schema
                    ON source_schema.oid = source_table.relnamespace
                  JOIN LATERAL unnest(constraint_definition.conkey)
                       AS source_key(attribute_number) ON TRUE
                  JOIN pg_attribute source_attribute
                    ON source_attribute.attrelid = source_table.oid
                   AND source_attribute.attnum = source_key.attribute_number
                 WHERE constraint_definition.contype = 'f'
                   AND source_schema.nspname = 'public'
                   AND source_table.relname IN (
                       'organizations', 'branches', 'user_statuses', 'users', 'roles',
                       'permissions', 'role_permissions', 'user_roles', 'audit_logs',
                       'departments', 'positions', 'user_credentials', 'employees',
                       'user_branch_access', 'refresh_tokens', 'languages', 'nationalities',
                       'gender_types', 'relationship_types', 'document_types',
                       'document_verification_statuses', 'stored_files',
                       'application_settings', 'number_sequences', 'lead_sources',
                       'lead_statuses', 'lost_reasons', 'tour_outcomes',
                       'lead_task_statuses', 'lead_activity_types', 'leads',
                       'lead_phones', 'prospective_children', 'lead_assignments',
                       'lead_status_history', 'lead_activities', 'lead_notes',
                       'lead_tasks', 'tours', 'lead_duplicates', 'pbx_configs',
                       'extensions', 'sip_accounts', 'phone_numbers', 'call_sessions',
                       'call_participants', 'call_events', 'call_recordings',
                       'lead_calls', 'webhook_events')
                """,
                (resultSet, rowNumber) -> new ForeignKeyIndexCoverage(
                        resultSet.getString("source_table"),
                        resultSet.getString("source_column"),
                        resultSet.getBoolean("covered")));

        List<ForeignKeyIndexCoverage> expectedCoverage = expectedForeignKeys().stream()
                .map(foreignKey -> new ForeignKeyIndexCoverage(
                        foreignKey.sourceTable(), foreignKey.sourceColumn(), true))
                .toList();

        assertThat(actualCoverage).containsExactlyInAnyOrderElementsOf(expectedCoverage);
    }

    @Test
    void definesExactRemainingCoreColumnMetadata() {
        List<ColumnMetadata> actualColumns = jdbcTemplate.query(
                """
                SELECT table_name,
                       column_name,
                       data_type,
                       character_maximum_length,
                       is_nullable,
                       column_default
                  FROM information_schema.columns
                 WHERE table_schema = 'public'
                   AND table_name IN (
                       'departments', 'positions', 'user_credentials', 'employees',
                       'user_branch_access', 'refresh_tokens', 'languages', 'nationalities',
                       'gender_types', 'relationship_types', 'document_types',
                       'document_verification_statuses', 'stored_files',
                       'application_settings', 'number_sequences')
                """,
                (resultSet, rowNumber) -> new ColumnMetadata(
                        resultSet.getString("table_name"),
                        resultSet.getString("column_name"),
                        resultSet.getString("data_type"),
                        resultSet.getObject("character_maximum_length", Integer.class),
                        resultSet.getString("is_nullable").equals("YES"),
                        resultSet.getString("column_default")));

        assertThat(actualColumns).containsExactlyInAnyOrderElementsOf(expectedRemainingColumns());
    }

    @Test
    void definesExactRemainingCorePrimaryAndUniqueKeys() {
        List<KeyMetadata> actualKeys = jdbcTemplate.query(
                """
                SELECT table_relation.relname AS table_name,
                       CASE constraint_definition.contype
                           WHEN 'p' THEN 'PRIMARY KEY'
                           WHEN 'u' THEN 'UNIQUE'
                       END AS key_type,
                       string_agg(attribute.attname, ',' ORDER BY key_column.ordinal_position)
                           AS key_columns,
                       index_definition.indnullsnotdistinct,
                       access_method.amname AS access_method
                  FROM pg_constraint constraint_definition
                  JOIN pg_class table_relation
                    ON table_relation.oid = constraint_definition.conrelid
                  JOIN pg_namespace table_schema
                    ON table_schema.oid = table_relation.relnamespace
                  JOIN pg_index index_definition
                    ON index_definition.indexrelid = constraint_definition.conindid
                  JOIN pg_class index_relation
                    ON index_relation.oid = index_definition.indexrelid
                  JOIN pg_am access_method
                    ON access_method.oid = index_relation.relam
                  JOIN LATERAL unnest(constraint_definition.conkey) WITH ORDINALITY
                       AS key_column(attribute_number, ordinal_position) ON TRUE
                  JOIN pg_attribute attribute
                    ON attribute.attrelid = table_relation.oid
                   AND attribute.attnum = key_column.attribute_number
                 WHERE table_schema.nspname = 'public'
                   AND constraint_definition.contype IN ('p', 'u')
                   AND table_relation.relname IN (
                       'departments', 'positions', 'user_credentials', 'employees',
                       'user_branch_access', 'refresh_tokens', 'languages', 'nationalities',
                       'gender_types', 'relationship_types', 'document_types',
                       'document_verification_statuses', 'stored_files',
                       'application_settings', 'number_sequences')
                 GROUP BY table_relation.relname,
                          constraint_definition.oid,
                          constraint_definition.contype,
                          index_definition.indnullsnotdistinct,
                          access_method.amname
                """,
                (resultSet, rowNumber) -> new KeyMetadata(
                        resultSet.getString("table_name"),
                        resultSet.getString("key_type"),
                        resultSet.getString("key_columns"),
                        resultSet.getBoolean("indnullsnotdistinct"),
                        resultSet.getString("access_method")));

        assertThat(actualKeys).containsExactlyInAnyOrderElementsOf(expectedRemainingKeys());
    }

    @Test
    void hasExactCumulativeConstraintCounts() {
        ConstraintCounts constraintCounts = jdbcTemplate.queryForObject(
                """
                SELECT count(*) FILTER (WHERE constraint_definition.contype = 'p') AS primary_keys,
                       count(*) FILTER (WHERE constraint_definition.contype = 'u') AS unique_constraints,
                       count(*) FILTER (WHERE constraint_definition.contype = 'f') AS foreign_keys,
                       count(*) FILTER (WHERE constraint_definition.contype = 'c') AS check_constraints
                  FROM pg_constraint constraint_definition
                  JOIN pg_class table_relation
                    ON table_relation.oid = constraint_definition.conrelid
                  JOIN pg_namespace table_schema
                    ON table_schema.oid = table_relation.relnamespace
                 WHERE table_schema.nspname = 'public'
                   AND table_relation.relname IN (
                       'organizations', 'branches', 'user_statuses', 'users', 'roles',
                       'permissions', 'role_permissions', 'user_roles', 'audit_logs',
                       'departments', 'positions', 'user_credentials', 'employees',
                       'user_branch_access', 'refresh_tokens', 'languages', 'nationalities',
                       'gender_types', 'relationship_types', 'document_types',
                       'document_verification_statuses', 'stored_files',
                       'application_settings', 'number_sequences', 'lead_sources',
                       'lead_statuses', 'lost_reasons', 'tour_outcomes',
                       'lead_task_statuses', 'lead_activity_types', 'leads',
                       'lead_phones', 'prospective_children', 'lead_assignments',
                       'lead_status_history', 'lead_activities', 'lead_notes',
                       'lead_tasks', 'tours', 'lead_duplicates', 'pbx_configs',
                       'extensions', 'sip_accounts', 'phone_numbers',
                       'call_directions', 'call_dispositions', 'call_event_types',
                       'webhook_statuses', 'call_sessions', 'call_participants',
                       'call_events', 'call_recordings', 'lead_calls', 'webhook_events')
                """,
                (resultSet, rowNumber) -> new ConstraintCounts(
                        resultSet.getInt("primary_keys"),
                        resultSet.getInt("unique_constraints"),
                        resultSet.getInt("foreign_keys"),
                        resultSet.getInt("check_constraints")));

        assertThat(constraintCounts).isEqualTo(new ConstraintCounts(54, 37, 98, 1));
    }

    @Test
    void definesTheCurrentUserRoleIndexWithApprovedPostgresSemantics() {
        List<PartialUniqueIndexMetadata> indexes = jdbcTemplate.query(
                """
                SELECT index_definition.indisunique,
                       access_method.amname AS access_method,
                       index_definition.indnullsnotdistinct,
                       index_definition.indnkeyatts,
                       pg_get_indexdef(index_definition.indexrelid, 1, TRUE) AS first_key,
                       pg_get_indexdef(index_definition.indexrelid, 2, TRUE) AS second_key,
                       pg_get_indexdef(index_definition.indexrelid, 3, TRUE) AS third_key,
                       pg_get_expr(index_definition.indpred, index_definition.indrelid) AS predicate
                  FROM pg_index index_definition
                  JOIN pg_class index_relation
                    ON index_relation.oid = index_definition.indexrelid
                  JOIN pg_class table_relation
                    ON table_relation.oid = index_definition.indrelid
                  JOIN pg_namespace table_schema
                    ON table_schema.oid = table_relation.relnamespace
                  JOIN pg_am access_method
                    ON access_method.oid = index_relation.relam
                 WHERE table_schema.nspname = 'public'
                   AND table_relation.relname = 'user_roles'
                   AND index_relation.relname = 'ux_user_roles_current_assignment'
                """,
                (resultSet, rowNumber) -> new PartialUniqueIndexMetadata(
                        resultSet.getBoolean("indisunique"),
                        resultSet.getString("access_method"),
                        resultSet.getBoolean("indnullsnotdistinct"),
                        resultSet.getInt("indnkeyatts"),
                        resultSet.getString("first_key"),
                        resultSet.getString("second_key"),
                        resultSet.getString("third_key"),
                        resultSet.getString("predicate")));

        assertThat(indexes).hasSize(1);
        PartialUniqueIndexMetadata index = indexes.getFirst();
        assertThat(index.unique()).isTrue();
        assertThat(index.accessMethod()).isEqualTo("btree");
        assertThat(index.nullsNotDistinct()).isTrue();
        assertThat(index.keyCount()).isEqualTo(3);
        assertThat(List.of(index.firstKey(), index.secondKey(), index.thirdKey()))
                .containsExactly("user_id", "role_id", "branch_id");
        assertThat(index.predicate().replaceAll("[()\\s]", ""))
                .isEqualTo("valid_untilISNULL");
    }

    @Test
    void definesExactCrmReferenceColumnMetadata() {
        List<ColumnMetadata> actualColumns = jdbcTemplate.query(
                """
                SELECT table_name,
                       column_name,
                       data_type,
                       character_maximum_length,
                       is_nullable,
                       column_default
                  FROM information_schema.columns
                 WHERE table_schema = 'public'
                   AND table_name IN (
                       'lead_sources', 'lead_statuses', 'lost_reasons',
                       'tour_outcomes', 'lead_task_statuses', 'lead_activity_types')
                """,
                (resultSet, rowNumber) -> new ColumnMetadata(
                        resultSet.getString("table_name"),
                        resultSet.getString("column_name"),
                        resultSet.getString("data_type"),
                        resultSet.getObject("character_maximum_length", Integer.class),
                        resultSet.getString("is_nullable").equals("YES"),
                        resultSet.getString("column_default")));

        assertThat(actualColumns).containsExactlyInAnyOrderElementsOf(expectedCrmReferenceColumns());
    }

    @Test
    void definesExactCrmReferenceKeysAndConstraintCounts() {
        List<KeyMetadata> actualKeys = jdbcTemplate.query(
                """
                SELECT table_relation.relname AS table_name,
                       CASE constraint_definition.contype
                           WHEN 'p' THEN 'PRIMARY KEY'
                           WHEN 'u' THEN 'UNIQUE'
                       END AS key_type,
                       string_agg(attribute.attname, ',' ORDER BY key_column.ordinal_position)
                           AS key_columns,
                       index_definition.indnullsnotdistinct,
                       access_method.amname AS access_method
                  FROM pg_constraint constraint_definition
                  JOIN pg_class table_relation
                    ON table_relation.oid = constraint_definition.conrelid
                  JOIN pg_namespace table_schema
                    ON table_schema.oid = table_relation.relnamespace
                  JOIN pg_index index_definition
                    ON index_definition.indexrelid = constraint_definition.conindid
                  JOIN pg_class index_relation
                    ON index_relation.oid = index_definition.indexrelid
                  JOIN pg_am access_method
                    ON access_method.oid = index_relation.relam
                  JOIN LATERAL unnest(constraint_definition.conkey) WITH ORDINALITY
                       AS key_column(attribute_number, ordinal_position) ON TRUE
                  JOIN pg_attribute attribute
                    ON attribute.attrelid = table_relation.oid
                   AND attribute.attnum = key_column.attribute_number
                 WHERE table_schema.nspname = 'public'
                   AND constraint_definition.contype IN ('p', 'u')
                   AND table_relation.relname IN (
                       'lead_sources', 'lead_statuses', 'lost_reasons',
                       'tour_outcomes', 'lead_task_statuses', 'lead_activity_types')
                 GROUP BY table_relation.relname,
                          constraint_definition.oid,
                          constraint_definition.contype,
                          index_definition.indnullsnotdistinct,
                          access_method.amname
                """,
                (resultSet, rowNumber) -> new KeyMetadata(
                        resultSet.getString("table_name"),
                        resultSet.getString("key_type"),
                        resultSet.getString("key_columns"),
                        resultSet.getBoolean("indnullsnotdistinct"),
                        resultSet.getString("access_method")));

        assertThat(actualKeys).containsExactlyInAnyOrderElementsOf(expectedCrmReferenceKeys());

        ConstraintCounts constraintCounts = jdbcTemplate.queryForObject(
                """
                SELECT count(*) FILTER (WHERE constraint_definition.contype = 'p') AS primary_keys,
                       count(*) FILTER (WHERE constraint_definition.contype = 'u') AS unique_constraints,
                       count(*) FILTER (WHERE constraint_definition.contype = 'f') AS foreign_keys,
                       count(*) FILTER (WHERE constraint_definition.contype = 'c') AS check_constraints
                  FROM pg_constraint constraint_definition
                  JOIN pg_class table_relation
                    ON table_relation.oid = constraint_definition.conrelid
                  JOIN pg_namespace table_schema
                    ON table_schema.oid = table_relation.relnamespace
                 WHERE table_schema.nspname = 'public'
                   AND table_relation.relname IN (
                       'lead_sources', 'lead_statuses', 'lost_reasons',
                       'tour_outcomes', 'lead_task_statuses', 'lead_activity_types')
                """,
                (resultSet, rowNumber) -> new ConstraintCounts(
                        resultSet.getInt("primary_keys"),
                        resultSet.getInt("unique_constraints"),
                        resultSet.getInt("foreign_keys"),
                        resultSet.getInt("check_constraints")));
        assertThat(constraintCounts).isEqualTo(new ConstraintCounts(6, 6, 4, 0));
    }

    @Test
    void providesExactLeadingBtreeIndexesForCrmReferenceForeignKeys() {
        List<ForeignKeyIndexMetadata> indexes = jdbcTemplate.query(
                """
                SELECT table_relation.relname AS table_name,
                       index_relation.relname AS index_name,
                       pg_get_indexdef(index_definition.indexrelid, 1, TRUE) AS first_key,
                       access_method.amname AS access_method,
                       index_definition.indisvalid,
                       index_definition.indisready,
                       index_definition.indpred IS NOT NULL AS partial
                  FROM pg_index index_definition
                  JOIN pg_class index_relation
                    ON index_relation.oid = index_definition.indexrelid
                  JOIN pg_class table_relation
                    ON table_relation.oid = index_definition.indrelid
                  JOIN pg_namespace table_schema
                    ON table_schema.oid = table_relation.relnamespace
                  JOIN pg_am access_method
                    ON access_method.oid = index_relation.relam
                 WHERE table_schema.nspname = 'public'
                   AND index_relation.relname IN (
                       'idx_lead_sources_organization_id',
                       'idx_lead_statuses_organization_id',
                       'idx_lost_reasons_organization_id',
                       'idx_tour_outcomes_organization_id')
                """,
                (resultSet, rowNumber) -> new ForeignKeyIndexMetadata(
                        resultSet.getString("table_name"),
                        resultSet.getString("index_name"),
                        resultSet.getString("first_key"),
                        resultSet.getString("access_method"),
                        resultSet.getBoolean("indisvalid"),
                        resultSet.getBoolean("indisready"),
                        resultSet.getBoolean("partial")));

        assertThat(indexes).containsExactlyInAnyOrder(
                foreignKeyIndex("lead_sources", "idx_lead_sources_organization_id"),
                foreignKeyIndex("lead_statuses", "idx_lead_statuses_organization_id"),
                foreignKeyIndex("lost_reasons", "idx_lost_reasons_organization_id"),
                foreignKeyIndex("tour_outcomes", "idx_tour_outcomes_organization_id"));
    }

    @Test
    void definesExactCrmReferenceIndexInventory() {
        List<IndexMetadata> indexes = jdbcTemplate.query(
                """
                SELECT table_relation.relname AS table_name,
                       index_relation.relname AS index_name,
                       index_definition.indisunique,
                       index_definition.indnullsnotdistinct,
                       access_method.amname AS access_method,
                       index_definition.indisvalid,
                       index_definition.indisready,
                       index_definition.indpred IS NOT NULL AS partial,
                       string_agg(attribute.attname, ',' ORDER BY key_position.position)
                           AS key_columns
                  FROM pg_index index_definition
                  JOIN pg_class index_relation
                    ON index_relation.oid = index_definition.indexrelid
                  JOIN pg_class table_relation
                    ON table_relation.oid = index_definition.indrelid
                  JOIN pg_namespace table_schema
                    ON table_schema.oid = table_relation.relnamespace
                  JOIN pg_am access_method
                    ON access_method.oid = index_relation.relam
                  JOIN LATERAL generate_series(0, index_definition.indnkeyatts - 1)
                       AS key_position(position) ON TRUE
                  JOIN pg_attribute attribute
                    ON attribute.attrelid = table_relation.oid
                   AND attribute.attnum = index_definition.indkey[key_position.position]
                 WHERE table_schema.nspname = 'public'
                   AND table_relation.relname IN (
                       'lead_sources', 'lead_statuses', 'lost_reasons',
                       'tour_outcomes', 'lead_task_statuses', 'lead_activity_types')
                   AND NOT index_definition.indisprimary
                 GROUP BY table_relation.relname,
                          index_relation.relname,
                          index_definition.indisunique,
                          index_definition.indnullsnotdistinct,
                          access_method.amname,
                          index_definition.indisvalid,
                          index_definition.indisready,
                          index_definition.indpred
                """,
                (resultSet, rowNumber) -> new IndexMetadata(
                        resultSet.getString("table_name"),
                        resultSet.getString("index_name"),
                        resultSet.getBoolean("indisunique"),
                        resultSet.getBoolean("indnullsnotdistinct"),
                        resultSet.getString("access_method"),
                        resultSet.getBoolean("indisvalid"),
                        resultSet.getBoolean("indisready"),
                        resultSet.getBoolean("partial"),
                        resultSet.getString("key_columns")));

        assertThat(indexes).containsExactlyInAnyOrderElementsOf(expectedCrmReferenceIndexes());
    }

    @Test
    void hasNoDatabaseGeneratedCrmUuidsOrUuidExtensions() {
        List<ColumnDefaultMetadata> idColumns = jdbcTemplate.query(
                """
                SELECT table_name, column_default
                  FROM information_schema.columns
                 WHERE table_schema = 'public'
                   AND column_name = 'id'
                   AND table_name IN (
                       'lead_sources', 'lead_statuses', 'lost_reasons',
                       'tour_outcomes', 'lead_task_statuses', 'lead_activity_types',
                       'leads', 'lead_phones', 'prospective_children',
                       'lead_assignments', 'lead_status_history', 'lead_activities',
                       'lead_notes', 'lead_tasks', 'tours', 'lead_duplicates',
                       'call_directions', 'call_dispositions', 'call_event_types',
                       'webhook_statuses', 'pbx_configs', 'extensions',
                       'sip_accounts', 'phone_numbers', 'call_sessions',
                       'call_participants', 'call_events', 'call_recordings',
                       'webhook_events')
                 ORDER BY table_name
                """,
                (resultSet, rowNumber) -> new ColumnDefaultMetadata(
                        resultSet.getString("table_name"),
                        resultSet.getString("column_default")));
        assertThat(idColumns).containsExactly(
                new ColumnDefaultMetadata("call_directions", null),
                new ColumnDefaultMetadata("call_dispositions", null),
                new ColumnDefaultMetadata("call_event_types", null),
                new ColumnDefaultMetadata("call_events", null),
                new ColumnDefaultMetadata("call_participants", null),
                new ColumnDefaultMetadata("call_recordings", null),
                new ColumnDefaultMetadata("call_sessions", null),
                new ColumnDefaultMetadata("extensions", null),
                new ColumnDefaultMetadata("lead_activities", null),
                new ColumnDefaultMetadata("lead_activity_types", null),
                new ColumnDefaultMetadata("lead_assignments", null),
                new ColumnDefaultMetadata("lead_duplicates", null),
                new ColumnDefaultMetadata("lead_notes", null),
                new ColumnDefaultMetadata("lead_phones", null),
                new ColumnDefaultMetadata("lead_sources", null),
                new ColumnDefaultMetadata("lead_status_history", null),
                new ColumnDefaultMetadata("lead_statuses", null),
                new ColumnDefaultMetadata("lead_task_statuses", null),
                new ColumnDefaultMetadata("lead_tasks", null),
                new ColumnDefaultMetadata("leads", null),
                new ColumnDefaultMetadata("lost_reasons", null),
                new ColumnDefaultMetadata("pbx_configs", null),
                new ColumnDefaultMetadata("phone_numbers", null),
                new ColumnDefaultMetadata("prospective_children", null),
                new ColumnDefaultMetadata("sip_accounts", null),
                new ColumnDefaultMetadata("tour_outcomes", null),
                new ColumnDefaultMetadata("tours", null),
                new ColumnDefaultMetadata("webhook_events", null),
                new ColumnDefaultMetadata("webhook_statuses", null));

        List<String> uuidExtensions = jdbcTemplate.queryForList(
                "SELECT extname FROM pg_extension WHERE extname IN ('pgcrypto', 'uuid-ossp')",
                String.class);
        assertThat(uuidExtensions).isEmpty();
    }

    @Test
    void definesExactCrmLeadCoreColumnMetadata() {
        List<ColumnMetadata> actualColumns = jdbcTemplate.query(
                """
                SELECT table_name,
                       column_name,
                       data_type,
                       character_maximum_length,
                       is_nullable,
                       column_default
                  FROM information_schema.columns
                 WHERE table_schema = 'public'
                   AND table_name IN ('leads', 'lead_phones', 'prospective_children')
                """,
                (resultSet, rowNumber) -> new ColumnMetadata(
                        resultSet.getString("table_name"),
                        resultSet.getString("column_name"),
                        resultSet.getString("data_type"),
                        resultSet.getObject("character_maximum_length", Integer.class),
                        "YES".equals(resultSet.getString("is_nullable")),
                        resultSet.getString("column_default")));

        assertThat(actualColumns).containsExactlyInAnyOrderElementsOf(expectedCrmLeadCoreColumns());
    }

    @Test
    void definesExactCrmLeadCoreKeysAndConstraintCounts() {
        List<KeyMetadata> actualKeys = jdbcTemplate.query(
                """
                SELECT table_relation.relname AS table_name,
                       CASE constraint_definition.contype
                           WHEN 'p' THEN 'PRIMARY KEY'
                           WHEN 'u' THEN 'UNIQUE'
                       END AS key_type,
                       string_agg(attribute.attname, ',' ORDER BY key_position.ordinality)
                           AS key_columns,
                       index_definition.indnullsnotdistinct,
                       access_method.amname AS access_method
                  FROM pg_constraint constraint_definition
                  JOIN pg_class table_relation
                    ON table_relation.oid = constraint_definition.conrelid
                  JOIN pg_namespace table_schema
                    ON table_schema.oid = table_relation.relnamespace
                  JOIN pg_index index_definition
                    ON index_definition.indexrelid = constraint_definition.conindid
                  JOIN pg_class index_relation
                    ON index_relation.oid = index_definition.indexrelid
                  JOIN pg_am access_method
                    ON access_method.oid = index_relation.relam
                  JOIN LATERAL unnest(constraint_definition.conkey) WITH ORDINALITY
                       AS key_position(attribute_number, ordinality) ON TRUE
                  JOIN pg_attribute attribute
                    ON attribute.attrelid = table_relation.oid
                   AND attribute.attnum = key_position.attribute_number
                 WHERE table_schema.nspname = 'public'
                   AND constraint_definition.contype IN ('p', 'u')
                   AND table_relation.relname IN ('leads', 'lead_phones', 'prospective_children')
                 GROUP BY table_relation.relname,
                          constraint_definition.oid,
                          constraint_definition.contype,
                          index_definition.indnullsnotdistinct,
                          access_method.amname
                """,
                (resultSet, rowNumber) -> new KeyMetadata(
                        resultSet.getString("table_name"),
                        resultSet.getString("key_type"),
                        resultSet.getString("key_columns"),
                        resultSet.getBoolean("indnullsnotdistinct"),
                        resultSet.getString("access_method")));

        assertThat(actualKeys).containsExactlyInAnyOrderElementsOf(expectedCrmLeadCoreKeys());

        ConstraintCounts constraintCounts = jdbcTemplate.queryForObject(
                """
                SELECT count(*) FILTER (WHERE constraint_definition.contype = 'p') AS primary_keys,
                       count(*) FILTER (WHERE constraint_definition.contype = 'u') AS unique_constraints,
                       count(*) FILTER (WHERE constraint_definition.contype = 'f') AS foreign_keys,
                       count(*) FILTER (WHERE constraint_definition.contype = 'c') AS check_constraints
                  FROM pg_constraint constraint_definition
                  JOIN pg_class table_relation
                    ON table_relation.oid = constraint_definition.conrelid
                  JOIN pg_namespace table_schema
                    ON table_schema.oid = table_relation.relnamespace
                 WHERE table_schema.nspname = 'public'
                   AND table_relation.relname IN ('leads', 'lead_phones', 'prospective_children')
                """,
                (resultSet, rowNumber) -> new ConstraintCounts(
                        resultSet.getInt("primary_keys"),
                        resultSet.getInt("unique_constraints"),
                        resultSet.getInt("foreign_keys"),
                        resultSet.getInt("check_constraints")));
        assertThat(constraintCounts).isEqualTo(new ConstraintCounts(3, 0, 11, 0));
    }

    @Test
    void definesExactCrmLeadCoreIndexInventory() {
        List<IndexMetadata> indexes = jdbcTemplate.query(
                """
                SELECT table_relation.relname AS table_name,
                       index_relation.relname AS index_name,
                       index_definition.indisunique,
                       index_definition.indnullsnotdistinct,
                       access_method.amname AS access_method,
                       index_definition.indisvalid,
                       index_definition.indisready,
                       index_definition.indpred IS NOT NULL AS partial,
                       string_agg(attribute.attname, ',' ORDER BY key_position.position)
                           AS key_columns
                  FROM pg_index index_definition
                  JOIN pg_class index_relation
                    ON index_relation.oid = index_definition.indexrelid
                  JOIN pg_class table_relation
                    ON table_relation.oid = index_definition.indrelid
                  JOIN pg_namespace table_schema
                    ON table_schema.oid = table_relation.relnamespace
                  JOIN pg_am access_method
                    ON access_method.oid = index_relation.relam
                  JOIN LATERAL generate_series(0, index_definition.indnkeyatts - 1)
                       AS key_position(position) ON TRUE
                  JOIN pg_attribute attribute
                    ON attribute.attrelid = table_relation.oid
                   AND attribute.attnum = index_definition.indkey[key_position.position]
                 WHERE table_schema.nspname = 'public'
                   AND table_relation.relname IN ('leads', 'lead_phones', 'prospective_children')
                   AND NOT index_definition.indisprimary
                 GROUP BY table_relation.relname,
                          index_relation.relname,
                          index_definition.indisunique,
                          index_definition.indnullsnotdistinct,
                          access_method.amname,
                          index_definition.indisvalid,
                          index_definition.indisready,
                          index_definition.indpred
                """,
                (resultSet, rowNumber) -> new IndexMetadata(
                        resultSet.getString("table_name"),
                        resultSet.getString("index_name"),
                        resultSet.getBoolean("indisunique"),
                        resultSet.getBoolean("indnullsnotdistinct"),
                        resultSet.getString("access_method"),
                        resultSet.getBoolean("indisvalid"),
                        resultSet.getBoolean("indisready"),
                        resultSet.getBoolean("partial"),
                        resultSet.getString("key_columns")));

        assertThat(indexes).containsExactlyInAnyOrderElementsOf(expectedCrmLeadCoreIndexes());
    }

    @Test
    void definesPrimaryLeadPhoneIndexWithApprovedPostgresSemantics() {
        List<PartialUniqueIndexMetadata> indexes = jdbcTemplate.query(
                """
                SELECT index_definition.indisunique,
                       access_method.amname AS access_method,
                       index_definition.indnullsnotdistinct,
                       index_definition.indnkeyatts,
                       pg_get_indexdef(index_definition.indexrelid, 1, TRUE) AS first_key,
                       pg_get_indexdef(index_definition.indexrelid, 2, TRUE) AS second_key,
                       pg_get_indexdef(index_definition.indexrelid, 3, TRUE) AS third_key,
                       pg_get_expr(index_definition.indpred, index_definition.indrelid) AS predicate
                  FROM pg_index index_definition
                  JOIN pg_class index_relation
                    ON index_relation.oid = index_definition.indexrelid
                  JOIN pg_class table_relation
                    ON table_relation.oid = index_definition.indrelid
                  JOIN pg_namespace table_schema
                    ON table_schema.oid = table_relation.relnamespace
                  JOIN pg_am access_method
                    ON access_method.oid = index_relation.relam
                 WHERE table_schema.nspname = 'public'
                   AND table_relation.relname = 'lead_phones'
                   AND index_relation.relname = 'ux_lead_phones_primary_lead'
                """,
                (resultSet, rowNumber) -> new PartialUniqueIndexMetadata(
                        resultSet.getBoolean("indisunique"),
                        resultSet.getString("access_method"),
                        resultSet.getBoolean("indnullsnotdistinct"),
                        resultSet.getInt("indnkeyatts"),
                        resultSet.getString("first_key"),
                        resultSet.getString("second_key"),
                        resultSet.getString("third_key"),
                        resultSet.getString("predicate")));

        assertThat(indexes).hasSize(1);
        PartialUniqueIndexMetadata index = indexes.getFirst();
        assertThat(index.unique()).isTrue();
        assertThat(index.accessMethod()).isEqualTo("btree");
        assertThat(index.nullsNotDistinct()).isFalse();
        assertThat(index.keyCount()).isEqualTo(1);
        assertThat(index.firstKey()).isEqualTo("lead_id");
        assertThat(index.secondKey()).isEmpty();
        assertThat(index.thirdKey()).isEmpty();
        assertThat(index.predicate().replaceAll("[()\\s]", "")).isEqualTo("is_primary");

        List<String> partialUniqueIndexes = jdbcTemplate.queryForList(
                """
                SELECT index_relation.relname
                  FROM pg_index index_definition
                  JOIN pg_class index_relation
                    ON index_relation.oid = index_definition.indexrelid
                  JOIN pg_class table_relation
                    ON table_relation.oid = index_definition.indrelid
                  JOIN pg_namespace table_schema
                    ON table_schema.oid = table_relation.relnamespace
                 WHERE table_schema.nspname = 'public'
                   AND index_definition.indisunique
                   AND index_definition.indpred IS NOT NULL
                 ORDER BY index_relation.relname
                """,
                String.class);
        assertThat(partialUniqueIndexes).containsExactly(
                "ux_lead_assignments_active_lead",
                "ux_lead_phones_primary_lead",
                "ux_user_roles_current_assignment");
    }

    @Test
    void definesExactCrmWorkflowColumnMetadata() {
        List<ColumnMetadata> actualColumns = jdbcTemplate.query(
                """
                SELECT table_name,
                       column_name,
                       data_type,
                       character_maximum_length,
                       is_nullable,
                       column_default
                  FROM information_schema.columns
                 WHERE table_schema = 'public'
                   AND table_name IN (
                       'lead_assignments', 'lead_status_history', 'lead_activities',
                       'lead_notes', 'lead_tasks', 'tours', 'lead_duplicates')
                """,
                (resultSet, rowNumber) -> new ColumnMetadata(
                        resultSet.getString("table_name"),
                        resultSet.getString("column_name"),
                        resultSet.getString("data_type"),
                        resultSet.getObject("character_maximum_length", Integer.class),
                        "YES".equals(resultSet.getString("is_nullable")),
                        resultSet.getString("column_default")));

        assertThat(actualColumns).containsExactlyInAnyOrderElementsOf(expectedCrmWorkflowColumns());
    }

    @Test
    void definesExactCrmWorkflowKeysChecksAndConstraintCounts() {
        List<KeyMetadata> actualKeys = jdbcTemplate.query(
                """
                SELECT table_relation.relname AS table_name,
                       CASE constraint_definition.contype
                           WHEN 'p' THEN 'PRIMARY KEY'
                           WHEN 'u' THEN 'UNIQUE'
                       END AS key_type,
                       string_agg(attribute.attname, ',' ORDER BY key_position.ordinality)
                           AS key_columns,
                       index_definition.indnullsnotdistinct,
                       access_method.amname AS access_method
                  FROM pg_constraint constraint_definition
                  JOIN pg_class table_relation
                    ON table_relation.oid = constraint_definition.conrelid
                  JOIN pg_namespace table_schema
                    ON table_schema.oid = table_relation.relnamespace
                  JOIN pg_index index_definition
                    ON index_definition.indexrelid = constraint_definition.conindid
                  JOIN pg_class index_relation
                    ON index_relation.oid = index_definition.indexrelid
                  JOIN pg_am access_method
                    ON access_method.oid = index_relation.relam
                  JOIN LATERAL unnest(constraint_definition.conkey) WITH ORDINALITY
                       AS key_position(attribute_number, ordinality) ON TRUE
                  JOIN pg_attribute attribute
                    ON attribute.attrelid = table_relation.oid
                   AND attribute.attnum = key_position.attribute_number
                 WHERE table_schema.nspname = 'public'
                   AND constraint_definition.contype IN ('p', 'u')
                   AND table_relation.relname IN (
                       'lead_assignments', 'lead_status_history', 'lead_activities',
                       'lead_notes', 'lead_tasks', 'tours', 'lead_duplicates')
                 GROUP BY table_relation.relname,
                          constraint_definition.oid,
                          constraint_definition.contype,
                          index_definition.indnullsnotdistinct,
                          access_method.amname
                """,
                (resultSet, rowNumber) -> new KeyMetadata(
                        resultSet.getString("table_name"),
                        resultSet.getString("key_type"),
                        resultSet.getString("key_columns"),
                        resultSet.getBoolean("indnullsnotdistinct"),
                        resultSet.getString("access_method")));
        assertThat(actualKeys).containsExactlyInAnyOrderElementsOf(expectedCrmWorkflowKeys());

        List<String> checks = jdbcTemplate.queryForList(
                """
                SELECT constraint_definition.conname || '|' ||
                       pg_get_constraintdef(constraint_definition.oid, TRUE)
                  FROM pg_constraint constraint_definition
                  JOIN pg_class table_relation
                    ON table_relation.oid = constraint_definition.conrelid
                  JOIN pg_namespace table_schema
                    ON table_schema.oid = table_relation.relnamespace
                 WHERE table_schema.nspname = 'public'
                   AND table_relation.relname IN (
                       'lead_assignments', 'lead_status_history', 'lead_activities',
                       'lead_notes', 'lead_tasks', 'tours', 'lead_duplicates')
                   AND constraint_definition.contype = 'c'
                """,
                String.class);
        assertThat(checks).hasSize(1);
        assertThat(checks.getFirst().replaceAll("[()\\s]", ""))
                .isEqualTo("ck_lead_duplicates_not_self|CHECKlead_id<>duplicate_of_lead_id");

        ConstraintCounts constraintCounts = jdbcTemplate.queryForObject(
                """
                SELECT count(*) FILTER (WHERE constraint_definition.contype = 'p') AS primary_keys,
                       count(*) FILTER (WHERE constraint_definition.contype = 'u') AS unique_constraints,
                       count(*) FILTER (WHERE constraint_definition.contype = 'f') AS foreign_keys,
                       count(*) FILTER (WHERE constraint_definition.contype = 'c') AS check_constraints
                  FROM pg_constraint constraint_definition
                  JOIN pg_class table_relation
                    ON table_relation.oid = constraint_definition.conrelid
                  JOIN pg_namespace table_schema
                    ON table_schema.oid = table_relation.relnamespace
                 WHERE table_schema.nspname = 'public'
                   AND table_relation.relname IN (
                       'lead_assignments', 'lead_status_history', 'lead_activities',
                       'lead_notes', 'lead_tasks', 'tours', 'lead_duplicates')
                """,
                (resultSet, rowNumber) -> new ConstraintCounts(
                        resultSet.getInt("primary_keys"),
                        resultSet.getInt("unique_constraints"),
                        resultSet.getInt("foreign_keys"),
                        resultSet.getInt("check_constraints")));
        assertThat(constraintCounts).isEqualTo(new ConstraintCounts(7, 1, 24, 1));

        Integer applicationTriggerCount = jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                  FROM pg_trigger trigger_definition
                  JOIN pg_class table_relation
                    ON table_relation.oid = trigger_definition.tgrelid
                  JOIN pg_namespace table_schema
                    ON table_schema.oid = table_relation.relnamespace
                 WHERE table_schema.nspname = 'public'
                   AND NOT trigger_definition.tgisinternal
                   AND table_relation.relname IN (
                       'lead_assignments', 'lead_status_history', 'lead_activities',
                       'lead_notes', 'lead_tasks', 'tours', 'lead_duplicates')
                """,
                Integer.class);
        assertThat(applicationTriggerCount).isZero();
    }

    @Test
    void definesExactCrmWorkflowIndexInventory() {
        List<IndexMetadata> indexes = jdbcTemplate.query(
                """
                SELECT table_relation.relname AS table_name,
                       index_relation.relname AS index_name,
                       index_definition.indisunique,
                       index_definition.indnullsnotdistinct,
                       access_method.amname AS access_method,
                       index_definition.indisvalid,
                       index_definition.indisready,
                       index_definition.indpred IS NOT NULL AS partial,
                       string_agg(attribute.attname, ',' ORDER BY key_position.position)
                           AS key_columns
                  FROM pg_index index_definition
                  JOIN pg_class index_relation
                    ON index_relation.oid = index_definition.indexrelid
                  JOIN pg_class table_relation
                    ON table_relation.oid = index_definition.indrelid
                  JOIN pg_namespace table_schema
                    ON table_schema.oid = table_relation.relnamespace
                  JOIN pg_am access_method
                    ON access_method.oid = index_relation.relam
                  JOIN LATERAL generate_series(0, index_definition.indnkeyatts - 1)
                       AS key_position(position) ON TRUE
                  JOIN pg_attribute attribute
                    ON attribute.attrelid = table_relation.oid
                   AND attribute.attnum = index_definition.indkey[key_position.position]
                 WHERE table_schema.nspname = 'public'
                   AND table_relation.relname IN (
                       'lead_assignments', 'lead_status_history', 'lead_activities',
                       'lead_notes', 'lead_tasks', 'tours', 'lead_duplicates')
                   AND NOT index_definition.indisprimary
                 GROUP BY table_relation.relname,
                          index_relation.relname,
                          index_definition.indisunique,
                          index_definition.indnullsnotdistinct,
                          access_method.amname,
                          index_definition.indisvalid,
                          index_definition.indisready,
                          index_definition.indpred
                """,
                (resultSet, rowNumber) -> new IndexMetadata(
                        resultSet.getString("table_name"),
                        resultSet.getString("index_name"),
                        resultSet.getBoolean("indisunique"),
                        resultSet.getBoolean("indnullsnotdistinct"),
                        resultSet.getString("access_method"),
                        resultSet.getBoolean("indisvalid"),
                        resultSet.getBoolean("indisready"),
                        resultSet.getBoolean("partial"),
                        resultSet.getString("key_columns")));
        assertThat(indexes).containsExactlyInAnyOrderElementsOf(expectedCrmWorkflowIndexes());
    }

    @Test
    void definesActiveAssignmentIndexWithApprovedPostgresSemantics() {
        List<PartialUniqueIndexMetadata> indexes = jdbcTemplate.query(
                """
                SELECT index_definition.indisunique,
                       access_method.amname AS access_method,
                       index_definition.indnullsnotdistinct,
                       index_definition.indnkeyatts,
                       pg_get_indexdef(index_definition.indexrelid, 1, TRUE) AS first_key,
                       pg_get_indexdef(index_definition.indexrelid, 2, TRUE) AS second_key,
                       pg_get_indexdef(index_definition.indexrelid, 3, TRUE) AS third_key,
                       pg_get_expr(index_definition.indpred, index_definition.indrelid) AS predicate
                  FROM pg_index index_definition
                  JOIN pg_class index_relation
                    ON index_relation.oid = index_definition.indexrelid
                  JOIN pg_class table_relation
                    ON table_relation.oid = index_definition.indrelid
                  JOIN pg_namespace table_schema
                    ON table_schema.oid = table_relation.relnamespace
                  JOIN pg_am access_method
                    ON access_method.oid = index_relation.relam
                 WHERE table_schema.nspname = 'public'
                   AND table_relation.relname = 'lead_assignments'
                   AND index_relation.relname = 'ux_lead_assignments_active_lead'
                """,
                (resultSet, rowNumber) -> new PartialUniqueIndexMetadata(
                        resultSet.getBoolean("indisunique"),
                        resultSet.getString("access_method"),
                        resultSet.getBoolean("indnullsnotdistinct"),
                        resultSet.getInt("indnkeyatts"),
                        resultSet.getString("first_key"),
                        resultSet.getString("second_key"),
                        resultSet.getString("third_key"),
                        resultSet.getString("predicate")));

        assertThat(indexes).hasSize(1);
        PartialUniqueIndexMetadata index = indexes.getFirst();
        assertThat(index.unique()).isTrue();
        assertThat(index.accessMethod()).isEqualTo("btree");
        assertThat(index.nullsNotDistinct()).isFalse();
        assertThat(index.keyCount()).isEqualTo(1);
        assertThat(index.firstKey()).isEqualTo("lead_id");
        assertThat(index.secondKey()).isEmpty();
        assertThat(index.thirdKey()).isEmpty();
        assertThat(index.predicate().replaceAll("[()\\s]", ""))
                .isEqualTo("ended_atISNULL");

        List<String> partialUniqueIndexes = jdbcTemplate.queryForList(
                """
                SELECT index_relation.relname
                  FROM pg_index index_definition
                  JOIN pg_class index_relation
                    ON index_relation.oid = index_definition.indexrelid
                  JOIN pg_class table_relation
                    ON table_relation.oid = index_definition.indrelid
                  JOIN pg_namespace table_schema
                    ON table_schema.oid = table_relation.relnamespace
                 WHERE table_schema.nspname = 'public'
                   AND index_definition.indisunique
                   AND index_definition.indpred IS NOT NULL
                 ORDER BY index_relation.relname
                """,
                String.class);
        assertThat(partialUniqueIndexes).containsExactly(
                "ux_lead_assignments_active_lead",
                "ux_lead_phones_primary_lead",
                "ux_user_roles_current_assignment");
    }

    @Test
    void definesExactTelephonyConfigurationColumnMetadata() {
        List<ColumnMetadata> actualColumns = jdbcTemplate.query(
                """
                SELECT table_name,
                       column_name,
                       data_type,
                       character_maximum_length,
                       is_nullable,
                       column_default
                  FROM information_schema.columns
                 WHERE table_schema = 'public'
                   AND table_name IN (
                       'call_directions', 'call_dispositions', 'call_event_types',
                       'webhook_statuses', 'pbx_configs', 'extensions',
                       'sip_accounts', 'phone_numbers')
                """,
                (resultSet, rowNumber) -> new ColumnMetadata(
                        resultSet.getString("table_name"),
                        resultSet.getString("column_name"),
                        resultSet.getString("data_type"),
                        resultSet.getObject("character_maximum_length", Integer.class),
                        "YES".equals(resultSet.getString("is_nullable")),
                        resultSet.getString("column_default")));
        assertThat(actualColumns)
                .containsExactlyInAnyOrderElementsOf(expectedTelephonyConfigurationColumns());
    }

    @Test
    void definesExactTelephonyConfigurationKeysAndConstraintCounts() {
        List<KeyMetadata> actualKeys = jdbcTemplate.query(
                """
                SELECT table_relation.relname AS table_name,
                       CASE constraint_definition.contype
                           WHEN 'p' THEN 'PRIMARY KEY'
                           WHEN 'u' THEN 'UNIQUE'
                       END AS key_type,
                       string_agg(attribute.attname, ',' ORDER BY key_position.ordinality)
                           AS key_columns,
                       index_definition.indnullsnotdistinct,
                       access_method.amname AS access_method
                  FROM pg_constraint constraint_definition
                  JOIN pg_class table_relation
                    ON table_relation.oid = constraint_definition.conrelid
                  JOIN pg_namespace table_schema
                    ON table_schema.oid = table_relation.relnamespace
                  JOIN pg_index index_definition
                    ON index_definition.indexrelid = constraint_definition.conindid
                  JOIN pg_class index_relation
                    ON index_relation.oid = index_definition.indexrelid
                  JOIN pg_am access_method
                    ON access_method.oid = index_relation.relam
                  JOIN LATERAL unnest(constraint_definition.conkey) WITH ORDINALITY
                       AS key_position(attribute_number, ordinality) ON TRUE
                  JOIN pg_attribute attribute
                    ON attribute.attrelid = table_relation.oid
                   AND attribute.attnum = key_position.attribute_number
                 WHERE table_schema.nspname = 'public'
                   AND constraint_definition.contype IN ('p', 'u')
                   AND table_relation.relname IN (
                       'call_directions', 'call_dispositions', 'call_event_types',
                       'webhook_statuses', 'pbx_configs', 'extensions',
                       'sip_accounts', 'phone_numbers')
                 GROUP BY table_relation.relname,
                          constraint_definition.oid,
                          constraint_definition.contype,
                          index_definition.indnullsnotdistinct,
                          access_method.amname
                """,
                (resultSet, rowNumber) -> new KeyMetadata(
                        resultSet.getString("table_name"),
                        resultSet.getString("key_type"),
                        resultSet.getString("key_columns"),
                        resultSet.getBoolean("indnullsnotdistinct"),
                        resultSet.getString("access_method")));
        assertThat(actualKeys)
                .containsExactlyInAnyOrderElementsOf(expectedTelephonyConfigurationKeys());

        ConstraintCounts counts = jdbcTemplate.queryForObject(
                """
                SELECT count(*) FILTER (WHERE constraint_definition.contype = 'p') AS primary_keys,
                       count(*) FILTER (WHERE constraint_definition.contype = 'u') AS unique_constraints,
                       count(*) FILTER (WHERE constraint_definition.contype = 'f') AS foreign_keys,
                       count(*) FILTER (WHERE constraint_definition.contype = 'c') AS check_constraints
                  FROM pg_constraint constraint_definition
                  JOIN pg_class table_relation
                    ON table_relation.oid = constraint_definition.conrelid
                  JOIN pg_namespace table_schema
                    ON table_schema.oid = table_relation.relnamespace
                 WHERE table_schema.nspname = 'public'
                   AND table_relation.relname IN (
                       'call_directions', 'call_dispositions', 'call_event_types',
                       'webhook_statuses', 'pbx_configs', 'extensions',
                       'sip_accounts', 'phone_numbers')
                """,
                (resultSet, rowNumber) -> new ConstraintCounts(
                        resultSet.getInt("primary_keys"),
                        resultSet.getInt("unique_constraints"),
                        resultSet.getInt("foreign_keys"),
                        resultSet.getInt("check_constraints")));
        assertThat(counts).isEqualTo(new ConstraintCounts(8, 7, 7, 0));
    }

    @Test
    void definesExactTelephonyConfigurationIndexInventory() {
        List<IndexMetadata> indexes = jdbcTemplate.query(
                """
                SELECT table_relation.relname AS table_name,
                       index_relation.relname AS index_name,
                       index_definition.indisunique,
                       index_definition.indnullsnotdistinct,
                       access_method.amname AS access_method,
                       index_definition.indisvalid,
                       index_definition.indisready,
                       index_definition.indpred IS NOT NULL AS partial,
                       string_agg(attribute.attname, ',' ORDER BY key_position.position)
                           AS key_columns
                  FROM pg_index index_definition
                  JOIN pg_class index_relation
                    ON index_relation.oid = index_definition.indexrelid
                  JOIN pg_class table_relation
                    ON table_relation.oid = index_definition.indrelid
                  JOIN pg_namespace table_schema
                    ON table_schema.oid = table_relation.relnamespace
                  JOIN pg_am access_method
                    ON access_method.oid = index_relation.relam
                  JOIN LATERAL generate_series(0, index_definition.indnkeyatts - 1)
                       AS key_position(position) ON TRUE
                  JOIN pg_attribute attribute
                    ON attribute.attrelid = table_relation.oid
                   AND attribute.attnum = index_definition.indkey[key_position.position]
                 WHERE table_schema.nspname = 'public'
                   AND table_relation.relname IN (
                       'call_directions', 'call_dispositions', 'call_event_types',
                       'webhook_statuses', 'pbx_configs', 'extensions',
                       'sip_accounts', 'phone_numbers')
                   AND NOT index_definition.indisprimary
                 GROUP BY table_relation.relname,
                          index_relation.relname,
                          index_definition.indisunique,
                          index_definition.indnullsnotdistinct,
                          access_method.amname,
                          index_definition.indisvalid,
                          index_definition.indisready,
                          index_definition.indpred
                """,
                (resultSet, rowNumber) -> new IndexMetadata(
                        resultSet.getString("table_name"),
                        resultSet.getString("index_name"),
                        resultSet.getBoolean("indisunique"),
                        resultSet.getBoolean("indnullsnotdistinct"),
                        resultSet.getString("access_method"),
                        resultSet.getBoolean("indisvalid"),
                        resultSet.getBoolean("indisready"),
                        resultSet.getBoolean("partial"),
                        resultSet.getString("key_columns")));
        assertThat(indexes)
                .containsExactlyInAnyOrderElementsOf(expectedTelephonyConfigurationIndexes());
    }

    @Test
    void definesExactTelephonyCallColumnMetadata() {
        List<ColumnMetadata> actualColumns = jdbcTemplate.query(
                """
                SELECT table_name,
                       column_name,
                       data_type,
                       character_maximum_length,
                       is_nullable,
                       column_default
                  FROM information_schema.columns
                 WHERE table_schema = 'public'
                   AND table_name IN (
                       'call_sessions', 'call_participants', 'call_events',
                       'call_recordings', 'lead_calls', 'webhook_events')
                """,
                (resultSet, rowNumber) -> new ColumnMetadata(
                        resultSet.getString("table_name"),
                        resultSet.getString("column_name"),
                        resultSet.getString("data_type"),
                        resultSet.getObject("character_maximum_length", Integer.class),
                        "YES".equals(resultSet.getString("is_nullable")),
                        resultSet.getString("column_default")));

        assertThat(actualColumns)
                .containsExactlyInAnyOrderElementsOf(expectedTelephonyCallColumns());
    }

    @Test
    void definesExactTelephonyCallKeysAndConstraintCounts() {
        List<KeyMetadata> actualKeys = jdbcTemplate.query(
                """
                SELECT table_relation.relname AS table_name,
                       CASE constraint_definition.contype
                           WHEN 'p' THEN 'PRIMARY KEY'
                           WHEN 'u' THEN 'UNIQUE'
                       END AS key_type,
                       string_agg(attribute.attname, ',' ORDER BY key_position.ordinality)
                           AS key_columns,
                       index_definition.indnullsnotdistinct,
                       access_method.amname AS access_method
                  FROM pg_constraint constraint_definition
                  JOIN pg_class table_relation
                    ON table_relation.oid = constraint_definition.conrelid
                  JOIN pg_namespace table_schema
                    ON table_schema.oid = table_relation.relnamespace
                  JOIN pg_index index_definition
                    ON index_definition.indexrelid = constraint_definition.conindid
                  JOIN pg_class index_relation
                    ON index_relation.oid = index_definition.indexrelid
                  JOIN pg_am access_method
                    ON access_method.oid = index_relation.relam
                  JOIN LATERAL unnest(constraint_definition.conkey) WITH ORDINALITY
                       AS key_position(attribute_number, ordinality) ON TRUE
                  JOIN pg_attribute attribute
                    ON attribute.attrelid = table_relation.oid
                   AND attribute.attnum = key_position.attribute_number
                 WHERE table_schema.nspname = 'public'
                   AND constraint_definition.contype IN ('p', 'u')
                   AND table_relation.relname IN (
                       'call_sessions', 'call_participants', 'call_events',
                       'call_recordings', 'lead_calls', 'webhook_events')
                 GROUP BY table_relation.relname,
                          constraint_definition.oid,
                          constraint_definition.contype,
                          index_definition.indnullsnotdistinct,
                          access_method.amname
                """,
                (resultSet, rowNumber) -> new KeyMetadata(
                        resultSet.getString("table_name"),
                        resultSet.getString("key_type"),
                        resultSet.getString("key_columns"),
                        resultSet.getBoolean("indnullsnotdistinct"),
                        resultSet.getString("access_method")));
        assertThat(actualKeys).containsExactlyInAnyOrderElementsOf(expectedTelephonyCallKeys());

        ConstraintCounts counts = jdbcTemplate.queryForObject(
                """
                SELECT count(*) FILTER (WHERE constraint_definition.contype = 'p') AS primary_keys,
                       count(*) FILTER (WHERE constraint_definition.contype = 'u') AS unique_constraints,
                       count(*) FILTER (WHERE constraint_definition.contype = 'f') AS foreign_keys,
                       count(*) FILTER (WHERE constraint_definition.contype = 'c') AS check_constraints
                  FROM pg_constraint constraint_definition
                  JOIN pg_class table_relation
                    ON table_relation.oid = constraint_definition.conrelid
                  JOIN pg_namespace table_schema
                    ON table_schema.oid = table_relation.relnamespace
                 WHERE table_schema.nspname = 'public'
                   AND table_relation.relname IN (
                       'call_sessions', 'call_participants', 'call_events',
                       'call_recordings', 'lead_calls', 'webhook_events')
                """,
                (resultSet, rowNumber) -> new ConstraintCounts(
                        resultSet.getInt("primary_keys"),
                        resultSet.getInt("unique_constraints"),
                        resultSet.getInt("foreign_keys"),
                        resultSet.getInt("check_constraints")));
        assertThat(counts).isEqualTo(new ConstraintCounts(6, 2, 15, 0));

        List<String> leadCallColumns = jdbcTemplate.queryForList(
                """
                SELECT column_name
                  FROM information_schema.columns
                 WHERE table_schema = 'public'
                   AND table_name = 'lead_calls'
                 ORDER BY ordinal_position
                """,
                String.class);
        assertThat(leadCallColumns)
                .containsExactly("lead_id", "call_session_id", "linked_by", "linked_at");
    }

    @Test
    void definesExactTelephonyCallIndexInventory() {
        List<IndexMetadata> indexes = jdbcTemplate.query(
                """
                SELECT table_relation.relname AS table_name,
                       index_relation.relname AS index_name,
                       index_definition.indisunique,
                       index_definition.indnullsnotdistinct,
                       access_method.amname AS access_method,
                       index_definition.indisvalid,
                       index_definition.indisready,
                       index_definition.indpred IS NOT NULL AS partial,
                       string_agg(attribute.attname, ',' ORDER BY key_position.position)
                           AS key_columns
                  FROM pg_index index_definition
                  JOIN pg_class index_relation
                    ON index_relation.oid = index_definition.indexrelid
                  JOIN pg_class table_relation
                    ON table_relation.oid = index_definition.indrelid
                  JOIN pg_namespace table_schema
                    ON table_schema.oid = table_relation.relnamespace
                  JOIN pg_am access_method
                    ON access_method.oid = index_relation.relam
                  JOIN LATERAL generate_series(0, index_definition.indnkeyatts - 1)
                       AS key_position(position) ON TRUE
                  JOIN pg_attribute attribute
                    ON attribute.attrelid = table_relation.oid
                   AND attribute.attnum = index_definition.indkey[key_position.position]
                 WHERE table_schema.nspname = 'public'
                   AND table_relation.relname IN (
                       'call_sessions', 'call_participants', 'call_events',
                       'call_recordings', 'lead_calls', 'webhook_events')
                   AND NOT index_definition.indisprimary
                 GROUP BY table_relation.relname,
                          index_relation.relname,
                          index_definition.indisunique,
                          index_definition.indnullsnotdistinct,
                          access_method.amname,
                          index_definition.indisvalid,
                          index_definition.indisready,
                          index_definition.indpred
                """,
                (resultSet, rowNumber) -> new IndexMetadata(
                        resultSet.getString("table_name"),
                        resultSet.getString("index_name"),
                        resultSet.getBoolean("indisunique"),
                        resultSet.getBoolean("indnullsnotdistinct"),
                        resultSet.getString("access_method"),
                        resultSet.getBoolean("indisvalid"),
                        resultSet.getBoolean("indisready"),
                        resultSet.getBoolean("partial"),
                        resultSet.getString("key_columns")));
        assertThat(indexes).containsExactlyInAnyOrderElementsOf(expectedTelephonyCallIndexes());

        Integer primaryIndexCount = jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                  FROM pg_index index_definition
                  JOIN pg_class table_relation
                    ON table_relation.oid = index_definition.indrelid
                  JOIN pg_namespace table_schema
                    ON table_schema.oid = table_relation.relnamespace
                  JOIN pg_class index_relation
                    ON index_relation.oid = index_definition.indexrelid
                  JOIN pg_am access_method
                    ON access_method.oid = index_relation.relam
                 WHERE table_schema.nspname = 'public'
                   AND table_relation.relname IN (
                       'call_sessions', 'call_participants', 'call_events',
                       'call_recordings', 'lead_calls', 'webhook_events')
                   AND index_definition.indisprimary
                   AND index_definition.indisvalid
                   AND index_definition.indisready
                   AND index_definition.indpred IS NULL
                   AND NOT index_definition.indnullsnotdistinct
                   AND access_method.amname = 'btree'
                """,
                Integer.class);
        assertThat(primaryIndexCount).isEqualTo(6);
    }

    @Test
    void providesLeadingBtreeCoverageForEveryTelephonyCallForeignKey() {
        List<ForeignKeyIndexCoverage> actualCoverage = jdbcTemplate.query(
                """
                SELECT source_table.relname AS source_table,
                       source_attribute.attname AS source_column,
                       EXISTS (
                           SELECT 1
                             FROM pg_index index_definition
                             JOIN pg_class index_relation
                               ON index_relation.oid = index_definition.indexrelid
                             JOIN pg_am access_method
                               ON access_method.oid = index_relation.relam
                            WHERE index_definition.indrelid = source_table.oid
                              AND index_definition.indisvalid
                              AND index_definition.indisready
                              AND index_definition.indpred IS NULL
                              AND index_definition.indnkeyatts > 0
                              AND index_definition.indkey[0] = source_attribute.attnum
                              AND access_method.amname = 'btree'
                       ) AS covered
                  FROM pg_constraint constraint_definition
                  JOIN pg_class source_table
                    ON source_table.oid = constraint_definition.conrelid
                  JOIN pg_namespace source_schema
                    ON source_schema.oid = source_table.relnamespace
                  JOIN LATERAL unnest(constraint_definition.conkey)
                       AS source_key(attribute_number) ON TRUE
                  JOIN pg_attribute source_attribute
                    ON source_attribute.attrelid = source_table.oid
                   AND source_attribute.attnum = source_key.attribute_number
                 WHERE constraint_definition.contype = 'f'
                   AND source_schema.nspname = 'public'
                   AND source_table.relname IN (
                       'call_sessions', 'call_participants', 'call_events',
                       'call_recordings', 'lead_calls', 'webhook_events')
                """,
                (resultSet, rowNumber) -> new ForeignKeyIndexCoverage(
                        resultSet.getString("source_table"),
                        resultSet.getString("source_column"),
                        resultSet.getBoolean("covered")));

        List<ForeignKeyIndexCoverage> expectedCoverage = expectedTelephonyCallForeignKeys().stream()
                .map(foreignKey -> new ForeignKeyIndexCoverage(
                        foreignKey.sourceTable(), foreignKey.sourceColumn(), true))
                .toList();
        assertThat(actualCoverage).containsExactlyInAnyOrderElementsOf(expectedCoverage);
    }

    @Test
    void enforcesGlobalTelephonyReferenceCodeUniqueness() {
        for (String tableName : telephonyReferenceTables()) {
            String firstCode = genericTelephonyValue("CODE");
            String secondCode = genericTelephonyValue("CODE");
            try {
                insertTelephonyReference(tableName, firstCode);
                assertSqlState(
                        "23505", () -> insertTelephonyReference(tableName, firstCode));
                insertTelephonyReference(tableName, secondCode);

                assertThat(countTelephonyReferences(tableName, firstCode, secondCode)).isEqualTo(2);
            } finally {
                deleteTelephonyReferences(tableName, firstCode, secondCode);
            }
        }
    }

    @Test
    void enforcesExactPbxScopedExtensionSipAndPhoneIdentities() {
        TelephonyFixture fixture = createTelephonyFixture();
        String sharedExtension = genericTelephonyValue("EXT");
        String sharedSipUsername = genericTelephonyValue("SIP");
        String sharedNumber = genericTelephonyValue("NUMBER");
        try {
            UUID firstExtensionId = insertExtension(fixture.firstPbxId(), sharedExtension);
            assertSqlState(
                    "23505", () -> insertExtension(fixture.firstPbxId(), sharedExtension));
            UUID secondExtensionId = insertExtension(fixture.secondPbxId(), sharedExtension);
            UUID additionalExtensionId = insertExtension(
                    fixture.firstPbxId(), genericTelephonyValue("EXT"));

            insertSipAccount(fixture.firstPbxId(), firstExtensionId, sharedSipUsername);
            assertSqlState(
                    "23505",
                    () -> insertSipAccount(
                            fixture.firstPbxId(), additionalExtensionId, sharedSipUsername));
            insertSipAccount(fixture.secondPbxId(), secondExtensionId, sharedSipUsername);
            insertSipAccount(
                    fixture.firstPbxId(), firstExtensionId, genericTelephonyValue("SIP"));

            insertPhoneNumber(fixture.firstPbxId(), sharedNumber);
            assertSqlState(
                    "23505", () -> insertPhoneNumber(fixture.firstPbxId(), sharedNumber));
            insertPhoneNumber(fixture.secondPbxId(), sharedNumber);
            insertPhoneNumber(fixture.firstPbxId(), genericTelephonyValue("NUMBER"));

            assertThat(countRowsForPbx("extensions", fixture.firstPbxId())).isEqualTo(2);
            assertThat(countRowsForPbx("extensions", fixture.secondPbxId())).isEqualTo(1);
            assertThat(countRowsForPbx("sip_accounts", fixture.firstPbxId())).isEqualTo(2);
            assertThat(countRowsForPbx("sip_accounts", fixture.secondPbxId())).isEqualTo(1);
            assertThat(countRowsForPbx("phone_numbers", fixture.firstPbxId())).isEqualTo(2);
            assertThat(countRowsForPbx("phone_numbers", fixture.secondPbxId())).isEqualTo(1);
        } finally {
            deleteTelephonyFixture(fixture);
        }
    }

    @Test
    void permitsMoreThanFourUnassignedExtensions() {
        TelephonyFixture fixture = createTelephonyFixture();
        try {
            for (int extensionNumber = 1; extensionNumber <= 6; extensionNumber++) {
                insertExtension(
                        fixture.firstPbxId(),
                        genericTelephonyValue("EXT" + extensionNumber));
            }

            assertThat(countRowsForPbx("extensions", fixture.firstPbxId())).isEqualTo(6);
            Integer assignedExtensionCount = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM extensions WHERE pbx_config_id = ? AND user_id IS NOT NULL",
                    Integer.class,
                    fixture.firstPbxId());
            assertThat(assignedExtensionCount).isZero();
        } finally {
            deleteTelephonyFixture(fixture);
        }
    }

    @Test
    void rejectsSelectedTelephonyConfigurationForeignKeyViolations() {
        TelephonyFixture fixture = createTelephonyFixture();
        try {
            assertSqlState("23503", () -> insertPbxConfig(UUID.randomUUID(), null));
            assertSqlState(
                    "23503", () -> insertExtension(UUID.randomUUID(), genericTelephonyValue("EXT")));
            assertSqlState(
                    "23503",
                    () -> insertExtension(
                            fixture.firstPbxId(), UUID.randomUUID(), genericTelephonyValue("EXT")));
            assertSqlState(
                    "23503",
                    () -> insertSipAccount(
                            fixture.firstPbxId(), UUID.randomUUID(), genericTelephonyValue("SIP")));
            assertSqlState(
                    "23503",
                    () -> insertPhoneNumber(UUID.randomUUID(), genericTelephonyValue("NUMBER")));

            assertThat(countRowsForPbx("extensions", fixture.firstPbxId())).isZero();
        } finally {
            deleteTelephonyFixture(fixture);
        }
    }

    @Test
    void enforcesPbxScopedCallIdentityAndPreservesApplicationOwnedSessionFields() {
        TelephonyCallFixture fixture = createTelephonyCallFixture();
        String sharedExternalCallId = genericTelephonyValue("CALL");
        try {
            UUID firstSessionId = insertCallSession(
                    fixture.telephonyFixture().firstPbxId(),
                    fixture.directionId(),
                    sharedExternalCallId);
            assertSqlState(
                    "23505",
                    () -> insertCallSession(
                            fixture.telephonyFixture().firstPbxId(),
                            fixture.directionId(),
                            sharedExternalCallId));
            insertCallSession(
                    fixture.telephonyFixture().secondPbxId(),
                    fixture.directionId(),
                    sharedExternalCallId);
            UUID applicationInvalidSessionId = insertApplicationInvalidCallSession(
                    fixture.telephonyFixture().firstPbxId(), fixture.directionId());

            Map<String, Object> defaultedSession = jdbcTemplate.queryForMap(
                    """
                    SELECT disposition_id, answered_at, ended_at, duration_seconds
                      FROM call_sessions
                     WHERE id = ?
                    """,
                    firstSessionId);
            assertThat(defaultedSession.get("disposition_id")).isNull();
            assertThat(defaultedSession.get("answered_at")).isNull();
            assertThat(defaultedSession.get("ended_at")).isNull();
            assertThat(defaultedSession.get("duration_seconds")).isEqualTo(0);
            assertThat(jdbcTemplate.queryForObject(
                            "SELECT duration_seconds FROM call_sessions WHERE id = ?",
                            Integer.class,
                            applicationInvalidSessionId))
                    .isEqualTo(-1);
            assertThat(countRowsForPbx("call_sessions", fixture.telephonyFixture().firstPbxId()))
                    .isEqualTo(2);
            assertThat(countRowsForPbx("call_sessions", fixture.telephonyFixture().secondPbxId()))
                    .isEqualTo(1);
        } finally {
            deleteTelephonyCallFixture(fixture);
        }
    }

    @Test
    void permitsFlexibleCallParticipantsWithoutDatabaseRoleValidation() {
        TelephonyCallFixture fixture = createTelephonyCallFixture();
        UUID callSessionId = insertCallSession(
                fixture.telephonyFixture().firstPbxId(),
                fixture.directionId(),
                genericTelephonyValue("CALL"));
        try {
            insertCallParticipant(callSessionId, "CALLER");
            insertCallParticipant(callSessionId, "AGENT");
            insertCallParticipant(callSessionId, "TEST_ONLY_UNRECOGNIZED_ROLE");

            List<Map<String, Object>> participants = jdbcTemplate.queryForList(
                    """
                    SELECT participant_role, user_id, extension_id, normalized_phone,
                           joined_at, left_at
                      FROM call_participants
                     WHERE call_session_id = ?
                     ORDER BY participant_role
                    """,
                    callSessionId);
            assertThat(participants).hasSize(3);
            assertThat(participants)
                    .extracting(row -> row.get("participant_role"))
                    .containsExactly("AGENT", "CALLER", "TEST_ONLY_UNRECOGNIZED_ROLE");
            assertThat(participants)
                    .allSatisfy(row -> {
                        assertThat(row.get("user_id")).isNull();
                        assertThat(row.get("extension_id")).isNull();
                        assertThat(row.get("normalized_phone")).isNull();
                        assertThat(row.get("joined_at")).isNull();
                        assertThat(row.get("left_at")).isNull();
                    });
        } finally {
            deleteTelephonyCallFixture(fixture);
        }
    }

    @Test
    void permitsSanitizedCallEventsWithNullableAndDuplicateExternalIds() {
        TelephonyCallFixture fixture = createTelephonyCallFixture();
        UUID callSessionId = insertCallSession(
                fixture.telephonyFixture().firstPbxId(),
                fixture.directionId(),
                genericTelephonyValue("CALL"));
        String externalEventId = genericTelephonyValue("EVENT");
        try {
            insertCallEvent(
                    callSessionId,
                    externalEventId,
                    fixture.eventTypeId(),
                    "{\"kind\":\"generic\",\"safe\":true}");
            insertCallEvent(
                    callSessionId,
                    externalEventId,
                    fixture.eventTypeId(),
                    "{\"kind\":\"generic-duplicate\"}");
            insertCallEvent(callSessionId, null, fixture.eventTypeId(), null);

            assertThat(jdbcTemplate.queryForObject(
                            "SELECT count(*) FROM call_events WHERE call_session_id = ?",
                            Integer.class,
                            callSessionId))
                    .isEqualTo(3);
            assertThat(jdbcTemplate.queryForList(
                            """
                            SELECT sanitized_metadata ->> 'kind'
                              FROM call_events
                             WHERE call_session_id = ?
                               AND external_event_id = ?
                             ORDER BY sanitized_metadata ->> 'kind'
                            """,
                            String.class,
                            callSessionId,
                            externalEventId))
                    .containsExactly("generic", "generic-duplicate");
        } finally {
            deleteTelephonyCallFixture(fixture);
        }
    }

    @Test
    void permitsNullableAndNonUniqueRecordingMetadata() {
        TelephonyCallFixture fixture = createTelephonyCallFixture();
        UUID callSessionId = insertCallSession(
                fixture.telephonyFixture().firstPbxId(),
                fixture.directionId(),
                genericTelephonyValue("CALL"));
        UUID storedFileId = insertGenericStoredFile(
                fixture.telephonyFixture().organizationId(),
                fixture.telephonyFixture().branchId());
        String externalRecordingId = genericTelephonyValue("RECORDING");
        try {
            insertCallRecording(callSessionId, null, externalRecordingId, null);
            insertCallRecording(
                    callSessionId,
                    storedFileId,
                    externalRecordingId,
                    "https://example.invalid/recordings/" + UUID.randomUUID());

            List<Map<String, Object>> recordings = jdbcTemplate.queryForList(
                    """
                    SELECT stored_file_id, recording_url, duration_seconds
                      FROM call_recordings
                     WHERE call_session_id = ?
                     ORDER BY stored_file_id NULLS FIRST
                    """,
                    callSessionId);
            assertThat(recordings).hasSize(2);
            assertThat(recordings.getFirst().get("stored_file_id")).isNull();
            assertThat(recordings.getFirst().get("recording_url")).isNull();
            assertThat(recordings)
                    .allSatisfy(row -> assertThat(row.get("duration_seconds")).isNull());
        } finally {
            deleteTelephonyCallRows(fixture);
            jdbcTemplate.update("DELETE FROM stored_files WHERE id = ?", storedFileId);
            deleteTelephonyCallParents(fixture);
        }
    }

    @Test
    void enforcesCompositeLeadCallIdentityWhilePermittingApprovedCardinality() {
        TelephonyCallFixture fixture = createTelephonyCallFixture();
        LeadFixture firstLead = createLeadFixture();
        LeadFixture secondLead = createLeadFixture();
        UUID firstSessionId = insertCallSession(
                fixture.telephonyFixture().firstPbxId(),
                fixture.directionId(),
                genericTelephonyValue("CALL"));
        UUID secondSessionId = insertCallSession(
                fixture.telephonyFixture().firstPbxId(),
                fixture.directionId(),
                genericTelephonyValue("CALL"));
        try {
            insertLeadCall(firstLead.leadId(), firstSessionId);
            assertSqlState(
                    "23505", () -> insertLeadCall(firstLead.leadId(), firstSessionId));
            insertLeadCall(firstLead.leadId(), secondSessionId);
            insertLeadCall(secondLead.leadId(), firstSessionId);

            assertThat(jdbcTemplate.queryForObject(
                            "SELECT count(*) FROM lead_calls WHERE lead_id = ?",
                            Integer.class,
                            firstLead.leadId()))
                    .isEqualTo(2);
            assertThat(jdbcTemplate.queryForObject(
                            "SELECT count(*) FROM lead_calls WHERE call_session_id = ?",
                            Integer.class,
                            firstSessionId))
                    .isEqualTo(2);
            assertThat(jdbcTemplate.queryForObject(
                            "SELECT count(*) FROM lead_calls WHERE linked_by IS NOT NULL",
                            Integer.class))
                    .isZero();
        } finally {
            deleteTelephonyCallRows(fixture);
            deleteLeadFixture(firstLead);
            deleteLeadFixture(secondLead);
            deleteTelephonyCallParents(fixture);
        }
    }

    @Test
    void enforcesPbxScopedWebhookIdempotencyWithoutHashUniqueness() {
        TelephonyCallFixture fixture = createTelephonyCallFixture();
        String sharedExternalEventId = genericTelephonyValue("WEBHOOK");
        String sharedPayloadHash = genericTelephonyValue("HASH");
        try {
            insertWebhookEvent(
                    fixture.telephonyFixture().firstPbxId(),
                    fixture.webhookStatusId(),
                    sharedExternalEventId,
                    sharedPayloadHash);
            assertSqlState(
                    "23505",
                    () -> insertWebhookEvent(
                            fixture.telephonyFixture().firstPbxId(),
                            fixture.webhookStatusId(),
                            sharedExternalEventId,
                            genericTelephonyValue("HASH")));
            insertWebhookEvent(
                    fixture.telephonyFixture().secondPbxId(),
                    fixture.webhookStatusId(),
                    sharedExternalEventId,
                    sharedPayloadHash);
            insertWebhookEvent(
                    fixture.telephonyFixture().firstPbxId(),
                    fixture.webhookStatusId(),
                    genericTelephonyValue("WEBHOOK"),
                    sharedPayloadHash);
            assertSqlState(
                    "23502",
                    () -> insertWebhookEvent(
                            fixture.telephonyFixture().firstPbxId(),
                            fixture.webhookStatusId(),
                            genericTelephonyValue("WEBHOOK"),
                            null));

            assertThat(countRowsForPbx("webhook_events", fixture.telephonyFixture().firstPbxId()))
                    .isEqualTo(2);
            assertThat(countRowsForPbx("webhook_events", fixture.telephonyFixture().secondPbxId()))
                    .isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                            "SELECT count(*) FROM webhook_events WHERE payload_hash = ?",
                            Integer.class,
                            sharedPayloadHash))
                    .isEqualTo(3);
            assertThat(jdbcTemplate.queryForObject(
                            """
                            SELECT count(*) FROM webhook_events
                             WHERE processed_at IS NOT NULL OR error_message IS NOT NULL
                            """,
                            Integer.class))
                    .isZero();
        } finally {
            deleteTelephonyCallFixture(fixture);
        }
    }

    @Test
    void rejectsSelectedTelephonyCallForeignKeyViolations() {
        TelephonyCallFixture fixture = createTelephonyCallFixture();
        LeadFixture leadFixture = createLeadFixture();
        UUID callSessionId = insertCallSession(
                fixture.telephonyFixture().firstPbxId(),
                fixture.directionId(),
                genericTelephonyValue("CALL"));
        try {
            assertSqlState(
                    "23503",
                    () -> insertCallSession(
                            UUID.randomUUID(), fixture.directionId(), genericTelephonyValue("CALL")));
            assertSqlState(
                    "23503",
                    () -> insertCallSession(
                            fixture.telephonyFixture().firstPbxId(),
                            UUID.randomUUID(),
                            genericTelephonyValue("CALL")));
            assertSqlState(
                    "23503", () -> insertCallParticipant(UUID.randomUUID(), "CALLER"));
            assertSqlState(
                    "23503",
                    () -> insertCallEvent(
                            callSessionId,
                            genericTelephonyValue("EVENT"),
                            UUID.randomUUID(),
                            null));
            assertSqlState(
                    "23503",
                    () -> insertCallRecording(
                            callSessionId, UUID.randomUUID(), null, null));
            assertSqlState(
                    "23503", () -> insertLeadCall(UUID.randomUUID(), callSessionId));
            assertSqlState(
                    "23503", () -> insertLeadCall(leadFixture.leadId(), UUID.randomUUID()));
            assertSqlState(
                    "23503",
                    () -> insertWebhookEvent(
                            fixture.telephonyFixture().firstPbxId(),
                            UUID.randomUUID(),
                            genericTelephonyValue("WEBHOOK"),
                            genericTelephonyValue("HASH")));
        } finally {
            deleteTelephonyCallRows(fixture);
            deleteLeadFixture(leadFixture);
            deleteTelephonyCallParents(fixture);
        }
    }

    @Test
    void rejectsASecondActiveAssignmentForTheSameLead() {
        WorkflowFixture fixture = createWorkflowFixture();
        try {
            insertLeadAssignment(fixture.leadId(), fixture.firstUserId(), null);

            assertSqlState(
                    "23505",
                    () -> insertLeadAssignment(fixture.leadId(), fixture.secondUserId(), null));

            assertThat(countActiveAssignments(fixture.leadId())).isEqualTo(1);
        } finally {
            deleteWorkflowFixture(fixture);
        }
    }

    @Test
    void permitsAssignmentHistoryAndReplacingAnEndedActiveAssignment() {
        WorkflowFixture fixture = createWorkflowFixture();
        try {
            insertLeadAssignment(
                    fixture.leadId(), fixture.firstUserId(), "CURRENT_TIMESTAMP - INTERVAL '1 day'");
            insertLeadAssignment(
                    fixture.leadId(), fixture.secondUserId(), "CURRENT_TIMESTAMP - INTERVAL '2 days'");
            insertLeadAssignment(fixture.leadId(), fixture.firstUserId(), null);
            insertLeadAssignment(fixture.secondLeadId(), fixture.secondUserId(), null);

            assertThat(countAssignments(fixture.leadId())).isEqualTo(3);
            assertThat(countActiveAssignments(fixture.leadId())).isEqualTo(1);
            assertThat(countActiveAssignments(fixture.secondLeadId())).isEqualTo(1);

            jdbcTemplate.update(
                    """
                    UPDATE lead_assignments
                       SET ended_at = CURRENT_TIMESTAMP
                     WHERE lead_id = ? AND ended_at IS NULL
                    """,
                    fixture.leadId());
            insertLeadAssignment(fixture.leadId(), fixture.secondUserId(), null);

            assertThat(countAssignments(fixture.leadId())).isEqualTo(4);
            assertThat(countActiveAssignments(fixture.leadId())).isEqualTo(1);
        } finally {
            deleteWorkflowFixture(fixture);
        }
    }

    @Test
    void enforcesDirectedDuplicatePairAndSelfLinkConstraints() {
        WorkflowFixture fixture = createWorkflowFixture();
        try {
            insertLeadDuplicate(fixture.leadId(), fixture.secondLeadId());
            assertSqlState(
                    "23505",
                    () -> insertLeadDuplicate(fixture.leadId(), fixture.secondLeadId()));
            assertSqlState(
                    "23514", () -> insertLeadDuplicate(fixture.leadId(), fixture.leadId()));

            insertLeadDuplicate(fixture.secondLeadId(), fixture.leadId());
            Integer pairCount = jdbcTemplate.queryForObject(
                    """
                    SELECT count(*)
                      FROM lead_duplicates
                     WHERE lead_id IN (?, ?) AND duplicate_of_lead_id IN (?, ?)
                    """,
                    Integer.class,
                    fixture.leadId(),
                    fixture.secondLeadId(),
                    fixture.leadId(),
                    fixture.secondLeadId());
            assertThat(pairCount).isEqualTo(2);
        } finally {
            deleteWorkflowFixture(fixture);
        }
    }

    @Test
    void permitsCrossOrganizationDuplicateLinks() {
        WorkflowFixture firstFixture = createWorkflowFixture();
        WorkflowFixture secondFixture = createWorkflowFixture();
        try {
            insertLeadDuplicate(firstFixture.leadId(), secondFixture.leadId());

            Integer pairCount = jdbcTemplate.queryForObject(
                    """
                    SELECT count(*) FROM lead_duplicates
                     WHERE lead_id = ? AND duplicate_of_lead_id = ?
                    """,
                    Integer.class,
                    firstFixture.leadId(),
                    secondFixture.leadId());
            assertThat(pairCount).isEqualTo(1);
        } finally {
            deleteWorkflowFixture(firstFixture);
            deleteWorkflowFixture(secondFixture);
        }
    }

    @Test
    void permitsMultipleMutableStatusHistoryRows() {
        WorkflowFixture fixture = createWorkflowFixture();
        try {
            UUID firstHistoryId = insertLeadStatusHistory(
                    fixture.leadId(), null, fixture.statusId(), fixture.firstUserId());
            insertLeadStatusHistory(
                    fixture.leadId(),
                    fixture.statusId(),
                    fixture.statusId(),
                    fixture.secondUserId());

            jdbcTemplate.update(
                    "UPDATE lead_status_history SET note = 'Updated generic note' WHERE id = ?",
                    firstHistoryId);
            String note = jdbcTemplate.queryForObject(
                    "SELECT note FROM lead_status_history WHERE id = ?",
                    String.class,
                    firstHistoryId);
            assertThat(note).isEqualTo("Updated generic note");
            assertThat(countRowsForLead("lead_status_history", fixture.leadId())).isEqualTo(2);

            jdbcTemplate.update("DELETE FROM lead_status_history WHERE id = ?", firstHistoryId);
            assertThat(countRowsForLead("lead_status_history", fixture.leadId())).isEqualTo(1);
        } finally {
            deleteWorkflowFixture(fixture);
        }
    }

    @Test
    void permitsMultipleActivitiesEditableNotesAndEquivalentTasks() {
        WorkflowFixture fixture = createWorkflowFixture();
        try {
            insertLeadActivity(fixture);
            insertLeadActivity(fixture);
            UUID noteId = insertLeadNote(fixture);
            insertLeadNote(fixture);
            insertLeadTask(fixture);
            insertLeadTask(fixture);

            jdbcTemplate.update(
                    "UPDATE lead_notes SET note_text = 'Edited generic note' WHERE id = ?", noteId);
            String noteText = jdbcTemplate.queryForObject(
                    "SELECT note_text FROM lead_notes WHERE id = ?", String.class, noteId);
            String metadataKind = jdbcTemplate.queryForObject(
                    """
                    SELECT sanitized_metadata ->> 'kind'
                      FROM lead_activities
                     WHERE lead_id = ?
                     LIMIT 1
                    """,
                    String.class,
                    fixture.leadId());

            assertThat(noteText).isEqualTo("Edited generic note");
            assertThat(metadataKind).isEqualTo("generic");
            assertThat(countRowsForLead("lead_activities", fixture.leadId())).isEqualTo(2);
            assertThat(countRowsForLead("lead_notes", fixture.leadId())).isEqualTo(2);
            assertThat(countRowsForLead("lead_tasks", fixture.leadId())).isEqualTo(2);
            Integer incompleteTasks = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM lead_tasks WHERE lead_id = ? AND completed_at IS NULL",
                    Integer.class,
                    fixture.leadId());
            assertThat(incompleteTasks).isEqualTo(2);
        } finally {
            deleteWorkflowFixture(fixture);
        }
    }

    @Test
    void permitsMultipleToursAndApplicationOwnedCrossScopeValues() {
        WorkflowFixture firstFixture = createWorkflowFixture();
        WorkflowFixture secondFixture = createWorkflowFixture();
        try {
            insertTour(
                    firstFixture,
                    firstFixture.branchId(),
                    firstFixture.firstUserId(),
                    null);
            insertTour(
                    firstFixture,
                    firstFixture.branchId(),
                    firstFixture.firstUserId(),
                    null);
            insertTour(
                    firstFixture,
                    secondFixture.branchId(),
                    firstFixture.firstUserId(),
                    secondFixture.outcomeId());

            assertThat(countRowsForLead("tours", firstFixture.leadId())).isEqualTo(3);
            Integer openTours = jdbcTemplate.queryForObject(
                    """
                    SELECT count(*) FROM tours
                     WHERE lead_id = ? AND attended_at IS NULL AND outcome_id IS NULL
                    """,
                    Integer.class,
                    firstFixture.leadId());
            assertThat(openTours).isEqualTo(2);
        } finally {
            deleteWorkflowFixture(firstFixture);
            deleteWorkflowFixture(secondFixture);
        }
    }

    @Test
    void rejectsSelectedCrmWorkflowForeignKeyViolations() {
        WorkflowFixture fixture = createWorkflowFixture();
        try {
            assertSqlState(
                    "23503",
                    () -> insertLeadAssignment(
                            UUID.randomUUID(), fixture.firstUserId(), null));
            assertSqlState(
                    "23503",
                    () -> insertLeadStatusHistory(
                            fixture.leadId(),
                            null,
                            UUID.randomUUID(),
                            fixture.firstUserId()));
            assertSqlState("23503", () -> insertLeadTask(fixture, UUID.randomUUID()));
            assertSqlState(
                    "23503",
                    () -> insertTour(
                            fixture,
                            UUID.randomUUID(),
                            fixture.firstUserId(),
                            null));
            assertSqlState(
                    "23503",
                    () -> insertLeadDuplicate(fixture.leadId(), UUID.randomUUID()));

            assertThat(countAssignments(fixture.leadId())).isZero();
        } finally {
            deleteWorkflowFixture(fixture);
        }
    }

    @Test
    void rejectsASecondPrimaryPhoneForTheSameLead() {
        LeadFixture fixture = createLeadFixture();
        try {
            insertLeadPhone(fixture.leadId(), genericPhone(), true);

            assertSqlState(
                    "23505", () -> insertLeadPhone(fixture.leadId(), genericPhone(), true));

            assertThat(countLeadPhones(fixture.leadId(), true)).isEqualTo(1);
        } finally {
            deleteLeadFixture(fixture);
        }
    }

    @Test
    void permitsSupportedPrimaryAndNonPrimaryPhoneCombinations() {
        LeadFixture fixture = createLeadFixture();
        UUID secondLeadId = UUID.randomUUID();
        try {
            insertLead(
                    secondLeadId,
                    fixture.branchId(),
                    fixture.sourceId(),
                    fixture.statusId());

            insertLeadPhone(fixture.leadId(), genericPhone(), false);
            insertLeadPhone(fixture.leadId(), genericPhone(), false);
            assertThat(countLeadPhones(fixture.leadId(), false)).isEqualTo(2);
            assertThat(countLeadPhones(fixture.leadId(), true)).isZero();

            insertLeadPhone(fixture.leadId(), genericPhone(), true);
            insertLeadPhone(secondLeadId, genericPhone(), true);
            assertThat(countAllLeadPhones(fixture.leadId())).isEqualTo(3);
            assertThat(countLeadPhones(fixture.leadId(), true)).isEqualTo(1);
            assertThat(countLeadPhones(secondLeadId, true)).isEqualTo(1);
        } finally {
            deleteLeadFixture(fixture);
        }
    }

    @Test
    void permitsTheSameNormalizedPhoneWithinAndAcrossOrganizations() {
        LeadFixture firstFixture = createLeadFixture();
        LeadFixture secondFixture = createLeadFixture();
        UUID sameOrganizationLeadId = UUID.randomUUID();
        String normalizedPhone = genericPhone();
        try {
            insertLead(
                    sameOrganizationLeadId,
                    firstFixture.branchId(),
                    firstFixture.sourceId(),
                    firstFixture.statusId());
            insertLeadPhone(firstFixture.leadId(), normalizedPhone, false);
            insertLeadPhone(sameOrganizationLeadId, normalizedPhone, false);
            insertLeadPhone(secondFixture.leadId(), normalizedPhone, false);

            Integer phoneCount = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM lead_phones WHERE normalized_phone = ?",
                    Integer.class,
                    normalizedPhone);
            assertThat(phoneCount).isEqualTo(3);
        } finally {
            deleteLeadFixture(firstFixture);
            deleteLeadFixture(secondFixture);
        }
    }

    @Test
    void rejectsAllRequiredCrmLeadCoreForeignKeyViolations() {
        LeadFixture fixture = createLeadFixture();
        try {
            assertSqlState(
                    "23503",
                    () -> insertLead(
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            fixture.sourceId(),
                            fixture.statusId()));
            assertSqlState(
                    "23503",
                    () -> insertLead(
                            UUID.randomUUID(),
                            fixture.branchId(),
                            UUID.randomUUID(),
                            fixture.statusId()));
            assertSqlState(
                    "23503",
                    () -> insertLead(
                            UUID.randomUUID(),
                            fixture.branchId(),
                            fixture.sourceId(),
                            UUID.randomUUID()));
            assertSqlState(
                    "23503", () -> insertLeadPhone(UUID.randomUUID(), genericPhone(), false));
            assertSqlState("23503", () -> insertProspectiveChild(UUID.randomUUID()));

            Integer leadCount = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM leads WHERE branch_id = ?",
                    Integer.class,
                    fixture.branchId());
            assertThat(leadCount).isEqualTo(1);
        } finally {
            deleteLeadFixture(fixture);
        }
    }

    @Test
    void permitsMultipleProspectiveChildrenForOneLead() {
        LeadFixture fixture = createLeadFixture();
        try {
            insertProspectiveChild(fixture.leadId());
            insertProspectiveChild(fixture.leadId());

            Integer childCount = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM prospective_children WHERE lead_id = ?",
                    Integer.class,
                    fixture.leadId());
            assertThat(childCount).isEqualTo(2);
        } finally {
            deleteLeadFixture(fixture);
        }
    }

    @Test
    void rejectsDuplicateOrganizationScopedCrmReferenceCodes() {
        for (String tableName : organizationScopedCrmReferenceTables()) {
            UUID organizationId = createOrganization();
            String code = "DUPLICATE_" + UUID.randomUUID();
            try {
                insertOrganizationScopedCrmReference(tableName, organizationId, code);
                assertSqlState(
                        "23505",
                        () -> insertOrganizationScopedCrmReference(tableName, organizationId, code));
                assertThat(countOrganizationScopedCrmReference(tableName, code)).isEqualTo(1);
            } finally {
                deleteCrmReferenceRows(tableName, code);
                deleteOrganization(organizationId);
            }
        }
    }

    @Test
    void permitsOrganizationScopedCrmReferenceCodesAcrossOrganizations() {
        for (String tableName : organizationScopedCrmReferenceTables()) {
            UUID firstOrganizationId = createOrganization();
            UUID secondOrganizationId = createOrganization();
            String code = "SHARED_" + UUID.randomUUID();
            try {
                insertOrganizationScopedCrmReference(tableName, firstOrganizationId, code);
                insertOrganizationScopedCrmReference(tableName, secondOrganizationId, code);
                assertThat(countOrganizationScopedCrmReference(tableName, code)).isEqualTo(2);
            } finally {
                deleteCrmReferenceRows(tableName, code);
                deleteOrganization(firstOrganizationId);
                deleteOrganization(secondOrganizationId);
            }
        }
    }

    @Test
    void enforcesGlobalCrmReferenceCodeUniquenessAndAllowsDifferentCodes() {
        for (String tableName : globalCrmReferenceTables()) {
            String firstCode = "GLOBAL_FIRST_" + UUID.randomUUID();
            String secondCode = "GLOBAL_SECOND_" + UUID.randomUUID();
            try {
                insertGlobalCrmReference(tableName, firstCode);
                assertSqlState("23505", () -> insertGlobalCrmReference(tableName, firstCode));
                insertGlobalCrmReference(tableName, secondCode);
                assertThat(countGlobalCrmReferences(tableName, firstCode, secondCode)).isEqualTo(2);
            } finally {
                deleteCrmReferenceRows(tableName, firstCode);
                deleteCrmReferenceRows(tableName, secondCode);
            }
        }
    }

    @Test
    void rejectsDuplicateOrganizationScopeDepartment() {
        UUID organizationId = createOrganization();

        jdbcTemplate.update(
                """
                INSERT INTO departments (
                    id, organization_id, branch_id, code, name, created_at, updated_at)
                VALUES (?, ?, NULL, 'OPERATIONS', 'Operations', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                UUID.randomUUID(),
                organizationId);

        assertSqlState("23505", () -> jdbcTemplate.update(
                """
                INSERT INTO departments (
                    id, organization_id, branch_id, code, name, created_at, updated_at)
                VALUES (?, ?, NULL, 'OPERATIONS', 'Duplicate', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                UUID.randomUUID(),
                organizationId));
    }

    @Test
    void permitsDepartmentCodeAcrossOrganizationsAndConcreteBranches() {
        String organizationScopeCode = "DEPT_" + UUID.randomUUID();
        UUID firstOrganizationId = createOrganization();
        UUID secondOrganizationId = createOrganization();

        insertDepartment(firstOrganizationId, null, organizationScopeCode);
        insertDepartment(secondOrganizationId, null, organizationScopeCode);

        Integer organizationScopeCount = jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                  FROM departments
                 WHERE code = ?
                   AND branch_id IS NULL
                   AND organization_id IN (?, ?)
                """,
                Integer.class,
                organizationScopeCode,
                firstOrganizationId,
                secondOrganizationId);
        assertThat(organizationScopeCount)
                .as("the same department code must coexist in different organizations")
                .isEqualTo(2);

        String branchScopeCode = "BRANCH_DEPT_" + UUID.randomUUID();
        UUID branchOrganizationId = createOrganization();
        UUID firstBranchId = createBranch(branchOrganizationId);
        UUID secondBranchId = createBranch(branchOrganizationId);

        insertDepartment(branchOrganizationId, firstBranchId, branchScopeCode);
        insertDepartment(branchOrganizationId, secondBranchId, branchScopeCode);

        Integer branchScopeCount = jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                  FROM departments
                 WHERE organization_id = ?
                   AND code = ?
                   AND branch_id IN (?, ?)
                """,
                Integer.class,
                branchOrganizationId,
                branchScopeCode,
                firstBranchId,
                secondBranchId);
        assertThat(branchScopeCount)
                .as("the same department code must coexist in different concrete branches")
                .isEqualTo(2);
    }

    @Test
    void rejectsDuplicateDepartmentWithinConcreteBranch() {
        UUID organizationId = createOrganization();
        UUID branchId = createBranch(organizationId);
        String code = "DEPT_" + UUID.randomUUID();

        insertDepartment(organizationId, branchId, code);

        assertSqlState("23505", () -> insertDepartment(organizationId, branchId, code));
    }

    @Test
    void rejectsDuplicateOrganizationScopeApplicationSetting() {
        UUID organizationId = createOrganization();

        jdbcTemplate.update(
                """
                INSERT INTO application_settings (
                    id, organization_id, branch_id, setting_key, value_type, updated_at)
                VALUES (?, ?, NULL, 'locale.default', 'STRING', CURRENT_TIMESTAMP)
                """,
                UUID.randomUUID(),
                organizationId);

        assertSqlState("23505", () -> jdbcTemplate.update(
                """
                INSERT INTO application_settings (
                    id, organization_id, branch_id, setting_key, value_type, updated_at)
                VALUES (?, ?, NULL, 'locale.default', 'STRING', CURRENT_TIMESTAMP)
                """,
                UUID.randomUUID(),
                organizationId));
    }

    @Test
    void permitsApplicationSettingKeyAcrossOrganizationsAndConcreteBranches() {
        String organizationScopeKey = "test.setting." + UUID.randomUUID();
        UUID firstOrganizationId = createOrganization();
        UUID secondOrganizationId = createOrganization();

        insertApplicationSetting(firstOrganizationId, null, organizationScopeKey);
        insertApplicationSetting(secondOrganizationId, null, organizationScopeKey);

        Integer organizationScopeCount = jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                  FROM application_settings
                 WHERE setting_key = ?
                   AND branch_id IS NULL
                   AND organization_id IN (?, ?)
                """,
                Integer.class,
                organizationScopeKey,
                firstOrganizationId,
                secondOrganizationId);
        assertThat(organizationScopeCount)
                .as("the same setting key must coexist in different organizations")
                .isEqualTo(2);

        String branchScopeKey = "test.branch.setting." + UUID.randomUUID();
        UUID branchOrganizationId = createOrganization();
        UUID firstBranchId = createBranch(branchOrganizationId);
        UUID secondBranchId = createBranch(branchOrganizationId);

        insertApplicationSetting(branchOrganizationId, firstBranchId, branchScopeKey);
        insertApplicationSetting(branchOrganizationId, secondBranchId, branchScopeKey);

        Integer branchScopeCount = jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                  FROM application_settings
                 WHERE organization_id = ?
                   AND setting_key = ?
                   AND branch_id IN (?, ?)
                """,
                Integer.class,
                branchOrganizationId,
                branchScopeKey,
                firstBranchId,
                secondBranchId);
        assertThat(branchScopeCount)
                .as("the same setting key must coexist in different concrete branches")
                .isEqualTo(2);
    }

    @Test
    void rejectsDuplicateApplicationSettingWithinConcreteBranch() {
        UUID organizationId = createOrganization();
        UUID branchId = createBranch(organizationId);
        String settingKey = "test.setting." + UUID.randomUUID();

        insertApplicationSetting(organizationId, branchId, settingKey);

        assertSqlState(
                "23505",
                () -> insertApplicationSetting(organizationId, branchId, settingKey));
    }

    @Test
    void rejectsDuplicateNullScopeNumberSequence() {
        UUID organizationId = createOrganization();

        jdbcTemplate.update(
                """
                INSERT INTO number_sequences (
                    id, organization_id, branch_id, sequence_code, year, next_value, updated_at)
                VALUES (?, ?, NULL, 'DOCUMENT', NULL, 1, CURRENT_TIMESTAMP)
                """,
                UUID.randomUUID(),
                organizationId);

        assertSqlState("23505", () -> jdbcTemplate.update(
                """
                INSERT INTO number_sequences (
                    id, organization_id, branch_id, sequence_code, year, next_value, updated_at)
                VALUES (?, ?, NULL, 'DOCUMENT', NULL, 2, CURRENT_TIMESTAMP)
                """,
                UUID.randomUUID(),
                organizationId));
    }

    @Test
    void permitsNumberSequencesAcrossYearsBranchesAndNullYearScope() {
        UUID yearOrganizationId = createOrganization();
        String yearCode = "YEAR_" + UUID.randomUUID();
        insertNumberSequence(yearOrganizationId, null, yearCode, 2025);
        insertNumberSequence(yearOrganizationId, null, yearCode, 2026);

        Integer differentYearCount = jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                  FROM number_sequences
                 WHERE organization_id = ?
                   AND branch_id IS NULL
                   AND sequence_code = ?
                   AND year IN (2025, 2026)
                """,
                Integer.class,
                yearOrganizationId,
                yearCode);
        assertThat(differentYearCount)
                .as("the same sequence code must coexist across different years")
                .isEqualTo(2);

        UUID branchOrganizationId = createOrganization();
        UUID firstBranchId = createBranch(branchOrganizationId);
        UUID secondBranchId = createBranch(branchOrganizationId);
        String branchCode = "BRANCH_" + UUID.randomUUID();
        insertNumberSequence(branchOrganizationId, firstBranchId, branchCode, 2025);
        insertNumberSequence(branchOrganizationId, secondBranchId, branchCode, 2025);

        Integer differentBranchCount = jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                  FROM number_sequences
                 WHERE organization_id = ?
                   AND branch_id IN (?, ?)
                   AND sequence_code = ?
                   AND year = 2025
                """,
                Integer.class,
                branchOrganizationId,
                firstBranchId,
                secondBranchId,
                branchCode);
        assertThat(differentBranchCount)
                .as("the same sequence code and year must coexist across different branches")
                .isEqualTo(2);

        UUID nullableYearOrganizationId = createOrganization();
        UUID nullableYearBranchId = createBranch(nullableYearOrganizationId);
        String nullableYearCode = "NULL_YEAR_" + UUID.randomUUID();
        insertNumberSequence(nullableYearOrganizationId, nullableYearBranchId, nullableYearCode, null);
        insertNumberSequence(nullableYearOrganizationId, nullableYearBranchId, nullableYearCode, 2027);

        Integer nullableYearCount = jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                  FROM number_sequences
                 WHERE organization_id = ?
                   AND branch_id = ?
                   AND sequence_code = ?
                   AND (year IS NULL OR year = 2027)
                """,
                Integer.class,
                nullableYearOrganizationId,
                nullableYearBranchId,
                nullableYearCode);
        assertThat(nullableYearCount)
                .as("a NULL year and a concrete year must coexist in the same scope")
                .isEqualTo(2);
    }

    @Test
    void rejectsDuplicateNumberSequenceWithinConcreteBranchAndYear() {
        UUID organizationId = createOrganization();
        UUID branchId = createBranch(organizationId);
        String sequenceCode = "SEQUENCE_" + UUID.randomUUID();

        insertNumberSequence(organizationId, branchId, sequenceCode, 2025);

        assertSqlState(
                "23505",
                () -> insertNumberSequence(organizationId, branchId, sequenceCode, 2025));
    }

    @Test
    void rejectsDuplicateUserBranchAccess() {
        UserRoleFixture fixture = createUserRoleFixture(true);

        jdbcTemplate.update(
                """
                INSERT INTO user_branch_access (user_id, branch_id, granted_by, granted_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                """,
                fixture.userId(),
                fixture.branchId(),
                fixture.userId());

        assertSqlState("23505", () -> jdbcTemplate.update(
                """
                INSERT INTO user_branch_access (user_id, branch_id, granted_by, granted_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                """,
                fixture.userId(),
                fixture.branchId(),
                fixture.userId()));
    }

    @Test
    void rejectsDuplicateCurrentUserRoleWithNullBranch() {
        UserRoleFixture fixture = createUserRoleFixture(false);
        insertCurrentUserRole(UUID.randomUUID(), fixture, null);

        assertSqlState("23505", () -> insertCurrentUserRole(UUID.randomUUID(), fixture, null));
    }

    @Test
    void rejectsDuplicateCurrentUserRoleWithConcreteBranch() {
        UserRoleFixture fixture = createUserRoleFixture(true);
        insertCurrentUserRole(UUID.randomUUID(), fixture, fixture.branchId());

        assertSqlState(
                "23505",
                () -> insertCurrentUserRole(UUID.randomUUID(), fixture, fixture.branchId()));
    }

    @Test
    void permitsCurrentUserRoleAfterHistoricalAssignment() {
        UserRoleFixture fixture = createUserRoleFixture(true);
        insertHistoricalUserRole(UUID.randomUUID(), fixture, fixture.branchId());
        insertCurrentUserRole(UUID.randomUUID(), fixture, fixture.branchId());

        Integer assignmentCount = jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                  FROM user_roles
                 WHERE user_id = ?
                   AND role_id = ?
                   AND branch_id = ?
                """,
                Integer.class,
                fixture.userId(),
                fixture.roleId(),
                fixture.branchId());
        Integer currentAssignmentCount = jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                  FROM user_roles
                 WHERE user_id = ?
                   AND role_id = ?
                   AND branch_id = ?
                   AND valid_until IS NULL
                """,
                Integer.class,
                fixture.userId(),
                fixture.roleId(),
                fixture.branchId());

        assertThat(assignmentCount).isEqualTo(2);
        assertThat(currentAssignmentCount).isEqualTo(1);
    }

    @Test
    void rejectsAuditLogUpdate() {
        AuditFixture fixture = createAuditFixture();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE audit_logs SET action = 'MODIFIED' WHERE id = ?",
                fixture.auditLogId()))
                .isInstanceOfSatisfying(DataAccessException.class, exception -> {
                    assertSqlState("55000", exception);
                    assertThat(exception).hasMessageContaining("append-only");
                });
    }

    @Test
    void rejectsAuditLogDeleteAndPreservesRow() {
        AuditFixture fixture = createAuditFixture();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "DELETE FROM audit_logs WHERE id = ?",
                fixture.auditLogId()))
                .isInstanceOfSatisfying(DataAccessException.class, exception -> {
                    assertSqlState("55000", exception);
                    assertThat(exception).hasMessageContaining("append-only");
                });

        Integer rowCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE id = ?",
                Integer.class,
                fixture.auditLogId());
        assertThat(rowCount).isEqualTo(1);
    }

    private Flyway flywayForSchema(String schemaName, MigrationVersion target) {
        return Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .defaultSchema(schemaName)
                .schemas(schemaName)
                .target(target)
                .load();
    }

    private List<CoreReferenceSeed> queryCoreReferenceSeeds() {
        return jdbcTemplate.query(
                """
                SELECT table_name, id, code, name, is_active, sort_order, applies_to, is_final
                  FROM (
                        SELECT 1 AS table_order,
                               CASE code WHEN 'UZ' THEN 1 WHEN 'RU' THEN 2 ELSE 999 END AS row_order,
                               'languages' AS table_name,
                               id, code, name, is_active, sort_order,
                               NULL::VARCHAR AS applies_to,
                               NULL::BOOLEAN AS is_final
                          FROM languages
                        UNION ALL
                        SELECT 2,
                               CASE code WHEN 'MALE' THEN 1 WHEN 'FEMALE' THEN 2 ELSE 999 END,
                               'gender_types',
                               id, code, name, is_active, NULL::INTEGER,
                               NULL::VARCHAR, NULL::BOOLEAN
                          FROM gender_types
                        UNION ALL
                        SELECT 3,
                               CASE code
                                   WHEN 'FATHER' THEN 1
                                   WHEN 'MOTHER' THEN 2
                                   WHEN 'GUARDIAN' THEN 3
                                   ELSE 999
                               END,
                               'relationship_types',
                               id, code, name, is_active, NULL::INTEGER,
                               NULL::VARCHAR, NULL::BOOLEAN
                          FROM relationship_types
                        UNION ALL
                        SELECT 4,
                               CASE code
                                   WHEN 'BIRTH_CERTIFICATE' THEN 1
                                   WHEN 'PASSPORT' THEN 2
                                   WHEN 'ID_CARD' THEN 3
                                   WHEN 'PINFL' THEN 4
                                   WHEN 'MEDICAL_CERTIFICATE' THEN 5
                                   WHEN 'PHOTO' THEN 6
                                   ELSE 999
                               END,
                               'document_types',
                               id, code, name, is_active, NULL::INTEGER,
                               applies_to, NULL::BOOLEAN
                          FROM document_types
                        UNION ALL
                        SELECT 5,
                               CASE code
                                   WHEN 'PENDING' THEN 1
                                   WHEN 'VERIFIED' THEN 2
                                   WHEN 'REJECTED' THEN 3
                                   ELSE 999
                               END,
                               'document_verification_statuses',
                               id, code, name, is_active, NULL::INTEGER,
                               NULL::VARCHAR, is_final
                          FROM document_verification_statuses
                       ) approved_core_references
                 ORDER BY table_order, row_order
                """,
                (resultSet, rowNumber) -> new CoreReferenceSeed(
                        resultSet.getString("table_name"),
                        resultSet.getObject("id", UUID.class),
                        resultSet.getString("code"),
                        resultSet.getString("name"),
                        resultSet.getBoolean("is_active"),
                        resultSet.getObject("sort_order", Integer.class),
                        resultSet.getString("applies_to"),
                        resultSet.getObject("is_final", Boolean.class)));
    }

    private List<ReferenceSeed> queryReferenceSeeds(String tableName) {
        return jdbcTemplate.query(
                "SELECT id, code, name FROM "
                        + validatedTelephonyReferenceTable(tableName)
                        + " ORDER BY code",
                (resultSet, rowNumber) -> new ReferenceSeed(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getString("code"),
                        resultSet.getString("name")));
    }

    private List<FlaggedReferenceSeed> queryFlaggedReferenceSeeds(
            String tableName, String flagColumn) {
        String validatedFlagColumn = switch (tableName) {
            case "call_dispositions" -> {
                if (!"is_missed".equals(flagColumn)) {
                    throw new IllegalArgumentException("Unexpected call disposition flag");
                }
                yield flagColumn;
            }
            case "webhook_statuses" -> {
                if (!"is_final".equals(flagColumn)) {
                    throw new IllegalArgumentException("Unexpected webhook status flag");
                }
                yield flagColumn;
            }
            default -> throw new IllegalArgumentException(
                    "Unexpected flagged reference table: " + tableName);
        };
        return jdbcTemplate.query(
                "SELECT id, code, name, " + validatedFlagColumn + " AS flag FROM "
                        + validatedTelephonyReferenceTable(tableName)
                        + " ORDER BY code",
                (resultSet, rowNumber) -> new FlaggedReferenceSeed(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getString("code"),
                        resultSet.getString("name"),
                        resultSet.getBoolean("flag")));
    }

    private ReferenceSeed referenceSeed(String id, String code, String name) {
        return new ReferenceSeed(UUID.fromString(id), code, name);
    }

    private CoreReferenceSeed coreReferenceSeed(
            String tableName,
            String id,
            String code,
            String name,
            boolean active,
            Integer sortOrder,
            String appliesTo,
            Boolean finalStatus) {
        return new CoreReferenceSeed(
                tableName,
                UUID.fromString(id),
                code,
                name,
                active,
                sortOrder,
                appliesTo,
                finalStatus);
    }

    private FlaggedReferenceSeed flaggedReferenceSeed(
            String id, String code, String name, boolean flag) {
        return new FlaggedReferenceSeed(UUID.fromString(id), code, name, flag);
    }

    private List<ApprovedReference> approvedReferences() {
        return List.of(
                new ApprovedReference("lead_task_statuses", "OPEN"),
                new ApprovedReference("lead_task_statuses", "IN_PROGRESS"),
                new ApprovedReference("lead_task_statuses", "COMPLETED"),
                new ApprovedReference("lead_task_statuses", "CANCELLED"),
                new ApprovedReference("lead_activity_types", "CALL"),
                new ApprovedReference("lead_activity_types", "NOTE"),
                new ApprovedReference("lead_activity_types", "STATUS_CHANGE"),
                new ApprovedReference("lead_activity_types", "ASSIGNMENT"),
                new ApprovedReference("lead_activity_types", "TOUR"),
                new ApprovedReference("lead_activity_types", "SYSTEM"),
                new ApprovedReference("call_directions", "INBOUND"),
                new ApprovedReference("call_directions", "OUTBOUND"),
                new ApprovedReference("call_dispositions", "ANSWERED"),
                new ApprovedReference("call_dispositions", "MISSED"),
                new ApprovedReference("call_dispositions", "BUSY"),
                new ApprovedReference("call_dispositions", "REJECTED"),
                new ApprovedReference("call_dispositions", "FAILED"),
                new ApprovedReference("call_dispositions", "NO_ANSWER"),
                new ApprovedReference("call_event_types", "STARTED"),
                new ApprovedReference("call_event_types", "RINGING"),
                new ApprovedReference("call_event_types", "ANSWERED"),
                new ApprovedReference("call_event_types", "ENDED"),
                new ApprovedReference("call_event_types", "RECORDING_AVAILABLE"),
                new ApprovedReference("webhook_statuses", "RECEIVED"),
                new ApprovedReference("webhook_statuses", "PROCESSING"),
                new ApprovedReference("webhook_statuses", "PROCESSED"),
                new ApprovedReference("webhook_statuses", "FAILED"),
                new ApprovedReference("webhook_statuses", "IGNORED"));
    }

    private void insertGlobalReference(String tableName, UUID id, String code) {
        jdbcTemplate.update(
                "INSERT INTO " + validatedGlobalReferenceTable(tableName)
                        + " (id, code, name) VALUES (?, ?, 'Integration test reference')",
                id,
                code);
    }

    private String validatedGlobalReferenceTable(String tableName) {
        if (globalCrmReferenceTables().contains(tableName)
                || telephonyReferenceTables().contains(tableName)) {
            return tableName;
        }
        throw new IllegalArgumentException("Unexpected global reference table: " + tableName);
    }

    private List<String> organizationScopedCrmReferenceTables() {
        return List.of("lead_sources", "lead_statuses", "lost_reasons", "tour_outcomes");
    }

    private List<String> globalCrmReferenceTables() {
        return List.of("lead_task_statuses", "lead_activity_types");
    }

    private void insertOrganizationScopedCrmReference(
            String tableName, UUID organizationId, String code) {
        switch (tableName) {
            case "lead_sources" -> jdbcTemplate.update(
                    """
                    INSERT INTO lead_sources (
                        id, organization_id, code, name, source_type)
                    VALUES (?, ?, ?, 'Integration test source', 'INTEGRATION_TEST')
                    """,
                    UUID.randomUUID(),
                    organizationId,
                    code);
            case "lead_statuses" -> jdbcTemplate.update(
                    """
                    INSERT INTO lead_statuses (
                        id, organization_id, code, name, pipeline_order)
                    VALUES (?, ?, ?, 'Integration test status', 1)
                    """,
                    UUID.randomUUID(),
                    organizationId,
                    code);
            case "lost_reasons" -> jdbcTemplate.update(
                    """
                    INSERT INTO lost_reasons (id, organization_id, code, name)
                    VALUES (?, ?, ?, 'Integration test lost reason')
                    """,
                    UUID.randomUUID(),
                    organizationId,
                    code);
            case "tour_outcomes" -> jdbcTemplate.update(
                    """
                    INSERT INTO tour_outcomes (id, organization_id, code, name)
                    VALUES (?, ?, ?, 'Integration test tour outcome')
                    """,
                    UUID.randomUUID(),
                    organizationId,
                    code);
            default -> throw new IllegalArgumentException("Unexpected CRM reference table: " + tableName);
        }
    }

    private void insertGlobalCrmReference(String tableName, String code) {
        switch (tableName) {
            case "lead_task_statuses" -> jdbcTemplate.update(
                    """
                    INSERT INTO lead_task_statuses (id, code, name)
                    VALUES (?, ?, 'Integration test task status')
                    """,
                    UUID.randomUUID(),
                    code);
            case "lead_activity_types" -> jdbcTemplate.update(
                    """
                    INSERT INTO lead_activity_types (id, code, name)
                    VALUES (?, ?, 'Integration test activity type')
                    """,
                    UUID.randomUUID(),
                    code);
            default -> throw new IllegalArgumentException("Unexpected CRM reference table: " + tableName);
        }
    }

    private int countOrganizationScopedCrmReference(String tableName, String code) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM " + validatedCrmReferenceTable(tableName) + " WHERE code = ?",
                Integer.class,
                code);
    }

    private int countGlobalCrmReferences(String tableName, String firstCode, String secondCode) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM "
                        + validatedCrmReferenceTable(tableName)
                        + " WHERE code IN (?, ?)",
                Integer.class,
                firstCode,
                secondCode);
    }

    private void deleteCrmReferenceRows(String tableName, String code) {
        jdbcTemplate.update(
                "DELETE FROM " + validatedCrmReferenceTable(tableName) + " WHERE code = ?", code);
    }

    private String validatedCrmReferenceTable(String tableName) {
        if (organizationScopedCrmReferenceTables().contains(tableName)
                || globalCrmReferenceTables().contains(tableName)) {
            return tableName;
        }
        throw new IllegalArgumentException("Unexpected CRM reference table: " + tableName);
    }

    private void deleteOrganization(UUID organizationId) {
        jdbcTemplate.update("DELETE FROM organizations WHERE id = ?", organizationId);
    }

    private UUID createOrganization() {
        UUID organizationId = UUID.randomUUID();

        jdbcTemplate.update(
                """
                INSERT INTO organizations (id, code, name, created_at, updated_at)
                VALUES (?, ?, 'Integration test organization', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                organizationId,
                "ORG_" + organizationId);

        return organizationId;
    }

    private UUID createBranch(UUID organizationId) {
        UUID branchId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO branches (id, organization_id, code, name, created_at, updated_at)
                VALUES (?, ?, ?, 'Integration test branch', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                branchId,
                organizationId,
                "BRANCH_" + branchId);
        return branchId;
    }

    private LeadFixture createLeadFixture() {
        UUID organizationId = createOrganization();
        UUID branchId = createBranch(organizationId);
        UUID sourceId = UUID.randomUUID();
        UUID statusId = UUID.randomUUID();
        UUID leadId = UUID.randomUUID();

        jdbcTemplate.update(
                """
                INSERT INTO lead_sources (
                    id, organization_id, code, name, source_type)
                VALUES (?, ?, ?, 'Integration test source', 'INTEGRATION_TEST')
                """,
                sourceId,
                organizationId,
                "SOURCE_" + sourceId);
        jdbcTemplate.update(
                """
                INSERT INTO lead_statuses (
                    id, organization_id, code, name, pipeline_order)
                VALUES (?, ?, ?, 'Integration test status', 1)
                """,
                statusId,
                organizationId,
                "STATUS_" + statusId);
        insertLead(leadId, branchId, sourceId, statusId);

        return new LeadFixture(organizationId, branchId, sourceId, statusId, leadId);
    }

    private List<String> telephonyReferenceTables() {
        return List.of(
                "call_directions", "call_dispositions", "call_event_types", "webhook_statuses");
    }

    private void insertTelephonyReference(String tableName, String code) {
        jdbcTemplate.update(
                "INSERT INTO "
                        + validatedTelephonyReferenceTable(tableName)
                        + " (id, code, name) VALUES (?, ?, 'Generic reference')",
                UUID.randomUUID(),
                code);
    }

    private int countTelephonyReferences(
            String tableName, String firstCode, String secondCode) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM "
                        + validatedTelephonyReferenceTable(tableName)
                        + " WHERE code IN (?, ?)",
                Integer.class,
                firstCode,
                secondCode);
    }

    private void deleteTelephonyReferences(
            String tableName, String firstCode, String secondCode) {
        jdbcTemplate.update(
                "DELETE FROM "
                        + validatedTelephonyReferenceTable(tableName)
                        + " WHERE code IN (?, ?)",
                firstCode,
                secondCode);
    }

    private String validatedTelephonyReferenceTable(String tableName) {
        if (telephonyReferenceTables().contains(tableName)) {
            return tableName;
        }
        throw new IllegalArgumentException("Unexpected telephony reference table: " + tableName);
    }

    private TelephonyFixture createTelephonyFixture() {
        UUID organizationId = createOrganization();
        UUID branchId = createBranch(organizationId);
        UUID firstPbxId = insertPbxConfig(organizationId, branchId);
        UUID secondPbxId = insertPbxConfig(organizationId, null);
        return new TelephonyFixture(organizationId, branchId, firstPbxId, secondPbxId);
    }

    private UUID insertPbxConfig(UUID organizationId, UUID branchId) {
        UUID pbxConfigId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO pbx_configs (
                    id, organization_id, branch_id, name, base_url,
                    credential_secret_reference, created_at, updated_at)
                VALUES (
                    ?, ?, ?, 'Generic PBX configuration', ?, ?,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                pbxConfigId,
                organizationId,
                branchId,
                "https://example.invalid/pbx/" + pbxConfigId,
                "external-secret-reference-" + pbxConfigId);
        return pbxConfigId;
    }

    private UUID insertExtension(UUID pbxConfigId, String extensionNumber) {
        return insertExtension(pbxConfigId, null, extensionNumber);
    }

    private UUID insertExtension(UUID pbxConfigId, UUID userId, String extensionNumber) {
        UUID extensionId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO extensions (
                    id, pbx_config_id, user_id, extension_number, created_at, updated_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                extensionId,
                pbxConfigId,
                userId,
                extensionNumber);
        return extensionId;
    }

    private void insertSipAccount(UUID pbxConfigId, UUID extensionId, String sipUsername) {
        UUID accountId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO sip_accounts (
                    id, pbx_config_id, extension_id, sip_username, secret_reference,
                    created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                accountId,
                pbxConfigId,
                extensionId,
                sipUsername,
                "external-secret-reference-" + accountId);
    }

    private void insertPhoneNumber(UUID pbxConfigId, String normalizedNumber) {
        jdbcTemplate.update(
                """
                INSERT INTO phone_numbers (
                    id, pbx_config_id, normalized_number, display_number)
                VALUES (?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                pbxConfigId,
                normalizedNumber,
                normalizedNumber);
    }

    private int countRowsForPbx(String tableName, UUID pbxConfigId) {
        String validatedTable = switch (tableName) {
            case "extensions", "sip_accounts", "phone_numbers", "call_sessions", "webhook_events" ->
                tableName;
            default -> throw new IllegalArgumentException("Unexpected PBX child table: " + tableName);
        };
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM " + validatedTable + " WHERE pbx_config_id = ?",
                Integer.class,
                pbxConfigId);
    }

    private String genericTelephonyValue(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }

    private void deleteTelephonyFixture(TelephonyFixture fixture) {
        jdbcTemplate.update(
                "DELETE FROM phone_numbers WHERE pbx_config_id IN (?, ?)",
                fixture.firstPbxId(),
                fixture.secondPbxId());
        jdbcTemplate.update(
                "DELETE FROM sip_accounts WHERE pbx_config_id IN (?, ?)",
                fixture.firstPbxId(),
                fixture.secondPbxId());
        jdbcTemplate.update(
                "DELETE FROM extensions WHERE pbx_config_id IN (?, ?)",
                fixture.firstPbxId(),
                fixture.secondPbxId());
        jdbcTemplate.update(
                "DELETE FROM pbx_configs WHERE id IN (?, ?)",
                fixture.firstPbxId(),
                fixture.secondPbxId());
        jdbcTemplate.update("DELETE FROM branches WHERE id = ?", fixture.branchId());
        deleteOrganization(fixture.organizationId());
    }

    private TelephonyCallFixture createTelephonyCallFixture() {
        TelephonyFixture telephonyFixture = createTelephonyFixture();
        UUID directionId = insertTelephonyReferenceReturningId("call_directions");
        UUID eventTypeId = insertTelephonyReferenceReturningId("call_event_types");
        UUID webhookStatusId = insertTelephonyReferenceReturningId("webhook_statuses");
        return new TelephonyCallFixture(
                telephonyFixture, directionId, eventTypeId, webhookStatusId);
    }

    private UUID insertTelephonyReferenceReturningId(String tableName) {
        UUID referenceId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO "
                        + validatedTelephonyReferenceTable(tableName)
                        + " (id, code, name) VALUES (?, ?, 'Generic reference')",
                referenceId,
                genericTelephonyValue("REF"));
        return referenceId;
    }

    private UUID insertCallSession(UUID pbxConfigId, UUID directionId, String externalCallId) {
        UUID callSessionId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO call_sessions (
                    id, pbx_config_id, external_call_id, direction_id,
                    from_normalized_number, to_normalized_number, started_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                callSessionId,
                pbxConfigId,
                externalCallId,
                directionId,
                genericTelephonyValue("FROM"),
                genericTelephonyValue("TO"));
        return callSessionId;
    }

    private UUID insertApplicationInvalidCallSession(UUID pbxConfigId, UUID directionId) {
        UUID callSessionId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO call_sessions (
                    id, pbx_config_id, external_call_id, direction_id,
                    from_normalized_number, to_normalized_number, started_at,
                    answered_at, ended_at, duration_seconds, created_at)
                VALUES (
                    ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP - INTERVAL '1 hour',
                    CURRENT_TIMESTAMP - INTERVAL '2 hours', -1, CURRENT_TIMESTAMP)
                """,
                callSessionId,
                pbxConfigId,
                genericTelephonyValue("CALL"),
                directionId,
                genericTelephonyValue("FROM"),
                genericTelephonyValue("TO"));
        return callSessionId;
    }

    private void insertCallParticipant(UUID callSessionId, String participantRole) {
        jdbcTemplate.update(
                """
                INSERT INTO call_participants (id, call_session_id, participant_role)
                VALUES (?, ?, ?)
                """,
                UUID.randomUUID(),
                callSessionId,
                participantRole);
    }

    private void insertCallEvent(
            UUID callSessionId, String externalEventId, UUID eventTypeId, String sanitizedMetadata) {
        jdbcTemplate.update(
                """
                INSERT INTO call_events (
                    id, call_session_id, external_event_id, event_type_id,
                    occurred_at, sanitized_metadata, created_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CAST(? AS JSONB), CURRENT_TIMESTAMP)
                """,
                UUID.randomUUID(),
                callSessionId,
                externalEventId,
                eventTypeId,
                sanitizedMetadata);
    }

    private void insertCallRecording(
            UUID callSessionId, UUID storedFileId, String externalRecordingId, String recordingUrl) {
        jdbcTemplate.update(
                """
                INSERT INTO call_recordings (
                    id, call_session_id, stored_file_id, external_recording_id,
                    recording_url, created_at)
                VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """,
                UUID.randomUUID(),
                callSessionId,
                storedFileId,
                externalRecordingId,
                recordingUrl);
    }

    private UUID insertGenericStoredFile(UUID organizationId, UUID branchId) {
        UUID storedFileId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO stored_files (
                    id, organization_id, branch_id, storage_provider, bucket_name,
                    object_key, original_file_name, content_type, file_size, uploaded_at)
                VALUES (
                    ?, ?, ?, 'TEST_ONLY', 'generic-test-bucket', ?,
                    'generic-recording.bin', 'application/octet-stream', 0, CURRENT_TIMESTAMP)
                """,
                storedFileId,
                organizationId,
                branchId,
                "generic-object-" + storedFileId);
        return storedFileId;
    }

    private void insertLeadCall(UUID leadId, UUID callSessionId) {
        jdbcTemplate.update(
                """
                INSERT INTO lead_calls (lead_id, call_session_id, linked_at)
                VALUES (?, ?, CURRENT_TIMESTAMP)
                """,
                leadId,
                callSessionId);
    }

    private void insertWebhookEvent(
            UUID pbxConfigId,
            UUID statusId,
            String externalEventId,
            String payloadHash) {
        jdbcTemplate.update(
                """
                INSERT INTO webhook_events (
                    id, pbx_config_id, external_event_id, status_id,
                    payload_hash, received_at)
                VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """,
                UUID.randomUUID(),
                pbxConfigId,
                externalEventId,
                statusId,
                payloadHash);
    }

    private void deleteTelephonyCallFixture(TelephonyCallFixture fixture) {
        deleteTelephonyCallRows(fixture);
        deleteTelephonyCallParents(fixture);
    }

    private void deleteTelephonyCallRows(TelephonyCallFixture fixture) {
        UUID firstPbxId = fixture.telephonyFixture().firstPbxId();
        UUID secondPbxId = fixture.telephonyFixture().secondPbxId();
        jdbcTemplate.update(
                """
                DELETE FROM lead_calls
                 WHERE call_session_id IN (
                     SELECT id FROM call_sessions WHERE pbx_config_id IN (?, ?))
                """,
                firstPbxId,
                secondPbxId);
        jdbcTemplate.update(
                """
                DELETE FROM call_participants
                 WHERE call_session_id IN (
                     SELECT id FROM call_sessions WHERE pbx_config_id IN (?, ?))
                """,
                firstPbxId,
                secondPbxId);
        jdbcTemplate.update(
                """
                DELETE FROM call_events
                 WHERE call_session_id IN (
                     SELECT id FROM call_sessions WHERE pbx_config_id IN (?, ?))
                """,
                firstPbxId,
                secondPbxId);
        jdbcTemplate.update(
                """
                DELETE FROM call_recordings
                 WHERE call_session_id IN (
                     SELECT id FROM call_sessions WHERE pbx_config_id IN (?, ?))
                """,
                firstPbxId,
                secondPbxId);
        jdbcTemplate.update(
                "DELETE FROM call_sessions WHERE pbx_config_id IN (?, ?)",
                firstPbxId,
                secondPbxId);
        jdbcTemplate.update(
                "DELETE FROM webhook_events WHERE pbx_config_id IN (?, ?)",
                firstPbxId,
                secondPbxId);
    }

    private void deleteTelephonyCallParents(TelephonyCallFixture fixture) {
        jdbcTemplate.update("DELETE FROM call_directions WHERE id = ?", fixture.directionId());
        jdbcTemplate.update("DELETE FROM call_event_types WHERE id = ?", fixture.eventTypeId());
        jdbcTemplate.update("DELETE FROM webhook_statuses WHERE id = ?", fixture.webhookStatusId());
        deleteTelephonyFixture(fixture.telephonyFixture());
    }

    private WorkflowFixture createWorkflowFixture() {
        LeadFixture leadFixture = createLeadFixture();
        UUID secondLeadId = UUID.randomUUID();
        UUID firstUserId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();
        UUID activityTypeId = UUID.randomUUID();
        UUID taskStatusId = UUID.randomUUID();
        UUID outcomeId = UUID.randomUUID();

        insertLead(
                secondLeadId,
                leadFixture.branchId(),
                leadFixture.sourceId(),
                leadFixture.statusId());
        insertWorkflowUser(firstUserId, leadFixture.organizationId());
        insertWorkflowUser(secondUserId, leadFixture.organizationId());
        jdbcTemplate.update(
                """
                INSERT INTO lead_activity_types (id, code, name)
                VALUES (?, ?, 'Generic integration activity')
                """,
                activityTypeId,
                "ACTIVITY_" + activityTypeId);
        jdbcTemplate.update(
                """
                INSERT INTO lead_task_statuses (id, code, name)
                VALUES (?, ?, 'Generic integration task status')
                """,
                taskStatusId,
                "TASK_" + taskStatusId);
        jdbcTemplate.update(
                """
                INSERT INTO tour_outcomes (id, organization_id, code, name)
                VALUES (?, ?, ?, 'Generic integration outcome')
                """,
                outcomeId,
                leadFixture.organizationId(),
                "OUTCOME_" + outcomeId);

        return new WorkflowFixture(
                leadFixture,
                secondLeadId,
                firstUserId,
                secondUserId,
                activityTypeId,
                taskStatusId,
                outcomeId);
    }

    private void insertWorkflowUser(UUID userId, UUID organizationId) {
        jdbcTemplate.update(
                """
                INSERT INTO users (
                    id, organization_id, username_normalized, display_name, status_id,
                    created_at, updated_at)
                VALUES (?, ?, ?, 'Generic integration user', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                userId,
                organizationId,
                "workflow_user_" + userId,
                ACTIVE_STATUS_ID);
    }

    private void insertLeadAssignment(UUID leadId, UUID assignedUserId, String endedAtExpression) {
        String validatedEndedAtExpression = switch (endedAtExpression) {
            case null -> "NULL";
            case "CURRENT_TIMESTAMP - INTERVAL '1 day'" -> endedAtExpression;
            case "CURRENT_TIMESTAMP - INTERVAL '2 days'" -> endedAtExpression;
            default -> throw new IllegalArgumentException("Unexpected ended_at expression");
        };
        jdbcTemplate.update(
                """
                INSERT INTO lead_assignments (
                    id, lead_id, assigned_user_id, assigned_at, ended_at, assignment_reason)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP, %s, 'Generic assignment')
                """
                        .formatted(validatedEndedAtExpression),
                UUID.randomUUID(),
                leadId,
                assignedUserId);
    }

    private int countAssignments(UUID leadId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM lead_assignments WHERE lead_id = ?", Integer.class, leadId);
    }

    private int countActiveAssignments(UUID leadId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM lead_assignments WHERE lead_id = ? AND ended_at IS NULL",
                Integer.class,
                leadId);
    }

    private void insertLeadDuplicate(UUID leadId, UUID duplicateOfLeadId) {
        jdbcTemplate.update(
                """
                INSERT INTO lead_duplicates (id, lead_id, duplicate_of_lead_id, detected_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                """,
                UUID.randomUUID(),
                leadId,
                duplicateOfLeadId);
    }

    private UUID insertLeadStatusHistory(
            UUID leadId, UUID fromStatusId, UUID toStatusId, UUID changedBy) {
        UUID historyId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO lead_status_history (
                    id, lead_id, from_status_id, to_status_id, changed_by, changed_at, note)
                VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, 'Generic history note')
                """,
                historyId,
                leadId,
                fromStatusId,
                toStatusId,
                changedBy);
        return historyId;
    }

    private void insertLeadActivity(WorkflowFixture fixture) {
        jdbcTemplate.update(
                """
                INSERT INTO lead_activities (
                    id, lead_id, activity_type_id, performed_by, occurred_at,
                    summary, sanitized_metadata, created_at)
                VALUES (
                    ?, ?, ?, ?, CURRENT_TIMESTAMP,
                    'Generic activity', CAST(? AS JSONB), CURRENT_TIMESTAMP)
                """,
                UUID.randomUUID(),
                fixture.leadId(),
                fixture.activityTypeId(),
                fixture.firstUserId(),
                "{\"kind\":\"generic\"}");
    }

    private UUID insertLeadNote(WorkflowFixture fixture) {
        UUID noteId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO lead_notes (
                    id, lead_id, author_user_id, note_text, created_at, updated_at)
                VALUES (?, ?, ?, 'Generic note', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                noteId,
                fixture.leadId(),
                fixture.firstUserId());
        return noteId;
    }

    private void insertLeadTask(WorkflowFixture fixture) {
        insertLeadTask(fixture, fixture.taskStatusId());
    }

    private void insertLeadTask(WorkflowFixture fixture, UUID taskStatusId) {
        jdbcTemplate.update(
                """
                INSERT INTO lead_tasks (
                    id, lead_id, assigned_to_user_id, status_id, title, due_at,
                    created_by, created_at, updated_at)
                VALUES (
                    ?, ?, ?, ?, 'Generic task', TIMESTAMPTZ '2099-01-01 00:00:00+00',
                    ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                UUID.randomUUID(),
                fixture.leadId(),
                fixture.firstUserId(),
                taskStatusId,
                fixture.firstUserId());
    }

    private void insertTour(
            WorkflowFixture fixture, UUID branchId, UUID salesManagerUserId, UUID outcomeId) {
        jdbcTemplate.update(
                """
                INSERT INTO tours (
                    id, lead_id, branch_id, sales_manager_user_id, scheduled_at,
                    outcome_id, created_at, updated_at)
                VALUES (
                    ?, ?, ?, ?, CURRENT_TIMESTAMP + INTERVAL '1 day',
                    ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                UUID.randomUUID(),
                fixture.leadId(),
                branchId,
                salesManagerUserId,
                outcomeId);
    }

    private int countRowsForLead(String tableName, UUID leadId) {
        String validatedTable = switch (tableName) {
            case "lead_status_history", "lead_activities", "lead_notes", "lead_tasks", "tours" ->
                tableName;
            default -> throw new IllegalArgumentException("Unexpected workflow table: " + tableName);
        };
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM " + validatedTable + " WHERE lead_id = ?",
                Integer.class,
                leadId);
    }

    private void deleteWorkflowFixture(WorkflowFixture fixture) {
        UUID branchId = fixture.branchId();
        jdbcTemplate.update(
                """
                DELETE FROM lead_duplicates
                 WHERE lead_id IN (SELECT id FROM leads WHERE branch_id = ?)
                    OR duplicate_of_lead_id IN (SELECT id FROM leads WHERE branch_id = ?)
                """,
                branchId,
                branchId);
        jdbcTemplate.update(
                "DELETE FROM tours WHERE lead_id IN (SELECT id FROM leads WHERE branch_id = ?)",
                branchId);
        jdbcTemplate.update(
                "DELETE FROM lead_tasks WHERE lead_id IN (SELECT id FROM leads WHERE branch_id = ?)",
                branchId);
        jdbcTemplate.update(
                "DELETE FROM lead_notes WHERE lead_id IN (SELECT id FROM leads WHERE branch_id = ?)",
                branchId);
        jdbcTemplate.update(
                "DELETE FROM lead_activities WHERE lead_id IN (SELECT id FROM leads WHERE branch_id = ?)",
                branchId);
        jdbcTemplate.update(
                """
                DELETE FROM lead_status_history
                 WHERE lead_id IN (SELECT id FROM leads WHERE branch_id = ?)
                """,
                branchId);
        jdbcTemplate.update(
                """
                DELETE FROM lead_assignments
                 WHERE lead_id IN (SELECT id FROM leads WHERE branch_id = ?)
                """,
                branchId);
        jdbcTemplate.update("DELETE FROM tour_outcomes WHERE id = ?", fixture.outcomeId());
        jdbcTemplate.update("DELETE FROM lead_activity_types WHERE id = ?", fixture.activityTypeId());
        jdbcTemplate.update("DELETE FROM lead_task_statuses WHERE id = ?", fixture.taskStatusId());
        jdbcTemplate.update(
                "DELETE FROM users WHERE id IN (?, ?)", fixture.firstUserId(), fixture.secondUserId());
        deleteLeadFixture(fixture.leadFixture());
    }

    private void insertLead(UUID leadId, UUID branchId, UUID sourceId, UUID statusId) {
        jdbcTemplate.update(
                """
                INSERT INTO leads (
                    id, branch_id, source_id, status_id, parent_or_guardian_name,
                    first_contact_due_at, created_at, updated_at)
                VALUES (
                    ?, ?, ?, ?, 'Integration test guardian',
                    CURRENT_TIMESTAMP + INTERVAL '24 hours', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                leadId,
                branchId,
                sourceId,
                statusId);
    }

    private void insertLeadPhone(UUID leadId, String normalizedPhone, boolean primary) {
        jdbcTemplate.update(
                """
                INSERT INTO lead_phones (
                    id, lead_id, normalized_phone, display_phone, is_primary, created_at)
                VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """,
                UUID.randomUUID(),
                leadId,
                normalizedPhone,
                normalizedPhone,
                primary);
    }

    private void insertProspectiveChild(UUID leadId) {
        jdbcTemplate.update(
                """
                INSERT INTO prospective_children (id, lead_id, first_name, created_at, updated_at)
                VALUES (?, ?, 'Integration test child', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                UUID.randomUUID(),
                leadId);
    }

    private int countLeadPhones(UUID leadId, boolean primary) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM lead_phones WHERE lead_id = ? AND is_primary = ?",
                Integer.class,
                leadId,
                primary);
    }

    private int countAllLeadPhones(UUID leadId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM lead_phones WHERE lead_id = ?", Integer.class, leadId);
    }

    private String genericPhone() {
        return "TEST_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }

    private void deleteLeadFixture(LeadFixture fixture) {
        jdbcTemplate.update(
                """
                DELETE FROM prospective_children
                 WHERE lead_id IN (SELECT id FROM leads WHERE branch_id = ?)
                """,
                fixture.branchId());
        jdbcTemplate.update(
                """
                DELETE FROM lead_phones
                 WHERE lead_id IN (SELECT id FROM leads WHERE branch_id = ?)
                """,
                fixture.branchId());
        jdbcTemplate.update("DELETE FROM leads WHERE branch_id = ?", fixture.branchId());
        jdbcTemplate.update("DELETE FROM lead_sources WHERE id = ?", fixture.sourceId());
        jdbcTemplate.update("DELETE FROM lead_statuses WHERE id = ?", fixture.statusId());
        jdbcTemplate.update("DELETE FROM branches WHERE id = ?", fixture.branchId());
        deleteOrganization(fixture.organizationId());
    }

    private void insertDepartment(UUID organizationId, UUID branchId, String code) {
        jdbcTemplate.update(
                """
                INSERT INTO departments (
                    id, organization_id, branch_id, code, name, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'Integration test department', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                UUID.randomUUID(),
                organizationId,
                branchId,
                code);
    }

    private void insertApplicationSetting(UUID organizationId, UUID branchId, String settingKey) {
        jdbcTemplate.update(
                """
                INSERT INTO application_settings (
                    id, organization_id, branch_id, setting_key, value_type, updated_at)
                VALUES (?, ?, ?, ?, 'STRING', CURRENT_TIMESTAMP)
                """,
                UUID.randomUUID(),
                organizationId,
                branchId,
                settingKey);
    }

    private void insertNumberSequence(
            UUID organizationId, UUID branchId, String sequenceCode, Integer year) {
        jdbcTemplate.update(
                """
                INSERT INTO number_sequences (
                    id, organization_id, branch_id, sequence_code, year, next_value, updated_at)
                VALUES (?, ?, ?, ?, ?, 1, CURRENT_TIMESTAMP)
                """,
                UUID.randomUUID(),
                organizationId,
                branchId,
                sequenceCode,
                year);
    }

    private UserRoleFixture createUserRoleFixture(boolean withBranch) {
        UUID organizationId = createOrganization();
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID branchId = withBranch ? createBranch(organizationId) : null;

        jdbcTemplate.update(
                """
                INSERT INTO users (
                    id, organization_id, username_normalized, display_name, status_id,
                    created_at, updated_at)
                VALUES (?, ?, ?, 'Integration test user', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                userId,
                organizationId,
                "user_" + userId,
                ACTIVE_STATUS_ID);
        jdbcTemplate.update(
                """
                INSERT INTO roles (id, organization_id, code, name, created_at, updated_at)
                VALUES (?, ?, ?, 'Integration test role', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                roleId,
                organizationId,
                "ROLE_" + roleId);

        return new UserRoleFixture(userId, roleId, branchId);
    }

    private void insertCurrentUserRole(UUID assignmentId, UserRoleFixture fixture, UUID branchId) {
        jdbcTemplate.update(
                """
                INSERT INTO user_roles (
                    id, user_id, role_id, branch_id, valid_from, valid_until, assigned_by, created_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, NULL, ?, CURRENT_TIMESTAMP)
                """,
                assignmentId,
                fixture.userId(),
                fixture.roleId(),
                branchId,
                fixture.userId());
    }

    private void insertHistoricalUserRole(UUID assignmentId, UserRoleFixture fixture, UUID branchId) {
        jdbcTemplate.update(
                """
                INSERT INTO user_roles (
                    id, user_id, role_id, branch_id, valid_from, valid_until, assigned_by, created_at)
                VALUES (
                    ?, ?, ?, ?, CURRENT_TIMESTAMP - INTERVAL '2 days',
                    CURRENT_TIMESTAMP - INTERVAL '1 day', ?, CURRENT_TIMESTAMP)
                """,
                assignmentId,
                fixture.userId(),
                fixture.roleId(),
                branchId,
                fixture.userId());
    }

    private AuditFixture createAuditFixture() {
        UUID organizationId = UUID.randomUUID();
        UUID auditLogId = UUID.randomUUID();

        jdbcTemplate.update(
                """
                INSERT INTO organizations (id, code, name, created_at, updated_at)
                VALUES (?, ?, 'Integration test organization', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                organizationId,
                "AUDIT_ORG_" + organizationId);
        jdbcTemplate.update(
                """
                INSERT INTO audit_logs (id, organization_id, entity_type, entity_id, action, created_at)
                VALUES (?, ?, 'INTEGRATION_TEST', ?, 'CREATED', CURRENT_TIMESTAMP)
                """,
                auditLogId,
                organizationId,
                organizationId);

        return new AuditFixture(auditLogId);
    }

    private void assertSqlState(String expectedSqlState, ThrowingCallable operation) {
        assertThatThrownBy(operation)
                .isInstanceOfSatisfying(
                        DataAccessException.class,
                        exception -> assertSqlState(expectedSqlState, exception));
    }

    private void assertSqlState(String expectedSqlState, DataAccessException exception) {
        Throwable mostSpecificCause = exception.getMostSpecificCause();
        assertThat(mostSpecificCause).isInstanceOf(SQLException.class);
        assertThat(((SQLException) mostSpecificCause).getSQLState()).isEqualTo(expectedSqlState);
    }

    private boolean causeChainContainsSqlState(Throwable throwable, String expectedSqlState) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<Throwable> pending = new ArrayDeque<>();
        pending.push(throwable);

        while (!pending.isEmpty()) {
            Throwable current = pending.pop();
            if (!visited.add(current)) {
                continue;
            }
            if (current instanceof SQLException sqlException) {
                if (expectedSqlState.equals(sqlException.getSQLState())) {
                    return true;
                }
                if (sqlException.getNextException() != null) {
                    pending.push(sqlException.getNextException());
                }
            }
            if (current.getCause() != null) {
                pending.push(current.getCause());
            }
        }

        return false;
    }

    private List<ColumnMetadata> expectedTelephonyConfigurationColumns() {
        return """
                call_directions|id|uuid||false|
                call_directions|code|character varying|30|false|
                call_directions|name|character varying|80|false|
                call_dispositions|id|uuid||false|
                call_dispositions|code|character varying|50|false|
                call_dispositions|name|character varying|100|false|
                call_dispositions|is_missed|boolean||false|false
                call_event_types|id|uuid||false|
                call_event_types|code|character varying|50|false|
                call_event_types|name|character varying|100|false|
                webhook_statuses|id|uuid||false|
                webhook_statuses|code|character varying|50|false|
                webhook_statuses|name|character varying|100|false|
                webhook_statuses|is_final|boolean||false|false
                pbx_configs|id|uuid||false|
                pbx_configs|organization_id|uuid||false|
                pbx_configs|branch_id|uuid||true|
                pbx_configs|name|character varying|120|false|
                pbx_configs|base_url|character varying|500|false|
                pbx_configs|credential_secret_reference|character varying|255|false|
                pbx_configs|is_active|boolean||false|true
                pbx_configs|created_at|timestamp with time zone||false|
                pbx_configs|updated_at|timestamp with time zone||false|
                extensions|id|uuid||false|
                extensions|pbx_config_id|uuid||false|
                extensions|user_id|uuid||true|
                extensions|extension_number|character varying|30|false|
                extensions|display_name|character varying|120|true|
                extensions|is_active|boolean||false|true
                extensions|created_at|timestamp with time zone||false|
                extensions|updated_at|timestamp with time zone||false|
                sip_accounts|id|uuid||false|
                sip_accounts|pbx_config_id|uuid||false|
                sip_accounts|extension_id|uuid||false|
                sip_accounts|sip_username|character varying|120|false|
                sip_accounts|secret_reference|character varying|255|false|
                sip_accounts|is_active|boolean||false|true
                sip_accounts|created_at|timestamp with time zone||false|
                sip_accounts|updated_at|timestamp with time zone||false|
                phone_numbers|id|uuid||false|
                phone_numbers|pbx_config_id|uuid||false|
                phone_numbers|normalized_number|character varying|32|false|
                phone_numbers|display_number|character varying|50|false|
                phone_numbers|is_primary|boolean||false|false
                phone_numbers|is_active|boolean||false|true
                """
                .lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .map(line -> {
                    String[] fields = line.split("\\|", -1);
                    Integer length = fields[3].isEmpty() ? null : Integer.valueOf(fields[3]);
                    String defaultValue = fields[5].isEmpty() ? null : fields[5];
                    return new ColumnMetadata(
                            fields[0],
                            fields[1],
                            fields[2],
                            length,
                            Boolean.parseBoolean(fields[4]),
                            defaultValue);
                })
                .toList();
    }

    private List<KeyMetadata> expectedTelephonyConfigurationKeys() {
        return List.of(
                key("call_directions", "PRIMARY KEY", "id", false),
                key("call_directions", "UNIQUE", "code", false),
                key("call_dispositions", "PRIMARY KEY", "id", false),
                key("call_dispositions", "UNIQUE", "code", false),
                key("call_event_types", "PRIMARY KEY", "id", false),
                key("call_event_types", "UNIQUE", "code", false),
                key("webhook_statuses", "PRIMARY KEY", "id", false),
                key("webhook_statuses", "UNIQUE", "code", false),
                key("pbx_configs", "PRIMARY KEY", "id", false),
                key("extensions", "PRIMARY KEY", "id", false),
                key("extensions", "UNIQUE", "pbx_config_id,extension_number", false),
                key("sip_accounts", "PRIMARY KEY", "id", false),
                key("sip_accounts", "UNIQUE", "pbx_config_id,sip_username", false),
                key("phone_numbers", "PRIMARY KEY", "id", false),
                key("phone_numbers", "UNIQUE", "pbx_config_id,normalized_number", false));
    }

    private List<IndexMetadata> expectedTelephonyConfigurationIndexes() {
        return List.of(
                index("call_directions", "uk_call_directions_code", true, "code"),
                index("call_dispositions", "uk_call_dispositions_code", true, "code"),
                index("call_event_types", "uk_call_event_types_code", true, "code"),
                index("webhook_statuses", "uk_webhook_statuses_code", true, "code"),
                index(
                        "pbx_configs",
                        "idx_pbx_configs_organization_id",
                        false,
                        "organization_id"),
                index("pbx_configs", "idx_pbx_configs_branch_id", false, "branch_id"),
                index(
                        "extensions",
                        "uk_extensions_pbx_extension_number",
                        true,
                        "pbx_config_id,extension_number"),
                index("extensions", "idx_extensions_user_id", false, "user_id"),
                index(
                        "sip_accounts",
                        "uk_sip_accounts_pbx_username",
                        true,
                        "pbx_config_id,sip_username"),
                index("sip_accounts", "idx_sip_accounts_extension_id", false, "extension_id"),
                index(
                        "phone_numbers",
                        "uk_phone_numbers_pbx_normalized_number",
                        true,
                        "pbx_config_id,normalized_number"));
    }

    private List<ColumnMetadata> expectedTelephonyCallColumns() {
        return """
                call_sessions|id|uuid||false|
                call_sessions|pbx_config_id|uuid||false|
                call_sessions|external_call_id|character varying|150|false|
                call_sessions|direction_id|uuid||false|
                call_sessions|disposition_id|uuid||true|
                call_sessions|from_normalized_number|character varying|32|false|
                call_sessions|to_normalized_number|character varying|32|false|
                call_sessions|started_at|timestamp with time zone||false|
                call_sessions|answered_at|timestamp with time zone||true|
                call_sessions|ended_at|timestamp with time zone||true|
                call_sessions|duration_seconds|integer||false|0
                call_sessions|created_at|timestamp with time zone||false|
                call_participants|id|uuid||false|
                call_participants|call_session_id|uuid||false|
                call_participants|user_id|uuid||true|
                call_participants|extension_id|uuid||true|
                call_participants|normalized_phone|character varying|32|true|
                call_participants|participant_role|character varying|50|false|
                call_participants|joined_at|timestamp with time zone||true|
                call_participants|left_at|timestamp with time zone||true|
                call_events|id|uuid||false|
                call_events|call_session_id|uuid||false|
                call_events|external_event_id|character varying|150|true|
                call_events|event_type_id|uuid||false|
                call_events|occurred_at|timestamp with time zone||false|
                call_events|sanitized_metadata|jsonb||true|
                call_events|created_at|timestamp with time zone||false|
                call_recordings|id|uuid||false|
                call_recordings|call_session_id|uuid||false|
                call_recordings|stored_file_id|uuid||true|
                call_recordings|external_recording_id|character varying|150|true|
                call_recordings|recording_url|character varying|1000|true|
                call_recordings|duration_seconds|integer||true|
                call_recordings|created_at|timestamp with time zone||false|
                lead_calls|lead_id|uuid||false|
                lead_calls|call_session_id|uuid||false|
                lead_calls|linked_by|uuid||true|
                lead_calls|linked_at|timestamp with time zone||false|
                webhook_events|id|uuid||false|
                webhook_events|pbx_config_id|uuid||false|
                webhook_events|external_event_id|character varying|150|false|
                webhook_events|status_id|uuid||false|
                webhook_events|payload_hash|character varying|128|false|
                webhook_events|received_at|timestamp with time zone||false|
                webhook_events|processed_at|timestamp with time zone||true|
                webhook_events|error_message|text||true|
                """
                .lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .map(line -> {
                    String[] fields = line.split("\\|", -1);
                    Integer length = fields[3].isEmpty() ? null : Integer.valueOf(fields[3]);
                    String defaultValue = fields[5].isEmpty() ? null : fields[5];
                    return new ColumnMetadata(
                            fields[0],
                            fields[1],
                            fields[2],
                            length,
                            Boolean.parseBoolean(fields[4]),
                            defaultValue);
                })
                .toList();
    }

    private List<KeyMetadata> expectedTelephonyCallKeys() {
        return List.of(
                key("call_sessions", "PRIMARY KEY", "id", false),
                key(
                        "call_sessions",
                        "UNIQUE",
                        "pbx_config_id,external_call_id",
                        false),
                key("call_participants", "PRIMARY KEY", "id", false),
                key("call_events", "PRIMARY KEY", "id", false),
                key("call_recordings", "PRIMARY KEY", "id", false),
                key("lead_calls", "PRIMARY KEY", "lead_id,call_session_id", false),
                key("webhook_events", "PRIMARY KEY", "id", false),
                key(
                        "webhook_events",
                        "UNIQUE",
                        "pbx_config_id,external_event_id",
                        false));
    }

    private List<IndexMetadata> expectedTelephonyCallIndexes() {
        return List.of(
                index(
                        "call_sessions",
                        "uk_call_sessions_pbx_external_call_id",
                        true,
                        "pbx_config_id,external_call_id"),
                index("call_sessions", "idx_call_sessions_direction_id", false, "direction_id"),
                index(
                        "call_sessions",
                        "idx_call_sessions_disposition_id",
                        false,
                        "disposition_id"),
                index(
                        "call_sessions",
                        "idx_call_sessions_from_normalized_number",
                        false,
                        "from_normalized_number"),
                index(
                        "call_sessions",
                        "idx_call_sessions_to_normalized_number",
                        false,
                        "to_normalized_number"),
                index("call_sessions", "idx_call_sessions_started_at", false, "started_at"),
                index(
                        "call_participants",
                        "idx_call_participants_call_session_id",
                        false,
                        "call_session_id"),
                index("call_participants", "idx_call_participants_user_id", false, "user_id"),
                index(
                        "call_participants",
                        "idx_call_participants_extension_id",
                        false,
                        "extension_id"),
                index(
                        "call_participants",
                        "idx_call_participants_normalized_phone",
                        false,
                        "normalized_phone"),
                index("call_events", "idx_call_events_call_session_id", false, "call_session_id"),
                index("call_events", "idx_call_events_external_event_id", false, "external_event_id"),
                index("call_events", "idx_call_events_event_type_id", false, "event_type_id"),
                index("call_events", "idx_call_events_occurred_at", false, "occurred_at"),
                index(
                        "call_recordings",
                        "idx_call_recordings_call_session_id",
                        false,
                        "call_session_id"),
                index(
                        "call_recordings",
                        "idx_call_recordings_stored_file_id",
                        false,
                        "stored_file_id"),
                index(
                        "call_recordings",
                        "idx_call_recordings_external_recording_id",
                        false,
                        "external_recording_id"),
                index("lead_calls", "idx_lead_calls_call_session_id", false, "call_session_id"),
                index("lead_calls", "idx_lead_calls_linked_by", false, "linked_by"),
                index(
                        "webhook_events",
                        "uk_webhook_events_pbx_external_event_id",
                        true,
                        "pbx_config_id,external_event_id"),
                index("webhook_events", "idx_webhook_events_status_id", false, "status_id"),
                index("webhook_events", "idx_webhook_events_received_at", false, "received_at"),
                index("webhook_events", "idx_webhook_events_payload_hash", false, "payload_hash"));
    }

    private List<ForeignKeyMetadata> expectedTelephonyCallForeignKeys() {
        return List.of(
                restrictedForeignKey("call_sessions", "pbx_config_id", "pbx_configs"),
                restrictedForeignKey("call_sessions", "direction_id", "call_directions"),
                restrictedForeignKey("call_sessions", "disposition_id", "call_dispositions"),
                restrictedForeignKey("call_participants", "call_session_id", "call_sessions"),
                restrictedForeignKey("call_participants", "user_id", "users"),
                restrictedForeignKey("call_participants", "extension_id", "extensions"),
                restrictedForeignKey("call_events", "call_session_id", "call_sessions"),
                restrictedForeignKey("call_events", "event_type_id", "call_event_types"),
                restrictedForeignKey("call_recordings", "call_session_id", "call_sessions"),
                restrictedForeignKey("call_recordings", "stored_file_id", "stored_files"),
                restrictedForeignKey("lead_calls", "lead_id", "leads"),
                restrictedForeignKey("lead_calls", "call_session_id", "call_sessions"),
                restrictedForeignKey("lead_calls", "linked_by", "users"),
                restrictedForeignKey("webhook_events", "pbx_config_id", "pbx_configs"),
                restrictedForeignKey("webhook_events", "status_id", "webhook_statuses"));
    }

    private List<ColumnMetadata> expectedCrmWorkflowColumns() {
        return """
                lead_assignments|id|uuid||false|
                lead_assignments|lead_id|uuid||false|
                lead_assignments|assigned_user_id|uuid||false|
                lead_assignments|assigned_by|uuid||true|
                lead_assignments|assigned_at|timestamp with time zone||false|
                lead_assignments|ended_at|timestamp with time zone||true|
                lead_assignments|assignment_reason|text||true|
                lead_status_history|id|uuid||false|
                lead_status_history|lead_id|uuid||false|
                lead_status_history|from_status_id|uuid||true|
                lead_status_history|to_status_id|uuid||false|
                lead_status_history|changed_by|uuid||false|
                lead_status_history|changed_at|timestamp with time zone||false|
                lead_status_history|note|text||true|
                lead_activities|id|uuid||false|
                lead_activities|lead_id|uuid||false|
                lead_activities|activity_type_id|uuid||false|
                lead_activities|performed_by|uuid||true|
                lead_activities|occurred_at|timestamp with time zone||false|
                lead_activities|summary|text||false|
                lead_activities|sanitized_metadata|jsonb||true|
                lead_activities|created_at|timestamp with time zone||false|
                lead_notes|id|uuid||false|
                lead_notes|lead_id|uuid||false|
                lead_notes|author_user_id|uuid||false|
                lead_notes|note_text|text||false|
                lead_notes|created_at|timestamp with time zone||false|
                lead_notes|updated_at|timestamp with time zone||false|
                lead_tasks|id|uuid||false|
                lead_tasks|lead_id|uuid||false|
                lead_tasks|assigned_to_user_id|uuid||false|
                lead_tasks|status_id|uuid||false|
                lead_tasks|title|character varying|255|false|
                lead_tasks|description|text||true|
                lead_tasks|due_at|timestamp with time zone||false|
                lead_tasks|completed_at|timestamp with time zone||true|
                lead_tasks|created_by|uuid||false|
                lead_tasks|created_at|timestamp with time zone||false|
                lead_tasks|updated_at|timestamp with time zone||false|
                tours|id|uuid||false|
                tours|lead_id|uuid||false|
                tours|branch_id|uuid||false|
                tours|sales_manager_user_id|uuid||false|
                tours|scheduled_at|timestamp with time zone||false|
                tours|attended_at|timestamp with time zone||true|
                tours|outcome_id|uuid||true|
                tours|notes|text||true|
                tours|created_at|timestamp with time zone||false|
                tours|updated_at|timestamp with time zone||false|
                lead_duplicates|id|uuid||false|
                lead_duplicates|lead_id|uuid||false|
                lead_duplicates|duplicate_of_lead_id|uuid||false|
                lead_duplicates|detected_by_user_id|uuid||true|
                lead_duplicates|detected_at|timestamp with time zone||false|
                lead_duplicates|reason|text||true|
                lead_duplicates|resolved_at|timestamp with time zone||true|
                lead_duplicates|resolved_by_user_id|uuid||true|
                """
                .lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .map(line -> {
                    String[] fields = line.split("\\|", -1);
                    Integer length = fields[3].isEmpty() ? null : Integer.valueOf(fields[3]);
                    String defaultValue = fields[5].isEmpty() ? null : fields[5];
                    return new ColumnMetadata(
                            fields[0],
                            fields[1],
                            fields[2],
                            length,
                            Boolean.parseBoolean(fields[4]),
                            defaultValue);
                })
                .toList();
    }

    private List<KeyMetadata> expectedCrmWorkflowKeys() {
        return List.of(
                key("lead_assignments", "PRIMARY KEY", "id", false),
                key("lead_status_history", "PRIMARY KEY", "id", false),
                key("lead_activities", "PRIMARY KEY", "id", false),
                key("lead_notes", "PRIMARY KEY", "id", false),
                key("lead_tasks", "PRIMARY KEY", "id", false),
                key("tours", "PRIMARY KEY", "id", false),
                key("lead_duplicates", "PRIMARY KEY", "id", false),
                key("lead_duplicates", "UNIQUE", "lead_id,duplicate_of_lead_id", false));
    }

    private List<IndexMetadata> expectedCrmWorkflowIndexes() {
        return List.of(
                index("lead_assignments", "idx_lead_assignments_lead_id", false, "lead_id"),
                index(
                        "lead_assignments",
                        "idx_lead_assignments_assigned_user_id",
                        false,
                        "assigned_user_id"),
                index(
                        "lead_assignments", "idx_lead_assignments_assigned_by", false, "assigned_by"),
                index(
                        "lead_assignments",
                        "idx_lead_assignments_lead_ended_at",
                        false,
                        "lead_id,ended_at"),
                partialIndex(
                        "lead_assignments",
                        "ux_lead_assignments_active_lead",
                        true,
                        "lead_id"),
                index(
                        "lead_status_history",
                        "idx_lead_status_history_lead_id",
                        false,
                        "lead_id"),
                index(
                        "lead_status_history",
                        "idx_lead_status_history_from_status_id",
                        false,
                        "from_status_id"),
                index(
                        "lead_status_history",
                        "idx_lead_status_history_to_status_id",
                        false,
                        "to_status_id"),
                index(
                        "lead_status_history",
                        "idx_lead_status_history_changed_by",
                        false,
                        "changed_by"),
                index(
                        "lead_status_history",
                        "idx_lead_status_history_changed_at",
                        false,
                        "changed_at"),
                index("lead_activities", "idx_lead_activities_lead_id", false, "lead_id"),
                index(
                        "lead_activities",
                        "idx_lead_activities_activity_type_id",
                        false,
                        "activity_type_id"),
                index(
                        "lead_activities",
                        "idx_lead_activities_performed_by",
                        false,
                        "performed_by"),
                index(
                        "lead_activities", "idx_lead_activities_occurred_at", false, "occurred_at"),
                index("lead_notes", "idx_lead_notes_lead_id", false, "lead_id"),
                index(
                        "lead_notes", "idx_lead_notes_author_user_id", false, "author_user_id"),
                index("lead_tasks", "idx_lead_tasks_lead_id", false, "lead_id"),
                index(
                        "lead_tasks",
                        "idx_lead_tasks_assigned_to_user_id",
                        false,
                        "assigned_to_user_id"),
                index("lead_tasks", "idx_lead_tasks_status_id", false, "status_id"),
                index("lead_tasks", "idx_lead_tasks_created_by", false, "created_by"),
                index("lead_tasks", "idx_lead_tasks_due_at", false, "due_at"),
                index(
                        "lead_tasks",
                        "idx_lead_tasks_assignee_status_due",
                        false,
                        "assigned_to_user_id,status_id,due_at"),
                index("tours", "idx_tours_lead_id", false, "lead_id"),
                index("tours", "idx_tours_branch_id", false, "branch_id"),
                index(
                        "tours",
                        "idx_tours_sales_manager_user_id",
                        false,
                        "sales_manager_user_id"),
                index("tours", "idx_tours_outcome_id", false, "outcome_id"),
                index("tours", "idx_tours_scheduled_at", false, "scheduled_at"),
                index(
                        "lead_duplicates",
                        "uk_lead_duplicates_directed_pair",
                        true,
                        "lead_id,duplicate_of_lead_id"),
                index(
                        "lead_duplicates",
                        "idx_lead_duplicates_duplicate_of_lead_id",
                        false,
                        "duplicate_of_lead_id"),
                index(
                        "lead_duplicates",
                        "idx_lead_duplicates_detected_by_user_id",
                        false,
                        "detected_by_user_id"),
                index(
                        "lead_duplicates",
                        "idx_lead_duplicates_resolved_by_user_id",
                        false,
                        "resolved_by_user_id"));
    }

    private List<ColumnMetadata> expectedCrmLeadCoreColumns() {
        return """
                leads|id|uuid||false|
                leads|branch_id|uuid||false|
                leads|source_id|uuid||false|
                leads|status_id|uuid||false|
                leads|owner_user_id|uuid||true|
                leads|parent_or_guardian_name|character varying|255|false|
                leads|preferred_language_id|uuid||true|
                leads|intent_summary|text||true|
                leads|first_contact_due_at|timestamp with time zone||false|
                leads|first_contact_at|timestamp with time zone||true|
                leads|lost_reason_id|uuid||true|
                leads|archived_at|timestamp with time zone||true|
                leads|created_at|timestamp with time zone||false|
                leads|created_by|uuid||true|
                leads|updated_at|timestamp with time zone||false|
                leads|updated_by|uuid||true|
                lead_phones|id|uuid||false|
                lead_phones|lead_id|uuid||false|
                lead_phones|normalized_phone|character varying|32|false|
                lead_phones|display_phone|character varying|50|false|
                lead_phones|is_primary|boolean||false|false
                lead_phones|created_at|timestamp with time zone||false|
                prospective_children|id|uuid||false|
                prospective_children|lead_id|uuid||false|
                prospective_children|first_name|character varying|120|true|
                prospective_children|last_name|character varying|120|true|
                prospective_children|patronymic|character varying|120|true|
                prospective_children|date_of_birth|date||true|
                prospective_children|reported_age_months|integer||true|
                prospective_children|preferred_language_id|uuid||true|
                prospective_children|notes|text||true|
                prospective_children|created_at|timestamp with time zone||false|
                prospective_children|updated_at|timestamp with time zone||false|
                """
                .lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .map(line -> {
                    String[] fields = line.split("\\|", -1);
                    Integer length = fields[3].isEmpty() ? null : Integer.valueOf(fields[3]);
                    String defaultValue = fields[5].isEmpty() ? null : fields[5];
                    return new ColumnMetadata(
                            fields[0],
                            fields[1],
                            fields[2],
                            length,
                            Boolean.parseBoolean(fields[4]),
                            defaultValue);
                })
                .toList();
    }

    private List<KeyMetadata> expectedCrmLeadCoreKeys() {
        return List.of(
                key("leads", "PRIMARY KEY", "id", false),
                key("lead_phones", "PRIMARY KEY", "id", false),
                key("prospective_children", "PRIMARY KEY", "id", false));
    }

    private List<IndexMetadata> expectedCrmLeadCoreIndexes() {
        return List.of(
                index("leads", "idx_leads_branch_id", false, "branch_id"),
                index("leads", "idx_leads_source_id", false, "source_id"),
                index("leads", "idx_leads_status_id", false, "status_id"),
                index("leads", "idx_leads_owner_user_id", false, "owner_user_id"),
                index(
                        "leads",
                        "idx_leads_preferred_language_id",
                        false,
                        "preferred_language_id"),
                index("leads", "idx_leads_lost_reason_id", false, "lost_reason_id"),
                index("leads", "idx_leads_created_by", false, "created_by"),
                index("leads", "idx_leads_updated_by", false, "updated_by"),
                index("leads", "idx_leads_branch_status", false, "branch_id,status_id"),
                index(
                        "leads", "idx_leads_branch_created_at", false, "branch_id,created_at"),
                index(
                        "leads", "idx_leads_first_contact_due_at", false, "first_contact_due_at"),
                index("lead_phones", "idx_lead_phones_lead_id", false, "lead_id"),
                index(
                        "lead_phones",
                        "idx_lead_phones_normalized_phone",
                        false,
                        "normalized_phone"),
                partialIndex(
                        "lead_phones", "ux_lead_phones_primary_lead", true, "lead_id"),
                index(
                        "prospective_children",
                        "idx_prospective_children_lead_id",
                        false,
                        "lead_id"),
                index(
                        "prospective_children",
                        "idx_prospective_children_preferred_language_id",
                        false,
                        "preferred_language_id"));
    }

    private List<ColumnMetadata> expectedCrmReferenceColumns() {
        return """
                lead_sources|id|uuid||false|
                lead_sources|organization_id|uuid||false|
                lead_sources|code|character varying|50|false|
                lead_sources|name|character varying|120|false|
                lead_sources|source_type|character varying|50|false|
                lead_sources|is_active|boolean||false|true
                lead_sources|sort_order|integer||false|0
                lead_statuses|id|uuid||false|
                lead_statuses|organization_id|uuid||false|
                lead_statuses|code|character varying|50|false|
                lead_statuses|name|character varying|120|false|
                lead_statuses|pipeline_order|integer||false|
                lead_statuses|is_initial|boolean||false|false
                lead_statuses|is_success|boolean||false|false
                lead_statuses|is_lost|boolean||false|false
                lead_statuses|is_archived|boolean||false|false
                lead_statuses|is_active|boolean||false|true
                lost_reasons|id|uuid||false|
                lost_reasons|organization_id|uuid||false|
                lost_reasons|code|character varying|50|false|
                lost_reasons|name|character varying|120|false|
                lost_reasons|is_active|boolean||false|true
                tour_outcomes|id|uuid||false|
                tour_outcomes|organization_id|uuid||false|
                tour_outcomes|code|character varying|50|false|
                tour_outcomes|name|character varying|120|false|
                tour_outcomes|is_active|boolean||false|true
                lead_task_statuses|id|uuid||false|
                lead_task_statuses|code|character varying|50|false|
                lead_task_statuses|name|character varying|100|false|
                lead_task_statuses|is_closed|boolean||false|false
                lead_task_statuses|is_active|boolean||false|true
                lead_activity_types|id|uuid||false|
                lead_activity_types|code|character varying|50|false|
                lead_activity_types|name|character varying|100|false|
                lead_activity_types|is_active|boolean||false|true
                """
                .lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .map(line -> {
                    String[] fields = line.split("\\|", -1);
                    Integer length = fields[3].isEmpty() ? null : Integer.valueOf(fields[3]);
                    String defaultValue = fields[5].isEmpty() ? null : fields[5];
                    return new ColumnMetadata(
                            fields[0],
                            fields[1],
                            fields[2],
                            length,
                            Boolean.parseBoolean(fields[4]),
                            defaultValue);
                })
                .toList();
    }

    private List<KeyMetadata> expectedCrmReferenceKeys() {
        return List.of(
                key("lead_sources", "PRIMARY KEY", "id", false),
                key("lead_sources", "UNIQUE", "organization_id,code", false),
                key("lead_statuses", "PRIMARY KEY", "id", false),
                key("lead_statuses", "UNIQUE", "organization_id,code", false),
                key("lost_reasons", "PRIMARY KEY", "id", false),
                key("lost_reasons", "UNIQUE", "organization_id,code", false),
                key("tour_outcomes", "PRIMARY KEY", "id", false),
                key("tour_outcomes", "UNIQUE", "organization_id,code", false),
                key("lead_task_statuses", "PRIMARY KEY", "id", false),
                key("lead_task_statuses", "UNIQUE", "code", false),
                key("lead_activity_types", "PRIMARY KEY", "id", false),
                key("lead_activity_types", "UNIQUE", "code", false));
    }

    private ForeignKeyIndexMetadata foreignKeyIndex(String tableName, String indexName) {
        return new ForeignKeyIndexMetadata(
                tableName, indexName, "organization_id", "btree", true, true, false);
    }

    private List<IndexMetadata> expectedCrmReferenceIndexes() {
        return List.of(
                index(
                        "lead_sources",
                        "uk_lead_sources_organization_code",
                        true,
                        "organization_id,code"),
                index(
                        "lead_sources",
                        "idx_lead_sources_organization_id",
                        false,
                        "organization_id"),
                index(
                        "lead_statuses",
                        "uk_lead_statuses_organization_code",
                        true,
                        "organization_id,code"),
                index(
                        "lead_statuses",
                        "idx_lead_statuses_organization_id",
                        false,
                        "organization_id"),
                index(
                        "lead_statuses",
                        "idx_lead_statuses_pipeline_order",
                        false,
                        "pipeline_order"),
                index(
                        "lost_reasons",
                        "uk_lost_reasons_organization_code",
                        true,
                        "organization_id,code"),
                index(
                        "lost_reasons",
                        "idx_lost_reasons_organization_id",
                        false,
                        "organization_id"),
                index(
                        "tour_outcomes",
                        "uk_tour_outcomes_organization_code",
                        true,
                        "organization_id,code"),
                index(
                        "tour_outcomes",
                        "idx_tour_outcomes_organization_id",
                        false,
                        "organization_id"),
                index("lead_task_statuses", "uk_lead_task_statuses_code", true, "code"),
                index("lead_activity_types", "uk_lead_activity_types_code", true, "code"));
    }

    private IndexMetadata index(
            String tableName, String indexName, boolean unique, String keyColumns) {
        return new IndexMetadata(
                tableName,
                indexName,
                unique,
                false,
                "btree",
                true,
                true,
                false,
                keyColumns);
    }

    private IndexMetadata partialIndex(
            String tableName, String indexName, boolean unique, String keyColumns) {
        return new IndexMetadata(
                tableName,
                indexName,
                unique,
                false,
                "btree",
                true,
                true,
                true,
                keyColumns);
    }

    private List<ColumnMetadata> expectedRemainingColumns() {
        return """
                departments|id|uuid||false|
                departments|organization_id|uuid||false|
                departments|branch_id|uuid||true|
                departments|parent_department_id|uuid||true|
                departments|code|character varying|50|false|
                departments|name|character varying|255|false|
                departments|is_active|boolean||false|true
                departments|created_at|timestamp with time zone||false|
                departments|updated_at|timestamp with time zone||false|
                positions|id|uuid||false|
                positions|organization_id|uuid||false|
                positions|code|character varying|50|false|
                positions|name|character varying|255|false|
                positions|is_active|boolean||false|true
                positions|created_at|timestamp with time zone||false|
                positions|updated_at|timestamp with time zone||false|
                user_credentials|user_id|uuid||false|
                user_credentials|password_hash|character varying|255|false|
                user_credentials|password_changed_at|timestamp with time zone||false|
                user_credentials|failed_login_attempts|integer||false|0
                user_credentials|locked_until|timestamp with time zone||true|
                user_credentials|created_at|timestamp with time zone||false|
                user_credentials|updated_at|timestamp with time zone||false|
                employees|id|uuid||false|
                employees|user_id|uuid||true|
                employees|branch_id|uuid||false|
                employees|department_id|uuid||true|
                employees|position_id|uuid||true|
                employees|employee_number|character varying|50|false|
                employees|employment_start_date|date||false|
                employees|employment_end_date|date||true|
                employees|is_active|boolean||false|true
                employees|created_at|timestamp with time zone||false|
                employees|updated_at|timestamp with time zone||false|
                user_branch_access|user_id|uuid||false|
                user_branch_access|branch_id|uuid||false|
                user_branch_access|granted_by|uuid||false|
                user_branch_access|granted_at|timestamp with time zone||false|
                refresh_tokens|id|uuid||false|
                refresh_tokens|user_id|uuid||false|
                refresh_tokens|token_hash|character varying|255|false|
                refresh_tokens|expires_at|timestamp with time zone||false|
                refresh_tokens|revoked_at|timestamp with time zone||true|
                refresh_tokens|created_at|timestamp with time zone||false|
                refresh_tokens|created_ip|character varying|64|true|
                refresh_tokens|user_agent|text||true|
                languages|id|uuid||false|
                languages|code|character varying|20|false|
                languages|name|character varying|100|false|
                languages|is_active|boolean||false|true
                languages|sort_order|integer||false|0
                nationalities|id|uuid||false|
                nationalities|code|character varying|30|false|
                nationalities|name|character varying|100|false|
                nationalities|is_active|boolean||false|true
                gender_types|id|uuid||false|
                gender_types|code|character varying|30|false|
                gender_types|name|character varying|100|false|
                gender_types|is_active|boolean||false|true
                relationship_types|id|uuid||false|
                relationship_types|code|character varying|50|false|
                relationship_types|name|character varying|100|false|
                relationship_types|is_active|boolean||false|true
                document_types|id|uuid||false|
                document_types|code|character varying|50|false|
                document_types|name|character varying|150|false|
                document_types|applies_to|character varying|50|false|
                document_types|is_active|boolean||false|true
                document_verification_statuses|id|uuid||false|
                document_verification_statuses|code|character varying|50|false|
                document_verification_statuses|name|character varying|100|false|
                document_verification_statuses|is_final|boolean||false|false
                document_verification_statuses|is_active|boolean||false|true
                stored_files|id|uuid||false|
                stored_files|organization_id|uuid||false|
                stored_files|branch_id|uuid||true|
                stored_files|storage_provider|character varying|40|false|
                stored_files|bucket_name|character varying|120|false|
                stored_files|object_key|character varying|500|false|
                stored_files|original_file_name|character varying|255|false|
                stored_files|content_type|character varying|150|false|
                stored_files|file_size|bigint||false|
                stored_files|checksum|character varying|128|true|
                stored_files|uploaded_by|uuid||true|
                stored_files|uploaded_at|timestamp with time zone||false|
                stored_files|deleted_at|timestamp with time zone||true|
                application_settings|id|uuid||false|
                application_settings|organization_id|uuid||false|
                application_settings|branch_id|uuid||true|
                application_settings|setting_key|character varying|150|false|
                application_settings|setting_value|text||true|
                application_settings|value_type|character varying|40|false|
                application_settings|is_sensitive|boolean||false|false
                application_settings|updated_by|uuid||true|
                application_settings|updated_at|timestamp with time zone||false|
                number_sequences|id|uuid||false|
                number_sequences|organization_id|uuid||false|
                number_sequences|branch_id|uuid||true|
                number_sequences|sequence_code|character varying|80|false|
                number_sequences|year|integer||true|
                number_sequences|prefix|character varying|30|true|
                number_sequences|next_value|bigint||false|
                number_sequences|padding_length|integer||false|6
                number_sequences|updated_at|timestamp with time zone||false|
                """
                .lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .map(line -> {
                    String[] fields = line.split("\\|", -1);
                    Integer length = fields[3].isEmpty() ? null : Integer.valueOf(fields[3]);
                    String defaultValue = fields[5].isEmpty() ? null : fields[5];
                    return new ColumnMetadata(
                            fields[0],
                            fields[1],
                            fields[2],
                            length,
                            Boolean.parseBoolean(fields[4]),
                            defaultValue);
                })
                .toList();
    }

    private List<KeyMetadata> expectedRemainingKeys() {
        return List.of(
                key("departments", "PRIMARY KEY", "id", false),
                key("departments", "UNIQUE", "organization_id,branch_id,code", true),
                key("positions", "PRIMARY KEY", "id", false),
                key("positions", "UNIQUE", "organization_id,code", false),
                key("user_credentials", "PRIMARY KEY", "user_id", false),
                key("employees", "PRIMARY KEY", "id", false),
                key("employees", "UNIQUE", "user_id", false),
                key("employees", "UNIQUE", "employee_number", false),
                key("user_branch_access", "PRIMARY KEY", "user_id,branch_id", false),
                key("refresh_tokens", "PRIMARY KEY", "id", false),
                key("refresh_tokens", "UNIQUE", "token_hash", false),
                key("languages", "PRIMARY KEY", "id", false),
                key("languages", "UNIQUE", "code", false),
                key("nationalities", "PRIMARY KEY", "id", false),
                key("nationalities", "UNIQUE", "code", false),
                key("gender_types", "PRIMARY KEY", "id", false),
                key("gender_types", "UNIQUE", "code", false),
                key("relationship_types", "PRIMARY KEY", "id", false),
                key("relationship_types", "UNIQUE", "code", false),
                key("document_types", "PRIMARY KEY", "id", false),
                key("document_types", "UNIQUE", "code", false),
                key("document_verification_statuses", "PRIMARY KEY", "id", false),
                key("document_verification_statuses", "UNIQUE", "code", false),
                key("stored_files", "PRIMARY KEY", "id", false),
                key("stored_files", "UNIQUE", "storage_provider,bucket_name,object_key", false),
                key("application_settings", "PRIMARY KEY", "id", false),
                key("application_settings", "UNIQUE", "organization_id,branch_id,setting_key", true),
                key("number_sequences", "PRIMARY KEY", "id", false),
                key("number_sequences", "UNIQUE", "organization_id,branch_id,sequence_code,year", true));
    }

    private KeyMetadata key(
            String tableName, String keyType, String keyColumns, boolean nullsNotDistinct) {
        return new KeyMetadata(tableName, keyType, keyColumns, nullsNotDistinct, "btree");
    }

    private List<ForeignKeyMetadata> expectedForeignKeys() {
        return List.of(
                restrictedForeignKey("branches", "organization_id", "organizations"),
                restrictedForeignKey("users", "organization_id", "organizations"),
                restrictedForeignKey("users", "status_id", "user_statuses"),
                restrictedForeignKey("users", "created_by", "users"),
                restrictedForeignKey("users", "updated_by", "users"),
                restrictedForeignKey("roles", "organization_id", "organizations"),
                restrictedForeignKey("role_permissions", "role_id", "roles"),
                restrictedForeignKey("role_permissions", "permission_id", "permissions"),
                restrictedForeignKey("role_permissions", "granted_by", "users"),
                restrictedForeignKey("user_roles", "user_id", "users"),
                restrictedForeignKey("user_roles", "role_id", "roles"),
                restrictedForeignKey("user_roles", "branch_id", "branches"),
                restrictedForeignKey("user_roles", "assigned_by", "users"),
                restrictedForeignKey("audit_logs", "organization_id", "organizations"),
                restrictedForeignKey("audit_logs", "branch_id", "branches"),
                restrictedForeignKey("audit_logs", "actor_user_id", "users"),
                restrictedForeignKey("departments", "organization_id", "organizations"),
                restrictedForeignKey("departments", "branch_id", "branches"),
                restrictedForeignKey("departments", "parent_department_id", "departments"),
                restrictedForeignKey("positions", "organization_id", "organizations"),
                restrictedForeignKey("user_credentials", "user_id", "users"),
                restrictedForeignKey("employees", "user_id", "users"),
                restrictedForeignKey("employees", "branch_id", "branches"),
                restrictedForeignKey("employees", "department_id", "departments"),
                restrictedForeignKey("employees", "position_id", "positions"),
                restrictedForeignKey("user_branch_access", "user_id", "users"),
                restrictedForeignKey("user_branch_access", "branch_id", "branches"),
                restrictedForeignKey("user_branch_access", "granted_by", "users"),
                restrictedForeignKey("refresh_tokens", "user_id", "users"),
                restrictedForeignKey("stored_files", "organization_id", "organizations"),
                restrictedForeignKey("stored_files", "branch_id", "branches"),
                restrictedForeignKey("stored_files", "uploaded_by", "users"),
                restrictedForeignKey("application_settings", "organization_id", "organizations"),
                restrictedForeignKey("application_settings", "branch_id", "branches"),
                restrictedForeignKey("application_settings", "updated_by", "users"),
                restrictedForeignKey("number_sequences", "organization_id", "organizations"),
                restrictedForeignKey("number_sequences", "branch_id", "branches"),
                restrictedForeignKey("lead_sources", "organization_id", "organizations"),
                restrictedForeignKey("lead_statuses", "organization_id", "organizations"),
                restrictedForeignKey("lost_reasons", "organization_id", "organizations"),
                restrictedForeignKey("tour_outcomes", "organization_id", "organizations"),
                restrictedForeignKey("leads", "branch_id", "branches"),
                restrictedForeignKey("leads", "source_id", "lead_sources"),
                restrictedForeignKey("leads", "status_id", "lead_statuses"),
                restrictedForeignKey("leads", "owner_user_id", "users"),
                restrictedForeignKey("leads", "preferred_language_id", "languages"),
                restrictedForeignKey("leads", "lost_reason_id", "lost_reasons"),
                restrictedForeignKey("leads", "created_by", "users"),
                restrictedForeignKey("leads", "updated_by", "users"),
                restrictedForeignKey("lead_phones", "lead_id", "leads"),
                restrictedForeignKey("prospective_children", "lead_id", "leads"),
                restrictedForeignKey(
                        "prospective_children", "preferred_language_id", "languages"),
                restrictedForeignKey("lead_assignments", "lead_id", "leads"),
                restrictedForeignKey("lead_assignments", "assigned_user_id", "users"),
                restrictedForeignKey("lead_assignments", "assigned_by", "users"),
                restrictedForeignKey("lead_status_history", "lead_id", "leads"),
                restrictedForeignKey("lead_status_history", "from_status_id", "lead_statuses"),
                restrictedForeignKey("lead_status_history", "to_status_id", "lead_statuses"),
                restrictedForeignKey("lead_status_history", "changed_by", "users"),
                restrictedForeignKey("lead_activities", "lead_id", "leads"),
                restrictedForeignKey(
                        "lead_activities", "activity_type_id", "lead_activity_types"),
                restrictedForeignKey("lead_activities", "performed_by", "users"),
                restrictedForeignKey("lead_notes", "lead_id", "leads"),
                restrictedForeignKey("lead_notes", "author_user_id", "users"),
                restrictedForeignKey("lead_tasks", "lead_id", "leads"),
                restrictedForeignKey("lead_tasks", "assigned_to_user_id", "users"),
                restrictedForeignKey("lead_tasks", "status_id", "lead_task_statuses"),
                restrictedForeignKey("lead_tasks", "created_by", "users"),
                restrictedForeignKey("tours", "lead_id", "leads"),
                restrictedForeignKey("tours", "branch_id", "branches"),
                restrictedForeignKey("tours", "sales_manager_user_id", "users"),
                restrictedForeignKey("tours", "outcome_id", "tour_outcomes"),
                restrictedForeignKey("lead_duplicates", "lead_id", "leads"),
                restrictedForeignKey("lead_duplicates", "duplicate_of_lead_id", "leads"),
                restrictedForeignKey("lead_duplicates", "detected_by_user_id", "users"),
                restrictedForeignKey("lead_duplicates", "resolved_by_user_id", "users"),
                restrictedForeignKey("pbx_configs", "organization_id", "organizations"),
                restrictedForeignKey("pbx_configs", "branch_id", "branches"),
                restrictedForeignKey("extensions", "pbx_config_id", "pbx_configs"),
                restrictedForeignKey("extensions", "user_id", "users"),
                restrictedForeignKey("sip_accounts", "pbx_config_id", "pbx_configs"),
                restrictedForeignKey("sip_accounts", "extension_id", "extensions"),
                restrictedForeignKey("phone_numbers", "pbx_config_id", "pbx_configs"),
                restrictedForeignKey("call_sessions", "pbx_config_id", "pbx_configs"),
                restrictedForeignKey("call_sessions", "direction_id", "call_directions"),
                restrictedForeignKey("call_sessions", "disposition_id", "call_dispositions"),
                restrictedForeignKey("call_participants", "call_session_id", "call_sessions"),
                restrictedForeignKey("call_participants", "user_id", "users"),
                restrictedForeignKey("call_participants", "extension_id", "extensions"),
                restrictedForeignKey("call_events", "call_session_id", "call_sessions"),
                restrictedForeignKey("call_events", "event_type_id", "call_event_types"),
                restrictedForeignKey("call_recordings", "call_session_id", "call_sessions"),
                restrictedForeignKey("call_recordings", "stored_file_id", "stored_files"),
                restrictedForeignKey("lead_calls", "lead_id", "leads"),
                restrictedForeignKey("lead_calls", "call_session_id", "call_sessions"),
                restrictedForeignKey("lead_calls", "linked_by", "users"),
                restrictedForeignKey("webhook_events", "pbx_config_id", "pbx_configs"),
                restrictedForeignKey("webhook_events", "status_id", "webhook_statuses"));
    }

    private ForeignKeyMetadata restrictedForeignKey(
            String sourceTable, String sourceColumn, String targetTable) {
        return new ForeignKeyMetadata(
                sourceTable, sourceColumn, targetTable, "id", "RESTRICT", "RESTRICT");
    }

    private record UserStatusSeed(UUID id, String code) {}

    private record MigrationHistory(
            String version, String description, String script, boolean success) {}

    private record TaskStatusSeed(
            UUID id, String code, String name, boolean closed, boolean active) {}

    private record ActiveReferenceSeed(UUID id, String code, String name, boolean active) {}

    private record CoreReferenceSeed(
            String tableName,
            UUID id,
            String code,
            String name,
            boolean active,
            Integer sortOrder,
            String appliesTo,
            Boolean finalStatus) {}

    private record ReferenceSeed(UUID id, String code, String name) {}

    private record FlaggedReferenceSeed(UUID id, String code, String name, boolean flag) {}

    private record ApprovedReference(String tableName, String code) {}

    private record ColumnMetadata(
            String tableName,
            String columnName,
            String dataType,
            Integer maximumLength,
            boolean nullable,
            String defaultValue) {}

    private record KeyMetadata(
            String tableName,
            String keyType,
            String keyColumns,
            boolean nullsNotDistinct,
            String accessMethod) {}

    private record ConstraintCounts(
            int primaryKeys,
            int uniqueConstraints,
            int foreignKeys,
            int checkConstraints) {}

    private record ForeignKeyMetadata(
            String sourceTable,
            String sourceColumn,
            String targetTable,
            String targetColumn,
            String deleteAction,
            String updateAction) {}

    private record ForeignKeyIndexCoverage(String sourceTable, String sourceColumn, boolean covered) {}

    private record ForeignKeyIndexMetadata(
            String tableName,
            String indexName,
            String firstKey,
            String accessMethod,
            boolean valid,
            boolean ready,
            boolean partial) {}

    private record IndexMetadata(
            String tableName,
            String indexName,
            boolean unique,
            boolean nullsNotDistinct,
            String accessMethod,
            boolean valid,
            boolean ready,
            boolean partial,
            String keyColumns) {}

    private record ColumnDefaultMetadata(String tableName, String defaultValue) {}

    private record PartialUniqueIndexMetadata(
            boolean unique,
            String accessMethod,
            boolean nullsNotDistinct,
            int keyCount,
            String firstKey,
            String secondKey,
            String thirdKey,
            String predicate) {}

    private record UserRoleFixture(UUID userId, UUID roleId, UUID branchId) {}

    private record AuditFixture(UUID auditLogId) {}

    private record LeadFixture(
            UUID organizationId, UUID branchId, UUID sourceId, UUID statusId, UUID leadId) {}

    private record TelephonyFixture(
            UUID organizationId, UUID branchId, UUID firstPbxId, UUID secondPbxId) {}

    private record TelephonyCallFixture(
            TelephonyFixture telephonyFixture,
            UUID directionId,
            UUID eventTypeId,
            UUID webhookStatusId) {}

    private record WorkflowFixture(
            LeadFixture leadFixture,
            UUID secondLeadId,
            UUID firstUserId,
            UUID secondUserId,
            UUID activityTypeId,
            UUID taskStatusId,
            UUID outcomeId) {

        private UUID branchId() {
            return leadFixture.branchId();
        }

        private UUID leadId() {
            return leadFixture.leadId();
        }

        private UUID statusId() {
            return leadFixture.statusId();
        }
    }
}
