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
    void appliesOnlyTheApprovedSchemasThroughV9() {
        Integer connectivityCheck = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        assertThat(connectivityCheck).isEqualTo(1);

        Integer postgresMajorVersion = jdbcTemplate.queryForObject(
                "SELECT current_setting('server_version_num')::INTEGER / 10000", Integer.class);
        assertThat(postgresMajorVersion).isEqualTo(17);

        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.info().applied())
                .extracting(migration -> migration.getVersion().getVersion())
                .containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9");

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
                "lead_activities",
                "lead_activity_types",
                "lead_assignments",
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
                "permissions",
                "positions",
                "prospective_children",
                "refresh_tokens",
                "relationship_types",
                "role_permissions",
                "roles",
                "stored_files",
                "tour_outcomes",
                "tours",
                "user_branch_access",
                "user_credentials",
                "user_roles",
                "user_statuses",
                "users");

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

        Integer prohibitedTableCount = jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                  FROM information_schema.tables
                 WHERE table_schema = 'public'
                   AND table_name IN (
                       'pbx_configs', 'extensions', 'sip_accounts', 'phone_numbers',
                       'call_sessions', 'call_participants', 'call_events',
                       'call_recordings', 'lead_calls', 'webhook_events',
                       'lead_conversions', 'children', 'admission_applications')
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
                SELECT (SELECT count(*) FROM languages)
                     + (SELECT count(*) FROM nationalities)
                     + (SELECT count(*) FROM gender_types)
                     + (SELECT count(*) FROM relationship_types)
                     + (SELECT count(*) FROM document_types)
                     + (SELECT count(*) FROM document_verification_statuses)
                     + (SELECT count(*) FROM lead_sources)
                     + (SELECT count(*) FROM lead_statuses)
                     + (SELECT count(*) FROM lost_reasons)
                     + (SELECT count(*) FROM tour_outcomes)
                     + (SELECT count(*) FROM lead_task_statuses)
                     + (SELECT count(*) FROM lead_activity_types)
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
                       'application_settings', 'number_sequences', 'lead_sources',
                       'lead_statuses', 'lost_reasons', 'tour_outcomes',
                       'lead_task_statuses', 'lead_activity_types', 'leads',
                       'lead_phones', 'prospective_children', 'lead_assignments',
                       'lead_status_history', 'lead_activities', 'lead_notes',
                       'lead_tasks', 'tours', 'lead_duplicates')
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
                       'lead_tasks', 'tours', 'lead_duplicates')
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
                       'lead_tasks', 'tours', 'lead_duplicates')
                """,
                (resultSet, rowNumber) -> new ConstraintCounts(
                        resultSet.getInt("primary_keys"),
                        resultSet.getInt("unique_constraints"),
                        resultSet.getInt("foreign_keys"),
                        resultSet.getInt("check_constraints")));

        assertThat(constraintCounts).isEqualTo(new ConstraintCounts(40, 28, 76, 1));
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
                       'lead_notes', 'lead_tasks', 'tours', 'lead_duplicates')
                 ORDER BY table_name
                """,
                (resultSet, rowNumber) -> new ColumnDefaultMetadata(
                        resultSet.getString("table_name"),
                        resultSet.getString("column_default")));
        assertThat(idColumns).containsExactly(
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
                new ColumnDefaultMetadata("prospective_children", null),
                new ColumnDefaultMetadata("tour_outcomes", null),
                new ColumnDefaultMetadata("tours", null));

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
                restrictedForeignKey("lead_duplicates", "resolved_by_user_id", "users"));
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
