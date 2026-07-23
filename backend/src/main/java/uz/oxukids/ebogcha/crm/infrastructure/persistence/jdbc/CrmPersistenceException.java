package uz.oxukids.ebogcha.crm.infrastructure.persistence.jdbc;

public class CrmPersistenceException extends RuntimeException {

    public CrmPersistenceException(String message) {
        super(message);
    }

    public CrmPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
