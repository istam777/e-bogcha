package uz.oxukids.ebogcha.crm.infrastructure.persistence.jdbc;

public final class UserBranchAccessDeniedException extends CrmPersistenceException {

    public UserBranchAccessDeniedException() {
        super("User is not authorized to claim leads for this branch");
    }
}
