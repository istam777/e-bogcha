package uz.oxukids.ebogcha.crm.infrastructure.persistence.jdbc;

public final class CrmReferenceDataNotFoundException extends CrmPersistenceException {

    public CrmReferenceDataNotFoundException(String referenceType, String code) {
        super("Required CRM " + referenceType + " code is unavailable: " + code);
    }
}
