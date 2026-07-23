package uz.oxukids.ebogcha.crm.domain.model;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import uz.oxukids.ebogcha.crm.domain.exception.InvalidPhoneNumberException;

import java.util.Locale;
import java.util.Objects;

public final class PhoneNumber {

    public static final String APPROVED_DEFAULT_REGION = "UZ";

    private static final PhoneNumberUtil PHONE_NUMBER_UTIL = PhoneNumberUtil.getInstance();

    private final String canonicalValue;

    private PhoneNumber(String canonicalValue) {
        this.canonicalValue = canonicalValue;
    }

    public static PhoneNumber of(String input) {
        return of(input, APPROVED_DEFAULT_REGION);
    }

    public static PhoneNumber of(String input, String defaultRegion) {
        if (input == null || input.isBlank()) {
            throw new InvalidPhoneNumberException("Phone number must not be null or blank");
        }
        if (defaultRegion == null || defaultRegion.isBlank()) {
            throw new InvalidPhoneNumberException("Default phone region must not be null or blank");
        }

        String candidate = normalizeInternationalPrefix(input.strip());
        String region = defaultRegion.strip().toUpperCase(Locale.ROOT);
        try {
            var parsed = PHONE_NUMBER_UTIL.parse(candidate, region);
            if (!PHONE_NUMBER_UTIL.isPossibleNumber(parsed) || !PHONE_NUMBER_UTIL.isValidNumber(parsed)) {
                throw new InvalidPhoneNumberException("Phone number is not a valid public telephone number");
            }
            return new PhoneNumber(PHONE_NUMBER_UTIL.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164));
        } catch (NumberParseException exception) {
            throw new InvalidPhoneNumberException("Phone number cannot be parsed", exception);
        }
    }

    private static String normalizeInternationalPrefix(String value) {
        return value.startsWith("00") ? "+" + value.substring(2) : value;
    }

    public String canonicalValue() {
        return canonicalValue;
    }

    public String maskedValue() {
        int visibleDigits = Math.min(4, canonicalValue.length() - 1);
        int maskedLength = canonicalValue.length() - 1 - visibleDigits;
        return "+" + "*".repeat(Math.max(maskedLength, 0))
                + canonicalValue.substring(canonicalValue.length() - visibleDigits);
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof PhoneNumber that
                && canonicalValue.equals(that.canonicalValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(canonicalValue);
    }

    @Override
    public String toString() {
        return maskedValue();
    }
}
