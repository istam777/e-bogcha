package uz.oxukids.ebogcha.crm.domain.policy;

import org.junit.jupiter.api.Test;
import uz.oxukids.ebogcha.crm.domain.model.LeadStatus;

import static org.assertj.core.api.Assertions.assertThat;

class LeadStatusTransitionPolicyTest {

    private final LeadStatusTransitionPolicy policy = LeadStatusTransitionPolicy.standard();

    @Test
    void supportsPrimaryWorkflowAndNoShowRecovery() {
        assertThat(policy.allows(LeadStatus.NEW, LeadStatus.CONTACTED)).isTrue();
        assertThat(policy.allows(LeadStatus.CONTACTED, LeadStatus.TOUR_PLANNED)).isTrue();
        assertThat(policy.allows(LeadStatus.TOUR_PLANNED, LeadStatus.SUCCESSFUL)).isTrue();
        assertThat(policy.allows(LeadStatus.TOUR_PLANNED, LeadStatus.NO_SHOW)).isTrue();
        assertThat(policy.allows(LeadStatus.NO_SHOW, LeadStatus.TOUR_PLANNED)).isTrue();
    }

    @Test
    void allowsArchivingFromEveryActiveStatusAndReopeningAtNew() {
        for (LeadStatus status : LeadStatus.values()) {
            if (status != LeadStatus.ARCHIVED) {
                assertThat(policy.allows(status, LeadStatus.ARCHIVED)).as(status.name()).isTrue();
            }
        }
        assertThat(policy.allows(LeadStatus.ARCHIVED, LeadStatus.NEW)).isTrue();
    }

    @Test
    void sameStatusIsIdempotent() {
        for (LeadStatus status : LeadStatus.values()) {
            assertThat(policy.allows(status, status)).as(status.name()).isTrue();
        }
    }

    @Test
    void rejectsArbitraryWorkflowJumps() {
        assertThat(policy.allows(LeadStatus.NEW, LeadStatus.SUCCESSFUL)).isFalse();
        assertThat(policy.allows(LeadStatus.SUCCESSFUL, LeadStatus.CONTACTED)).isFalse();
        assertThat(policy.allows(LeadStatus.ARCHIVED, LeadStatus.SUCCESSFUL)).isFalse();
    }
}
