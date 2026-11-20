package pk.edu.nu.aafatlink.domain.assignment;

import java.util.Objects;

import pk.edu.nu.aafatlink.domain.request.AssistanceRequest;

/** Assignment plus the request details displayed by UC08. */
public record AssignmentContext(VolunteerAssignment assignment, AssistanceRequest request) {
    public AssignmentContext {
        Objects.requireNonNull(assignment, "assignment");
        Objects.requireNonNull(request, "request");
        if (!assignment.requestId().equals(request.id())) {
            throw new IllegalArgumentException("Assignment and request identities do not match.");
        }
    }
}
