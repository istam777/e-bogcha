package uz.oxukids.ebogcha.crm.infrastructure.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uz.oxukids.ebogcha.crm.application.port.out.DuplicateLeadDiscoveryPort;
import uz.oxukids.ebogcha.crm.application.port.out.LeadRepository;
import uz.oxukids.ebogcha.crm.application.service.CrmLeadService;
import uz.oxukids.ebogcha.crm.domain.policy.InitialContactDeadlinePolicy;
import uz.oxukids.ebogcha.crm.domain.policy.LeadStatusTransitionPolicy;
import uz.oxukids.ebogcha.crm.domain.time.CrmClock;

import java.time.Instant;

@Configuration(proxyBeanMethods = false)
public class CrmConfiguration {

    @Bean
    CrmClock crmClock() {
        return Instant::now;
    }

    @Bean
    InitialContactDeadlinePolicy initialContactDeadlinePolicy() {
        return new InitialContactDeadlinePolicy();
    }

    @Bean
    LeadStatusTransitionPolicy leadStatusTransitionPolicy() {
        return LeadStatusTransitionPolicy.standard();
    }

    @Bean
    CrmLeadService crmLeadService(
            LeadRepository leadRepository,
            DuplicateLeadDiscoveryPort duplicateLeadDiscoveryPort,
            CrmClock crmClock,
            InitialContactDeadlinePolicy deadlinePolicy,
            LeadStatusTransitionPolicy transitionPolicy
    ) {
        return new CrmLeadService(
                leadRepository,
                duplicateLeadDiscoveryPort,
                crmClock,
                deadlinePolicy,
                transitionPolicy
        );
    }
}
