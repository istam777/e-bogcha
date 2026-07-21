package uz.oxukids.ebogcha;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
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
    void appliesOnlyTheApprovedCoreAndFoundationSchemas() {
        Integer connectivityCheck = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        assertThat(connectivityCheck).isEqualTo(1);

        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.info().applied())
                .extracting(migration -> migration.getVersion().getVersion())
                .containsExactly("1", "2", "3", "4", "5", "6");

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
                "departments",
                "document_types",
                "document_verification_statuses",
                "employees",
                "gender_types",
                "languages",
                "nationalities",
                "number_sequences",
                "organizations",
                "permissions",
                "positions",
                "refresh_tokens",
                "relationship_types",
                "role_permissions",
                "roles",
                "stored_files",
                "user_branch_access",
                "user_credentials",
                "user_roles",
                "user_statuses",
                "users");

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
                SELECT (SELECT count(*) FROM languages)
                     + (SELECT count(*) FROM nationalities)
                     + (SELECT count(*) FROM gender_types)
                     + (SELECT count(*) FROM relationship_types)
                     + (SELECT count(*) FROM document_types)
                     + (SELECT count(*) FROM document_verification_statuses)
                """,
                Integer.class);
        assertThat(unseededReferenceRowCount).isZero();

        assertThat(applicationContext.containsBean("entityManagerFactory")).isFalse();
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
                       'application_settings', 'number_sequences')
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
                       'application_settings', 'number_sequences')
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
                       'application_settings', 'number_sequences')
                """,
                (resultSet, rowNumber) -> new ConstraintCounts(
                        resultSet.getInt("primary_keys"),
                        resultSet.getInt("unique_constraints"),
                        resultSet.getInt("foreign_keys"),
                        resultSet.getInt("check_constraints")));

        assertThat(constraintCounts).isEqualTo(new ConstraintCounts(24, 21, 37, 0));
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
                restrictedForeignKey("number_sequences", "branch_id", "branches"));
    }

    private ForeignKeyMetadata restrictedForeignKey(
            String sourceTable, String sourceColumn, String targetTable) {
        return new ForeignKeyMetadata(
                sourceTable, sourceColumn, targetTable, "id", "RESTRICT", "RESTRICT");
    }

    private record UserStatusSeed(UUID id, String code) {}

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
}
