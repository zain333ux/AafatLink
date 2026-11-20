package pk.edu.nu.aafatlink.domain.volunteer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class VolunteerProfileTest {
    private static final Skill FIRST_AID = new Skill(1, "First Aid");
    private static final Skill LOGISTICS = new Skill(2, "Logistics");
    private static final ServiceArea ISLAMABAD = new ServiceArea(10, "Islamabad");

    @Test
    void approvedAvailableProfileIsEligible() {
        VolunteerProfile profile = profile(VolunteerApprovalStatus.APPROVED, AvailabilityStatus.AVAILABLE);

        assertTrue(profile.isApprovedAndAvailable());
        assertTrue(profile.matchesRequirements(1, 10));
        assertFalse(profile.matchesRequirements(2, 10));
    }

    @Test
    void readinessChangesRequireContactSkillAndServiceArea() {
        assertThrows(IllegalArgumentException.class, () -> new VolunteerReadinessChanges(
                "", AvailabilityStatus.AVAILABLE, null, null, null, Set.of(1L), Set.of(10L)));
        assertThrows(IllegalArgumentException.class, () -> new VolunteerReadinessChanges(
                "+92 300 1234567", AvailabilityStatus.AVAILABLE, null, null, null, Set.of(), Set.of(10L)));
        assertThrows(IllegalArgumentException.class, () -> new VolunteerReadinessChanges(
                "+92 300 1234567", AvailabilityStatus.AVAILABLE, null, null, null, Set.of(1L), Set.of()));
    }

    @Test
    void readinessWindowMustBeCompleteAndOrdered() {
        Instant start = Instant.parse("2026-11-21T09:00:00Z");
        assertThrows(IllegalArgumentException.class, () -> new VolunteerReadinessChanges(
                "+92 300 1234567", AvailabilityStatus.AVAILABLE, null, start, null, Set.of(1L), Set.of(10L)));
        assertThrows(IllegalArgumentException.class, () -> new VolunteerReadinessChanges(
                "+92 300 1234567", AvailabilityStatus.AVAILABLE, null, start, start, Set.of(1L), Set.of(10L)));
    }

    @Test
    void profileAppliesOnlyCataloguedReadinessSelections() {
        VolunteerProfile profile = profile(VolunteerApprovalStatus.PENDING, AvailabilityStatus.UNAVAILABLE);
        VolunteerReadinessChanges changes = new VolunteerReadinessChanges(
                "+92 300 7654321", AvailabilityStatus.AVAILABLE, "Available for field response.", null, null,
                Set.of(2L), Set.of(10L));

        VolunteerProfile updated = profile.withReadinessChanges(changes,
                List.of(FIRST_AID, LOGISTICS), List.of(ISLAMABAD));

        assertEquals(AvailabilityStatus.AVAILABLE, updated.availability());
        assertEquals("+92 300 7654321", updated.contactNumber());
        assertEquals(List.of(LOGISTICS), updated.skills());
        assertEquals(List.of(ISLAMABAD), updated.serviceAreas());
        assertEquals(VolunteerApprovalStatus.PENDING, updated.approvalStatus());
        assertEquals(profile.version(), updated.version());
    }

    private static VolunteerProfile profile(VolunteerApprovalStatus approval, AvailabilityStatus availability) {
        return new VolunteerProfile(UUID.randomUUID(), approval, availability, "+92 300 1234567", null,
                null, null, List.of(FIRST_AID), List.of(ISLAMABAD), 3);
    }
}
