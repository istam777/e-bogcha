package uz.oxukids.ebogcha.crm.application.service;

import org.junit.jupiter.api.Test;
import uz.oxukids.ebogcha.crm.application.exception.InvalidLeadSearchQueryException;
import uz.oxukids.ebogcha.crm.application.exception.LeadSearchBranchAccessDeniedException;
import uz.oxukids.ebogcha.crm.application.port.in.OwnerState;
import uz.oxukids.ebogcha.crm.application.port.in.SearchLeadsQuery;
import uz.oxukids.ebogcha.crm.application.port.out.LeadQueryPort;
import uz.oxukids.ebogcha.crm.application.port.out.LeadQueryResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LeadSearchServiceTest {

    private static final UUID ACTOR_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID BRANCH_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final Instant NOW = Instant.parse("2026-07-23T08:00:00Z");

    @Test
    void queryTrimsTextAndAppliesDefaultOwnerState() {
        SearchLeadsQuery query = query("  Fictional  ", null, null, null, 0, 20);

        assertThat(query.queryText()).isEqualTo("Fictional");
        assertThat(query.ownerState()).isEqualTo(OwnerState.ALL);
    }

    @Test
    void rejectsInvalidTextPaginationAndDateRange() {
        assertThatThrownBy(() -> query("x", null, OwnerState.ALL, null, 0, 20))
                .isInstanceOf(InvalidLeadSearchQueryException.class);
        assertThatThrownBy(() -> query("valid", null, OwnerState.ALL, null, -1, 20))
                .isInstanceOf(InvalidLeadSearchQueryException.class);
        assertThatThrownBy(() -> query("valid", null, OwnerState.ALL, null, 0, 0))
                .isInstanceOf(InvalidLeadSearchQueryException.class);
        assertThatThrownBy(() -> query("valid", null, OwnerState.ALL, null, 0, 101))
                .isInstanceOf(InvalidLeadSearchQueryException.class);
        assertThatThrownBy(() -> query(
                null,
                null,
                OwnerState.ALL,
                Instant.parse("2026-07-24T00:00:00Z"),
                Instant.parse("2026-07-23T00:00:00Z"),
                0,
                20
        )).isInstanceOf(InvalidLeadSearchQueryException.class);
    }

    @Test
    void rejectsOwnerFilterCombinedWithUnassigned() {
        assertThatThrownBy(() -> query(
                null, UUID.randomUUID(), OwnerState.UNASSIGNED, null, 0, 20
        )).isInstanceOf(InvalidLeadSearchQueryException.class);
    }

    @Test
    void explicitInaccessibleBranchIsRejectedBeforeSearch() {
        RecordingPort port = new RecordingPort(false, new LeadQueryResult(List.of(), 0));
        LeadSearchService service = new LeadSearchService(port, () -> NOW);

        assertThatThrownBy(() -> service.searchLeads(query(
                null, null, OwnerState.ALL, null, null, BRANCH_ID, 0, 20
        ))).isInstanceOf(LeadSearchBranchAccessDeniedException.class);
        assertThat(port.searchCalls).isZero();
    }

    @Test
    void usesOneClockInstantAndCalculatesPaginationSafely() {
        RecordingPort port = new RecordingPort(true, new LeadQueryResult(List.of(), 41));
        LeadSearchService service = new LeadSearchService(port, () -> NOW);

        var result = service.searchLeads(query(
                null, null, OwnerState.ALL, null, null, null, 1, 20
        ));

        assertThat(port.asOf).isEqualTo(NOW);
        assertThat(result.totalElements()).isEqualTo(41);
        assertThat(result.totalPages()).isEqualTo(3);
        assertThat(result.hasPrevious()).isTrue();
        assertThat(result.hasNext()).isTrue();
    }

    @Test
    void pageBeyondResultsHasNoNextPage() {
        RecordingPort port = new RecordingPort(true, new LeadQueryResult(List.of(), 3));
        LeadSearchService service = new LeadSearchService(port, () -> NOW);

        var result = service.searchLeads(query(
                null, null, OwnerState.ALL, null, null, null, 4, 20
        ));

        assertThat(result.items()).isEmpty();
        assertThat(result.totalPages()).isEqualTo(1);
        assertThat(result.hasPrevious()).isTrue();
        assertThat(result.hasNext()).isFalse();
    }

    private static SearchLeadsQuery query(
            String text,
            UUID ownerOperatorId,
            OwnerState ownerState,
            Instant createdFrom,
            int page,
            int size
    ) {
        return query(
                text, ownerOperatorId, ownerState, createdFrom, null, null, page, size
        );
    }

    private static SearchLeadsQuery query(
            String text,
            UUID ownerOperatorId,
            OwnerState ownerState,
            Instant createdFrom,
            Instant createdTo,
            int page,
            int size
    ) {
        return query(
                text, ownerOperatorId, ownerState, createdFrom, createdTo, null, page, size
        );
    }

    private static SearchLeadsQuery query(
            String text,
            UUID ownerOperatorId,
            OwnerState ownerState,
            Instant createdFrom,
            Instant createdTo,
            UUID branchId,
            int page,
            int size
    ) {
        return new SearchLeadsQuery(
                ACTOR_ID,
                text,
                branchId,
                null,
                null,
                ownerOperatorId,
                ownerState,
                false,
                createdFrom,
                createdTo,
                page,
                size
        );
    }

    private static final class RecordingPort implements LeadQueryPort {

        private final boolean branchAccess;
        private final LeadQueryResult result;
        private int searchCalls;
        private Instant asOf;

        private RecordingPort(boolean branchAccess, LeadQueryResult result) {
            this.branchAccess = branchAccess;
            this.result = result;
        }

        @Override
        public boolean hasBranchAccess(UUID actorUserId, UUID branchId) {
            return branchAccess;
        }

        @Override
        public LeadQueryResult search(SearchLeadsQuery query, Instant asOf) {
            searchCalls++;
            this.asOf = asOf;
            return result;
        }
    }
}
