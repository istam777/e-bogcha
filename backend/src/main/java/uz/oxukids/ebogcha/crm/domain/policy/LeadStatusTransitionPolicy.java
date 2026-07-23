package uz.oxukids.ebogcha.crm.domain.policy;

import uz.oxukids.ebogcha.crm.domain.model.LeadStatus;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class LeadStatusTransitionPolicy {

    private final Map<LeadStatus, Set<LeadStatus>> transitions;

    private LeadStatusTransitionPolicy(Map<LeadStatus, Set<LeadStatus>> transitions) {
        EnumMap<LeadStatus, Set<LeadStatus>> copy = new EnumMap<>(LeadStatus.class);
        transitions.forEach((source, targets) -> copy.put(source, Set.copyOf(targets)));
        this.transitions = Map.copyOf(copy);
    }

    public static LeadStatusTransitionPolicy standard() {
        EnumMap<LeadStatus, Set<LeadStatus>> transitions = new EnumMap<>(LeadStatus.class);
        transitions.put(LeadStatus.NEW,
                EnumSet.of(LeadStatus.CONTACTED, LeadStatus.LOST, LeadStatus.ARCHIVED));
        transitions.put(LeadStatus.CONTACTED,
                EnumSet.of(LeadStatus.TOUR_PLANNED, LeadStatus.SUCCESSFUL,
                        LeadStatus.LOST, LeadStatus.ARCHIVED));
        transitions.put(LeadStatus.TOUR_PLANNED,
                EnumSet.of(LeadStatus.SUCCESSFUL, LeadStatus.NO_SHOW,
                        LeadStatus.LOST, LeadStatus.ARCHIVED));
        transitions.put(LeadStatus.NO_SHOW,
                EnumSet.of(LeadStatus.CONTACTED, LeadStatus.TOUR_PLANNED,
                        LeadStatus.LOST, LeadStatus.ARCHIVED));
        transitions.put(LeadStatus.SUCCESSFUL, EnumSet.of(LeadStatus.LOST, LeadStatus.ARCHIVED));
        transitions.put(LeadStatus.LOST, EnumSet.of(LeadStatus.NEW, LeadStatus.ARCHIVED));
        transitions.put(LeadStatus.ARCHIVED, EnumSet.of(LeadStatus.NEW));
        return new LeadStatusTransitionPolicy(transitions);
    }

    public boolean allows(LeadStatus current, LeadStatus target) {
        Objects.requireNonNull(current, "current status must not be null");
        Objects.requireNonNull(target, "target status must not be null");
        return current == target || transitions.getOrDefault(current, Set.of()).contains(target);
    }

    public Set<LeadStatus> allowedTargets(LeadStatus current) {
        Objects.requireNonNull(current, "current status must not be null");
        return transitions.getOrDefault(current, Set.of());
    }
}
