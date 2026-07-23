package uz.oxukids.ebogcha.crm.infrastructure.web;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.oxukids.ebogcha.crm.application.port.in.AcceptLeadCommand;
import uz.oxukids.ebogcha.crm.application.port.in.AcceptLeadUseCase;
import uz.oxukids.ebogcha.crm.application.port.in.ChangeLeadStatusCommand;
import uz.oxukids.ebogcha.crm.application.port.in.ChangeLeadStatusUseCase;
import uz.oxukids.ebogcha.crm.application.port.in.CreateLeadCommand;
import uz.oxukids.ebogcha.crm.application.port.in.CreateLeadUseCase;
import uz.oxukids.ebogcha.crm.application.port.in.GetLeadUseCase;
import uz.oxukids.ebogcha.crm.domain.exception.LostReasonRequiredException;
import uz.oxukids.ebogcha.crm.domain.model.LeadStatus;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/crm/leads")
public class CrmLeadController {

    private static final String ACTOR_HEADER = "X-Actor-User-Id";

    private final CreateLeadUseCase createLeadUseCase;
    private final GetLeadUseCase getLeadUseCase;
    private final AcceptLeadUseCase acceptLeadUseCase;
    private final ChangeLeadStatusUseCase changeLeadStatusUseCase;
    private final CrmLeadApiMapper mapper;

    public CrmLeadController(
            CreateLeadUseCase createLeadUseCase,
            GetLeadUseCase getLeadUseCase,
            AcceptLeadUseCase acceptLeadUseCase,
            ChangeLeadStatusUseCase changeLeadStatusUseCase,
            CrmLeadApiMapper mapper
    ) {
        this.createLeadUseCase = createLeadUseCase;
        this.getLeadUseCase = getLeadUseCase;
        this.acceptLeadUseCase = acceptLeadUseCase;
        this.changeLeadStatusUseCase = changeLeadStatusUseCase;
        this.mapper = mapper;
    }

    @PostMapping
    public ResponseEntity<CreateLeadResponse> createLead(@Valid @RequestBody CreateLeadRequest request) {
        var result = createLeadUseCase.createLead(new CreateLeadCommand(
                request.leadId(),
                request.organizationId(),
                request.branchId(),
                request.source(),
                request.parentOrGuardianName(),
                request.displayPhone()
        ));
        return ResponseEntity
                .created(URI.create("/api/v1/crm/leads/" + result.lead().id()))
                .body(mapper.toCreateResponse(result));
    }

    @GetMapping("/{leadId}")
    public LeadResponse getLead(@PathVariable UUID leadId) {
        return mapper.toResponse(getLeadUseCase.getLead(leadId));
    }

    @PostMapping("/{leadId}/accept")
    public LeadResponse acceptLead(
            @PathVariable UUID leadId,
            @RequestHeader(name = ACTOR_HEADER, required = false) String actorHeader
    ) {
        UUID actorUserId = parseActor(actorHeader);
        return mapper.toResponse(acceptLeadUseCase.acceptLead(new AcceptLeadCommand(leadId, actorUserId)));
    }

    @PostMapping("/{leadId}/status-transitions")
    public LeadResponse changeStatus(
            @PathVariable UUID leadId,
            @RequestHeader(name = ACTOR_HEADER, required = false) String actorHeader,
            @Valid @RequestBody ChangeLeadStatusRequest request
    ) {
        UUID actorUserId = parseActor(actorHeader);
        if (request.targetStatus() == LeadStatus.LOST && request.lostReasonId() == null) {
            throw new LostReasonRequiredException();
        }
        return mapper.toResponse(changeLeadStatusUseCase.changeLeadStatus(new ChangeLeadStatusCommand(
                leadId,
                request.targetStatus(),
                request.lostReasonId(),
                actorUserId
        )));
    }

    private static UUID parseActor(String actorHeader) {
        if (actorHeader == null || actorHeader.isBlank()) {
            throw new InvalidActorHeaderException();
        }
        try {
            return UUID.fromString(actorHeader);
        } catch (IllegalArgumentException exception) {
            throw new InvalidActorHeaderException();
        }
    }
}
