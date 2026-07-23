package uz.oxukids.ebogcha.crm.domain.model;

import com.google.i18n.phonenumbers.PhoneNumberUtil;
import org.junit.jupiter.api.Test;
import uz.oxukids.ebogcha.crm.domain.exception.InvalidPhoneNumberException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhoneNumberTest {

    private static final PhoneNumberUtil UTIL = PhoneNumberUtil.getInstance();

    @Test
    void normalizesNationalFormattingWithApprovedDefaultRegion() {
        var example = UTIL.getExampleNumber(PhoneNumber.APPROVED_DEFAULT_REGION);
        String national = UTIL.format(example, PhoneNumberUtil.PhoneNumberFormat.NATIONAL);
        String expected = UTIL.format(example, PhoneNumberUtil.PhoneNumberFormat.E164);

        assertThat(PhoneNumber.of(national).canonicalValue()).isEqualTo(expected);
    }

    @Test
    void normalizesDoubleZeroInternationalPrefixAndPunctuation() {
        var example = UTIL.getExampleNumber("GB");
        String international = UTIL.format(example, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL);
        String doubleZero = "00" + international.substring(1);
        String expected = UTIL.format(example, PhoneNumberUtil.PhoneNumberFormat.E164);

        assertThat(PhoneNumber.of(doubleZero).canonicalValue()).isEqualTo(expected);
    }

    @Test
    void canonicalValueDefinesEqualityAcrossRepresentations() {
        var example = UTIL.getExampleNumber("UZ");
        String national = UTIL.format(example, PhoneNumberUtil.PhoneNumberFormat.NATIONAL);
        String international = UTIL.format(example, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL);

        assertThat(PhoneNumber.of(national)).isEqualTo(PhoneNumber.of(international));
    }

    @Test
    void rejectsNullBlankAndClearlyInvalidValues() {
        assertThatThrownBy(() -> PhoneNumber.of(null)).isInstanceOf(InvalidPhoneNumberException.class);
        assertThatThrownBy(() -> PhoneNumber.of("  ")).isInstanceOf(InvalidPhoneNumberException.class);
        assertThatThrownBy(() -> PhoneNumber.of("123")).isInstanceOf(InvalidPhoneNumberException.class);
    }

    @Test
    void stringRepresentationMasksCanonicalValue() {
        var example = UTIL.getExampleNumber("UZ");
        PhoneNumber phoneNumber = PhoneNumber.of(
                UTIL.format(example, PhoneNumberUtil.PhoneNumberFormat.E164)
        );

        assertThat(phoneNumber.toString())
                .startsWith("+")
                .contains("*")
                .doesNotContain(phoneNumber.canonicalValue());
    }
}
