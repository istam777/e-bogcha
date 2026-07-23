package uz.oxukids.ebogcha.crm.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import uz.oxukids.ebogcha.crm.application.exception.InvalidLeadSearchQueryException;
import uz.oxukids.ebogcha.crm.application.exception.LeadSearchBranchAccessDeniedException;
import uz.oxukids.ebogcha.crm.domain.exception.DuplicateLeadException;
import uz.oxukids.ebogcha.crm.domain.exception.InvalidLeadStatusTransitionException;
import uz.oxukids.ebogcha.crm.domain.exception.InvalidPhoneNumberException;
import uz.oxukids.ebogcha.crm.domain.exception.LeadAlreadyOwnedException;
import uz.oxukids.ebogcha.crm.domain.exception.LeadNotFoundException;
import uz.oxukids.ebogcha.crm.domain.exception.LostReasonRequiredException;
import uz.oxukids.ebogcha.crm.infrastructure.persistence.jdbc.BranchOutsideOrganizationException;
import uz.oxukids.ebogcha.crm.infrastructure.persistence.jdbc.CrmPersistenceException;
import uz.oxukids.ebogcha.crm.infrastructure.persistence.jdbc.CrmReferenceDataNotFoundException;
import uz.oxukids.ebogcha.crm.infrastructure.persistence.jdbc.UserBranchAccessDeniedException;

import java.net.URI;
import java.time.Instant;

@RestControllerAdvice
public class CrmApiExceptionHandler {

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            ConstraintViolationException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            InvalidLeadSearchQueryException.class,
            InvalidPhoneNumberException.class
    })
    ResponseEntity<ProblemDetail> invalidRequest(Exception exception, HttpServletRequest request) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "CRM_REQUEST_INVALID",
                "Invalid CRM request",
                "The request body, path, or parameters are invalid.",
                request
        );
    }

    @ExceptionHandler(InvalidActorHeaderException.class)
    ResponseEntity<ProblemDetail> invalidActor(
            InvalidActorHeaderException exception,
            HttpServletRequest request
    ) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "CRM_ACTOR_INVALID",
                "Invalid CRM actor",
                "X-Actor-User-Id must contain a valid UUID.",
                request
        );
    }

    @ExceptionHandler(LeadNotFoundException.class)
    ResponseEntity<ProblemDetail> leadNotFound(LeadNotFoundException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.NOT_FOUND,
                "CRM_LEAD_NOT_FOUND",
                "CRM lead not found",
                "The requested lead does not exist.",
                request
        );
    }

    @ExceptionHandler({
            UserBranchAccessDeniedException.class,
            LeadSearchBranchAccessDeniedException.class
    })
    ResponseEntity<ProblemDetail> branchAccessDenied(
            Exception exception,
            HttpServletRequest request
    ) {
        return problem(
                HttpStatus.FORBIDDEN,
                "CRM_BRANCH_ACCESS_DENIED",
                "CRM branch access denied",
                "The actor does not have access to this lead's branch.",
                request
        );
    }

    @ExceptionHandler(LeadAlreadyOwnedException.class)
    ResponseEntity<ProblemDetail> leadAlreadyOwned(
            LeadAlreadyOwnedException exception,
            HttpServletRequest request
    ) {
        return problem(
                HttpStatus.CONFLICT,
                "CRM_LEAD_ALREADY_OWNED",
                "CRM lead already owned",
                "The lead is already owned by another operator.",
                request
        );
    }

    @ExceptionHandler(DuplicateLeadException.class)
    ResponseEntity<ProblemDetail> duplicateLead(DuplicateLeadException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.CONFLICT,
                "CRM_LEAD_DUPLICATE",
                "Duplicate CRM lead",
                "A lead with the same identity already exists.",
                request
        );
    }

    @ExceptionHandler(InvalidLeadStatusTransitionException.class)
    ResponseEntity<ProblemDetail> invalidStatusTransition(
            InvalidLeadStatusTransitionException exception,
            HttpServletRequest request
    ) {
        return problem(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "CRM_STATUS_TRANSITION_INVALID",
                "Invalid CRM status transition",
                "The requested lead status transition is not allowed.",
                request
        );
    }

    @ExceptionHandler(LostReasonRequiredException.class)
    ResponseEntity<ProblemDetail> lostReasonRequired(
            LostReasonRequiredException exception,
            HttpServletRequest request
    ) {
        return problem(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "CRM_LOST_REASON_REQUIRED",
                "CRM lost reason required",
                "lostReasonId is required when the target status is LOST.",
                request
        );
    }

    @ExceptionHandler(BranchOutsideOrganizationException.class)
    ResponseEntity<ProblemDetail> branchOutsideOrganization(
            BranchOutsideOrganizationException exception,
            HttpServletRequest request
    ) {
        return problem(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "CRM_BRANCH_ORGANIZATION_INVALID",
                "Invalid CRM branch",
                "The branch does not belong to the specified organization.",
                request
        );
    }

    @ExceptionHandler(CrmReferenceDataNotFoundException.class)
    ResponseEntity<ProblemDetail> referenceDataNotFound(
            CrmReferenceDataNotFoundException exception,
            HttpServletRequest request
    ) {
        return problem(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "CRM_REFERENCE_INVALID",
                "Invalid CRM reference data",
                "One or more CRM reference values are unavailable.",
                request
        );
    }

    @ExceptionHandler(CrmPersistenceException.class)
    ResponseEntity<ProblemDetail> persistenceFailure(
            CrmPersistenceException exception,
            HttpServletRequest request
    ) {
        return internalError(request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpectedFailure(Exception exception, HttpServletRequest request) {
        return internalError(request);
    }

    private static ResponseEntity<ProblemDetail> internalError(HttpServletRequest request) {
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "CRM_INTERNAL_ERROR",
                "CRM service error",
                "The request could not be completed.",
                request
        );
    }

    private static ResponseEntity<ProblemDetail> problem(
            HttpStatus status,
            String code,
            String title,
            String detail,
            HttpServletRequest request
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("urn:problem:" + code.toLowerCase().replace('_', '-')));
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        problem.setProperty("timestamp", Instant.now().toString());
        return ResponseEntity.status(status).body(problem);
    }
}
