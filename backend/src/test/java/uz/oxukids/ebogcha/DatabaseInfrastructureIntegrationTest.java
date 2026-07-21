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
    void appliesOnlyTheApprovedFoundationSchema() {
        Integer connectivityCheck = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        assertThat(connectivityCheck).isEqualTo(1);

        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.info().applied())
                .extracting(migration -> migration.getVersion().getVersion())
                .containsExactly("1", "2", "3");

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
                "audit_logs",
                "branches",
                "organizations",
                "permissions",
                "role_permissions",
                "roles",
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
                       'permissions', 'role_permissions', 'user_roles', 'audit_logs')
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
                       'permissions', 'role_permissions', 'user_roles', 'audit_logs')
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

    private UserRoleFixture createUserRoleFixture(boolean withBranch) {
        UUID organizationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID branchId = withBranch ? UUID.randomUUID() : null;

        jdbcTemplate.update(
                """
                INSERT INTO organizations (id, code, name, created_at, updated_at)
                VALUES (?, ?, 'Integration test organization', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                organizationId,
                "ORG_" + organizationId);

        if (branchId != null) {
            jdbcTemplate.update(
                    """
                    INSERT INTO branches (id, organization_id, code, name, created_at, updated_at)
                    VALUES (?, ?, ?, 'Integration test branch', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """,
                    branchId,
                    organizationId,
                    "BRANCH_" + branchId);
        }

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
                restrictedForeignKey("audit_logs", "actor_user_id", "users"));
    }

    private ForeignKeyMetadata restrictedForeignKey(
            String sourceTable, String sourceColumn, String targetTable) {
        return new ForeignKeyMetadata(
                sourceTable, sourceColumn, targetTable, "id", "RESTRICT", "RESTRICT");
    }

    private record UserStatusSeed(UUID id, String code) {}

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
