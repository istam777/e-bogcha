package uz.oxukids.ebogcha.crm.domain.model;

import com.google.i18n.phonenumbers.PhoneNumberUtil;
import org.junit.jupiter.api.Test;
import uz.oxukids.ebogcha.crm.domain.exception.InvalidLeadStatusTransitionException;
import uz.oxukids.ebogcha.crm.domain.exception.LeadAlreadyOwnedException;
import uz.oxukids.ebogcha.crm.domain.exception.LostReasonRequiredException;
import uz.oxukids.ebogcha.crm.domain.policy.InitialContactDeadlinePolicy;
import uz.oxukids.ebogcha.crm.domain.policy.LeadStatusTransitionPolicy;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LeadTest {

    private static final UUID LEAD_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID ORGANIZATION_ID = UUID.fromString("20000000-0000-4000-8000-000000000001");
    private static final UUID BRANCH_ID = UUID.fromString("25000000-0000-4000-8000-000000000001");
    private static final UUID FIRST_OPERATOR = UUID.fromString("30000000-0000-4000-8000-000000000001");
    private static final UUID SECOND_OPERATOR = UUID.fromString("30000000-0000-4000-8000-000000000002");
    private static final UUID LOST_REASON_ID = UUID.fromString("40000000-0000-4000-8000-000000000001");

    @Test
    void newLeadStartsNewWithExactTwentyFourHourDeadline() {
        Instant createdAt = Instant.parse("2026-07-22T08:00:00Z");

        Lead lead = createLead(createdAt);

        assertThat(lead.status()).isEqualTo(LeadStatus.NEW);
        assertThat(lead.createdAt()).isEqualTo(createdAt);
        assertThat(lead.firstContactDueAt()).isEqualTo(Instant.parse("2026-07-23T08:00:00Z"));
        assertThat(lead.ownerOperatorId()).isEmpty();
    }

    @Test
    void firstOperatorWinsOwnership() {
        Lead lead = createLead(Instant.parse("2026-07-22T08:00:00Z"));

        lead.accept(FIRST_OPERATOR);

        assertThat(lead.ownerOperatorId()).contains(FIRST_OPERATOR);
    }

    @Test
    void repeatedAcceptanceBySameOperatorIsIdempotent() {
        Lead lead = createLead(Instant.parse("2026-07-22T08:00:00Z"));

        lead.accept(FIRST_OPERATOR);
        lead.accept(FIRST_OPERATOR);

        assertThat(lead.ownerOperatorId()).contains(FIRST_OPERATOR);
    }

    @Test
    void differentOperatorCannotReplaceOwner() {
        Lead lead = createLead(Instant.parse("2026-07-22T08:00:00Z"));
        lead.accept(FIRST_OPERATOR);

        assertThatThrownBy(() -> lead.accept(SECOND_OPERATOR))
                .isInstanceOf(LeadAlreadyOwnedException.class);
        assertThat(lead.ownerOperatorId()).contains(FIRST_OPERATOR);
    }

    @Test
    void aggregateRejectsTransitionOutsidePolicy() {
        Lead lead = createLead(Instant.parse("2026-07-22T08:00:00Z"));

        assertThatThrownBy(() -> lead.changeStatus(
                LeadStatus.SUCCESSFUL, null, LeadStatusTransitionPolicy.standard()
        )).isInstanceOf(InvalidLeadStatusTransitionException.class);
        assertThat(lead.status()).isEqualTo(LeadStatus.NEW);
    }

    @Test
    void enteringLostWithoutReasonIsRejected() {
        Lead lead = createLead(Instant.parse("2026-07-22T08:00:00Z"));

        assertThatThrownBy(() -> lead.changeStatus(
                LeadStatus.LOST, null, LeadStatusTransitionPolicy.standard()
        )).isInstanceOf(LostReasonRequiredException.class);
        assertThat(lead.status()).isEqualTo(LeadStatus.NEW);
        assertThat(lead.lostReasonId()).isEmpty();
    }

    @Test
    void enteringLostWithReasonSucceeds() {
        Lead lead = createLead(Instant.parse("2026-07-22T08:00:00Z"));

        boolean changed = lead.changeStatus(
                LeadStatus.LOST, LOST_REASON_ID, LeadStatusTransitionPolicy.standard()
        );

        assertThat(changed).isTrue();
        assertThat(lead.status()).isEqualTo(LeadStatus.LOST);
        assertThat(lead.lostReasonId()).contains(LOST_REASON_ID);
    }

    @Test
    void reopeningFromLostClearsLostReason() {
        Lead lead = createLead(Instant.parse("2026-07-22T08:00:00Z"));
        lead.changeStatus(LeadStatus.LOST, LOST_REASON_ID, LeadStatusTransitionPolicy.standard());

        lead.changeStatus(LeadStatus.NEW, null, LeadStatusTransitionPolicy.standard());

        assertThat(lead.status()).isEqualTo(LeadStatus.NEW);
        assertThat(lead.lostReasonId()).isEmpty();
    }

    @Test
    void sameLostStatusIsIdempotentWithoutClearingReason() {
        Lead lead = createLead(Instant.parse("2026-07-22T08:00:00Z"));
        lead.changeStatus(LeadStatus.LOST, LOST_REASON_ID, LeadStatusTransitionPolicy.standard());

        boolean changed = lead.changeStatus(
                LeadStatus.LOST, null, LeadStatusTransitionPolicy.standard()
        );

        assertThat(changed).isFalse();
        assertThat(lead.lostReasonId()).contains(LOST_REASON_ID);
    }

    private static Lead createLead(Instant createdAt) {
        var example = PhoneNumberUtil.getInstance().getExampleNumber("UZ");
        String phone = PhoneNumberUtil.getInstance().format(example, PhoneNumberUtil.PhoneNumberFormat.E164);
        return Lead.create(
                LEAD_ID,
                ORGANIZATION_ID,
                BRANCH_ID,
                LeadSource.SOCIAL_MEDIA,
                "Test Guardian",
                phone,
                PhoneNumber.of(phone),
                createdAt,
                new InitialContactDeadlinePolicy()
        );
    }
}
