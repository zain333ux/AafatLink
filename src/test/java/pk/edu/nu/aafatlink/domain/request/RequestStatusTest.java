package pk.edu.nu.aafatlink.domain.request;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RequestStatusTest {

    @Test
    void followsDocumentedMainLifecycle() {
        assertTrue(RequestStatus.PENDING_VERIFICATION.canTransitionTo(RequestStatus.VERIFIED));
        assertTrue(RequestStatus.VERIFIED.canTransitionTo(RequestStatus.ASSIGNED));
        assertTrue(RequestStatus.ASSIGNED.canTransitionTo(RequestStatus.IN_PROGRESS));
        assertTrue(RequestStatus.IN_PROGRESS.canTransitionTo(RequestStatus.AWAITING_CLOSURE_REVIEW));
        assertTrue(RequestStatus.AWAITING_CLOSURE_REVIEW.canTransitionTo(RequestStatus.CLOSED));
        assertFalse(RequestStatus.CLOSED.canTransitionTo(RequestStatus.IN_PROGRESS));
    }

    @Test
    void supportsInformationResubmissionAndReworkWithoutInventingPriorityStatus() {
        assertTrue(RequestStatus.PENDING_VERIFICATION.canTransitionTo(RequestStatus.ADDITIONAL_INFORMATION_REQUIRED));
        assertTrue(RequestStatus.VERIFIED.canTransitionTo(RequestStatus.ADDITIONAL_INFORMATION_REQUIRED));
        assertTrue(RequestStatus.ADDITIONAL_INFORMATION_REQUIRED.canTransitionTo(RequestStatus.PENDING_VERIFICATION));
        assertTrue(RequestStatus.AWAITING_CLOSURE_REVIEW.canTransitionTo(RequestStatus.IN_PROGRESS));
        assertFalse(RequestStatus.VERIFIED.canTransitionTo(RequestStatus.CLOSED));
    }

    @Test
    void exposesRequesterFriendlyStatusLabelsAndResubmissionRule() {
        assertEquals("Pending Verification", RequestStatus.PENDING_VERIFICATION.displayName());
        assertEquals("Awaiting Closure Review", RequestStatus.AWAITING_CLOSURE_REVIEW.displayName());
        assertTrue(RequestStatus.ADDITIONAL_INFORMATION_REQUIRED.canRequesterResubmit());
        assertFalse(RequestStatus.PENDING_VERIFICATION.canRequesterResubmit());
        assertFalse(RequestStatus.REJECTED.canRequesterResubmit());
    }

    @Test
    void prioritizationEligibilityMatchesUc04() {
        assertTrue(RequestStatus.VERIFIED.canBePrioritized());
        assertTrue(RequestStatus.ASSIGNED.canBePrioritized());
        assertTrue(RequestStatus.IN_PROGRESS.canBePrioritized());
        assertFalse(RequestStatus.PENDING_VERIFICATION.canBePrioritized());
        assertFalse(RequestStatus.AWAITING_CLOSURE_REVIEW.canBePrioritized());
        assertFalse(RequestStatus.CLOSED.canBePrioritized());
    }
}
