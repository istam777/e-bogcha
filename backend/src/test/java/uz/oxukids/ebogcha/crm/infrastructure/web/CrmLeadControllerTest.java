package uz.oxukids.ebogcha.crm.infrastructure.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uz.oxukids.ebogcha.crm.application.port.in.AcceptLeadUseCase;
import uz.oxukids.ebogcha.crm.application.port.in.ChangeLeadStatusUseCase;
import uz.oxukids.ebogcha.crm.application.port.in.CreateLeadUseCase;
import uz.oxukids.ebogcha.crm.application.port.in.GetLeadUseCase;
import uz.oxukids.ebogcha.crm.domain.exception.InvalidLeadStatusTransitionException;
import uz.oxukids.ebogcha.crm.domain.exception.LeadNotFoundException;
import uz.oxukids.ebogcha.crm.domain.model.LeadStatus;
import uz.oxukids.ebogcha.crm.infrastructure.persistence.jdbc.BranchOutsideOrganizationException;
import uz.oxukids.ebogcha.crm.infrastructure.persistence.jdbc.CrmPersistenceException;
import uz.oxukids.ebogcha.crm.infrastructure.persistence.jdbc.CrmReferenceDataNotFoundException;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CrmLeadController.class)
@Import({CrmApiExceptionHandler.class, CrmLeadApiMapper.class})
class CrmLeadControllerTest {

    private static final UUID LEAD_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID ORGANIZATION_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID BRANCH_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID ACTOR_ID = UUID.fromString("44444444-4444-4444-8444-444444444444");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CreateLeadUseCase createLeadUseCase;

    @MockitoBean
    private GetLeadUseCase getLeadUseCase;

    @MockitoBean
    private AcceptLeadUseCase acceptLeadUseCase;

    @MockitoBean
    private ChangeLeadStatusUseCase changeLeadStatusUseCase;

    @Test
    void blankGuardianReturnsStructuredBadRequest() throws Exception {
        mvc.perform(post("/api/v1/crm/leads")
                        .contentType(APPLICATION_JSON)
                        .content(validCreateJson().replace("Fictional Guardian", " ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CRM_REQUEST_INVALID"))
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.type").isString())
                .andExpect(jsonPath("$.title").isString())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").isString())
                .andExpect(jsonPath("$.instance").value("/api/v1/crm/leads"));
    }

    @Test
    void missingLeadReturnsNotFound() throws Exception {
        when(getLeadUseCase.getLead(LEAD_ID)).thenThrow(new LeadNotFoundException(LEAD_ID));

        mvc.perform(get("/api/v1/crm/leads/{leadId}", LEAD_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CRM_LEAD_NOT_FOUND"));
    }

    @Test
    void missingActorHeaderReturnsActorError() throws Exception {
        mvc.perform(post("/api/v1/crm/leads/{leadId}/accept", LEAD_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CRM_ACTOR_INVALID"));
    }

    @Test
    void malformedActorHeaderReturnsActorError() throws Exception {
        mvc.perform(post("/api/v1/crm/leads/{leadId}/accept", LEAD_ID)
                        .header("X-Actor-User-Id", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CRM_ACTOR_INVALID"));
    }

    @Test
    void unsupportedSourceReturnsStructuredBadRequest() throws Exception {
        mvc.perform(post("/api/v1/crm/leads")
                        .contentType(APPLICATION_JSON)
                        .content(validCreateJson().replace("PHONE", "UNSUPPORTED")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CRM_REQUEST_INVALID"));
    }

    @Test
    void malformedJsonReturnsStructuredBadRequest() throws Exception {
        mvc.perform(post("/api/v1/crm/leads")
                        .contentType(APPLICATION_JSON)
                        .content("{\"leadId\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CRM_REQUEST_INVALID"));
    }

    @Test
    void persistenceFailureReturnsSanitizedInternalError() throws Exception {
        String sensitiveDatabaseMessage = "SQL failure for +998901234567 and Fictional Guardian";
        when(getLeadUseCase.getLead(LEAD_ID))
                .thenThrow(new CrmPersistenceException(sensitiveDatabaseMessage));

        mvc.perform(get("/api/v1/crm/leads/{leadId}", LEAD_ID))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("CRM_INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("The request could not be completed."))
                .andExpect(content().string(not(containsString(sensitiveDatabaseMessage))))
                .andExpect(content().string(not(containsString("+998901234567"))));
    }

    @Test
    void branchOutsideOrganizationReturnsUnprocessableEntity() throws Exception {
        when(createLeadUseCase.createLead(any()))
                .thenThrow(new BranchOutsideOrganizationException(BRANCH_ID, ORGANIZATION_ID));

        mvc.perform(post("/api/v1/crm/leads")
                        .contentType(APPLICATION_JSON)
                        .content(validCreateJson()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CRM_BRANCH_ORGANIZATION_INVALID"));
    }

    @Test
    void invalidReferenceReturnsUnprocessableEntity() throws Exception {
        when(createLeadUseCase.createLead(any()))
                .thenThrow(new CrmReferenceDataNotFoundException("lead source", "PHONE"));

        mvc.perform(post("/api/v1/crm/leads")
                        .contentType(APPLICATION_JSON)
                        .content(validCreateJson()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CRM_REFERENCE_INVALID"));
    }

    @Test
    void invalidStatusTransitionReturnsUnprocessableEntity() throws Exception {
        when(changeLeadStatusUseCase.changeLeadStatus(any()))
                .thenThrow(new InvalidLeadStatusTransitionException(LeadStatus.NEW, LeadStatus.SUCCESSFUL));

        mvc.perform(post("/api/v1/crm/leads/{leadId}/status-transitions", LEAD_ID)
                        .header("X-Actor-User-Id", ACTOR_ID)
                        .contentType(APPLICATION_JSON)
                        .content("{\"targetStatus\":\"SUCCESSFUL\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CRM_STATUS_TRANSITION_INVALID"));
    }

    private static String validCreateJson() {
        return """
                {
                  "leadId": "%s",
                  "organizationId": "%s",
                  "branchId": "%s",
                  "source": "PHONE",
                  "parentOrGuardianName": "Fictional Guardian",
                  "displayPhone": "+998 90 123 45 67"
                }
                """.formatted(LEAD_ID, ORGANIZATION_ID, BRANCH_ID);
    }
}
