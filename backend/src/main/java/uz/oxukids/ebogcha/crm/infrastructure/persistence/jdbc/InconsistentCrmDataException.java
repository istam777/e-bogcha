package uz.oxukids.ebogcha.crm.infrastructure.persistence.jdbc;

public final class InconsistentCrmDataException extends CrmPersistenceException {

    public InconsistentCrmDataException(String message) {
        super(message);
    }
}
