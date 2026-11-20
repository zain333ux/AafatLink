package pk.edu.nu.aafatlink.domain.assignment;

import java.time.Instant;

/** Coordinator-selected skill, area and optional schedule used by UC05. */
public record AssignmentFilter(long skillId, long serviceAreaId,
        Instant scheduledStartAt, Instant scheduledEndAt) {
    public AssignmentFilter {
        if (skillId <= 0 || serviceAreaId <= 0) {
            throw new IllegalArgumentException("Select a required skill and service area.");
        }
        if ((scheduledStartAt == null) != (scheduledEndAt == null)) {
            throw new IllegalArgumentException("Provide both assignment times or leave both empty.");
        }
        if (scheduledStartAt != null && !scheduledStartAt.isBefore(scheduledEndAt)) {
            throw new IllegalArgumentException("Assignment end must be after its start.");
        }
    }
}
