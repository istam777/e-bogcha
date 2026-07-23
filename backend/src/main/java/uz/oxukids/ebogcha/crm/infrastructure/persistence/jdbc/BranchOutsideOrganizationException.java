package uz.oxukids.ebogcha.crm.infrastructure.persistence.jdbc;

import java.util.UUID;

public final class BranchOutsideOrganizationException extends CrmPersistenceException {

    public BranchOutsideOrganizationException(UUID branchId, UUID organizationId) {
        super("Branch " + branchId + " does not belong to organization " + organizationId);
    }
}
