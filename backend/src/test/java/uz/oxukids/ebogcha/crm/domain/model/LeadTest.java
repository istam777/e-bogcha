package uz.oxukids.ebogcha.crm.domain.model;

import com.google.i18n.phonenumbers.PhoneNumberUtil;
import org.junit.jupiter.api.Test;
import uz.oxukids.ebogcha.crm.domain.exception.LeadAlreadyOwnedException;
import uz.oxukids.ebogcha.crm.domain.exception.InvalidLeadStatusTransitionException;
import uz.oxukids.ebogcha.crm.domain.policy.InitialContactDeadlinePolicy;
import uz.oxukids.ebogcha.crm.domain.policy.LeadStatusTransitionPolicy;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LeadTest {

    private static final UUID LEAD_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID ORGANIZATION_ID = UUID.fromString("20000000-0000-4000-8000-000000000001");
    private static final UUID FIRST_OPERATOR = UUID.fromString("30000000-0000-4000-8000-000000000001");
    private static final UUID SECOND_OPERATOR = UUID.fromString("30000000-0000-4000-8000-000000000002");

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
                LeadStatus.SUCCESSFUL, LeadStatusTransitionPolicy.standard()
        )).isInstanceOf(InvalidLeadStatusTransitionException.class);
        assertThat(lead.status()).isEqualTo(LeadStatus.NEW);
    }

    private static Lead createLead(Instant createdAt) {
        var example = PhoneNumberUtil.getInstance().getExampleNumber("UZ");
        String phone = PhoneNumberUtil.getInstance().format(example, PhoneNumberUtil.PhoneNumberFormat.E164);
        return Lead.create(
                LEAD_ID,
                ORGANIZATION_ID,
                LeadSource.SOCIAL_MEDIA,
                PhoneNumber.of(phone),
                createdAt,
                new InitialContactDeadlinePolicy()
        );
    }
}
