package uz.oxukids.ebogcha.crm.infrastructure.web;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.oxukids.ebogcha.crm.application.port.in.AcceptLeadCommand;
import uz.oxukids.ebogcha.crm.application.port.in.AcceptLeadUseCase;
import uz.oxukids.ebogcha.crm.application.port.in.ChangeLeadStatusCommand;
import uz.oxukids.ebogcha.crm.application.port.in.ChangeLeadStatusUseCase;
import uz.oxukids.ebogcha.crm.application.port.in.CreateLeadCommand;
import uz.oxukids.ebogcha.crm.application.port.in.CreateLeadUseCase;
import uz.oxukids.ebogcha.crm.application.port.in.GetLeadUseCase;
import uz.oxukids.ebogcha.crm.application.port.in.OwnerState;
import uz.oxukids.ebogcha.crm.application.port.in.SearchLeadsQuery;
import uz.oxukids.ebogcha.crm.application.port.in.SearchLeadsUseCase;
import uz.oxukids.ebogcha.crm.domain.model.LeadSource;
import uz.oxukids.ebogcha.crm.domain.model.LeadStatus;

import java.net.URI;
import java.time.Instant;
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
    private final SearchLeadsUseCase searchLeadsUseCase;
    private final CrmLeadSearchApiMapper searchMapper;

    public CrmLeadController(
            CreateLeadUseCase createLeadUseCase,
            GetLeadUseCase getLeadUseCase,
            AcceptLeadUseCase acceptLeadUseCase,
            ChangeLeadStatusUseCase changeLeadStatusUseCase,
            CrmLeadApiMapper mapper,
            SearchLeadsUseCase searchLeadsUseCase,
            CrmLeadSearchApiMapper searchMapper
    ) {
        this.createLeadUseCase = createLeadUseCase;
        this.getLeadUseCase = getLeadUseCase;
        this.acceptLeadUseCase = acceptLeadUseCase;
        this.changeLeadStatusUseCase = changeLeadStatusUseCase;
        this.mapper = mapper;
        this.searchLeadsUseCase = searchLeadsUseCase;
        this.searchMapper = searchMapper;
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

    @GetMapping
    public LeadSearchResponse searchLeads(
            @RequestHeader(name = ACTOR_HEADER, required = false) String actorHeader,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) LeadStatus status,
            @RequestParam(required = false) LeadSource source,
            @RequestParam(required = false) UUID ownerOperatorId,
            @RequestParam(defaultValue = "ALL") OwnerState ownerState,
            @RequestParam(defaultValue = "false") boolean overdueOnly,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        UUID actorUserId = parseActor(actorHeader);
        return searchMapper.toResponse(searchLeadsUseCase.searchLeads(new SearchLeadsQuery(
                actorUserId,
                q,
                branchId,
                status,
                source,
                ownerOperatorId,
                ownerState,
                overdueOnly,
                createdFrom,
                createdTo,
                page,
                size
        )));
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
