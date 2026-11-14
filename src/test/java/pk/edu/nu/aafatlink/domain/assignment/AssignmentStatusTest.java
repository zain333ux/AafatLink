package pk.edu.nu.aafatlink.domain.assignment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AssignmentStatusTest {

    @Test
    void followsDocumentedMainLifecycle() {
        assertTrue(AssignmentStatus.PENDING_ACCEPTANCE.canTransitionTo(AssignmentStatus.ACCEPTED));
        assertTrue(AssignmentStatus.ACCEPTED.canTransitionTo(AssignmentStatus.IN_PROGRESS));
        assertTrue(AssignmentStatus.IN_PROGRESS.canTransitionTo(AssignmentStatus.AWAITING_CLOSURE_REVIEW));
        assertTrue(AssignmentStatus.AWAITING_CLOSURE_REVIEW.canTransitionTo(AssignmentStatus.COMPLETED));
        assertFalse(AssignmentStatus.COMPLETED.canTransitionTo(AssignmentStatus.IN_PROGRESS));
    }

    @Test
    void supportsDeclineReassignmentAndClosureRework() {
        assertTrue(AssignmentStatus.PENDING_ACCEPTANCE.canTransitionTo(AssignmentStatus.DECLINED));
        assertTrue(AssignmentStatus.PENDING_ACCEPTANCE.canTransitionTo(AssignmentStatus.REASSIGNED));
        assertTrue(AssignmentStatus.AWAITING_CLOSURE_REVIEW.canTransitionTo(AssignmentStatus.IN_PROGRESS));
        assertFalse(AssignmentStatus.DECLINED.canTransitionTo(AssignmentStatus.ACCEPTED));
    }
}
