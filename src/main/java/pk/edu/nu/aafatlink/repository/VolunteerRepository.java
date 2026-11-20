package pk.edu.nu.aafatlink.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import pk.edu.nu.aafatlink.domain.auth.AuthSession;
import pk.edu.nu.aafatlink.domain.assignment.AssignmentFilter;
import pk.edu.nu.aafatlink.domain.assignment.VolunteerCandidate;
import pk.edu.nu.aafatlink.domain.request.AssistanceRequest;
import pk.edu.nu.aafatlink.domain.volunteer.ServiceArea;
import pk.edu.nu.aafatlink.domain.volunteer.Skill;
import pk.edu.nu.aafatlink.domain.volunteer.VolunteerProfile;

/** Protected Variation boundary for volunteer readiness and eligibility data. */
public interface VolunteerRepository {
    List<Skill> findActiveSkills(AuthSession session);

    List<ServiceArea> findActiveServiceAreas(AuthSession session);

    Optional<VolunteerProfile> findProfile(AuthSession session, UUID volunteerId);

    VolunteerProfile saveReadinessAndContact(AuthSession session, VolunteerProfile profile, long expectedVersion);

    List<VolunteerCandidate> findEligible(AuthSession session, AssistanceRequest request, AssignmentFilter filter);
}
