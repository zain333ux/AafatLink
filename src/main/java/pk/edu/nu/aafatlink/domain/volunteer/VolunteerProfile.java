package pk.edu.nu.aafatlink.domain.volunteer;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Information Expert for readiness, approval and requirement matching.
 * Persistence remains behind {@code VolunteerRepository}.
 */
public final class VolunteerProfile {
    private final UUID accountId;
    private final VolunteerApprovalStatus approvalStatus;
    private final AvailabilityStatus availability;
    private final String contactNumber;
    private final String availabilityNotes;
    private final Instant availableFrom;
    private final Instant availableUntil;
    private final List<Skill> skills;
    private final List<ServiceArea> serviceAreas;
    private final long version;

    public VolunteerProfile(UUID accountId, VolunteerApprovalStatus approvalStatus,
            AvailabilityStatus availability, String contactNumber, String availabilityNotes,
            Instant availableFrom, Instant availableUntil, List<Skill> skills,
            List<ServiceArea> serviceAreas, long version) {
        this.accountId = Objects.requireNonNull(accountId, "accountId");
        this.approvalStatus = Objects.requireNonNull(approvalStatus, "approvalStatus");
        this.availability = Objects.requireNonNull(availability, "availability");
        this.contactNumber = contactNumber == null ? "" : contactNumber.trim();
        this.availabilityNotes = availabilityNotes;
        this.availableFrom = availableFrom;
        this.availableUntil = availableUntil;
        this.skills = List.copyOf(skills == null ? List.of() : skills);
        this.serviceAreas = List.copyOf(serviceAreas == null ? List.of() : serviceAreas);
        if (version < 0) {
            throw new IllegalArgumentException("Profile version must not be negative.");
        }
        this.version = version;
    }

    public boolean isApprovedAndAvailable() {
        return approvalStatus == VolunteerApprovalStatus.APPROVED
                && availability == AvailabilityStatus.AVAILABLE;
    }

    public boolean matchesRequirements(long skillId, long serviceAreaId) {
        return isApprovedAndAvailable()
                && skills.stream().anyMatch(skill -> skill.id() == skillId)
                && serviceAreas.stream().anyMatch(area -> area.id() == serviceAreaId);
    }

    public VolunteerProfile withReadinessChanges(VolunteerReadinessChanges changes,
            List<Skill> skillCatalog, List<ServiceArea> serviceAreaCatalog) {
        Objects.requireNonNull(changes, "changes");
        List<Skill> selectedSkills = select(changes.skillIds(), skillCatalog, Skill::id, "skill");
        List<ServiceArea> selectedAreas = select(changes.serviceAreaIds(), serviceAreaCatalog,
                ServiceArea::id, "service area");
        return new VolunteerProfile(accountId, approvalStatus, changes.availability(), changes.contactNumber(),
                changes.availabilityNotes(), changes.availableFrom(), changes.availableUntil(),
                selectedSkills, selectedAreas, version);
    }

    private static <T> List<T> select(Set<Long> selectedIds, List<T> catalog,
            Function<T, Long> idExtractor, String label) {
        Objects.requireNonNull(catalog, "catalog");
        var byId = catalog.stream().collect(Collectors.toMap(idExtractor, Function.identity()));
        if (!byId.keySet().containsAll(selectedIds)) {
            throw new IllegalArgumentException("A selected " + label + " is no longer available.");
        }
        return selectedIds.stream().sorted().map(byId::get)
                .sorted(Comparator.comparing(item -> idExtractor.apply(item))).toList();
    }

    public UUID accountId() { return accountId; }
    public VolunteerApprovalStatus approvalStatus() { return approvalStatus; }
    public AvailabilityStatus availability() { return availability; }
    public String contactNumber() { return contactNumber; }
    public String availabilityNotes() { return availabilityNotes; }
    public Instant availableFrom() { return availableFrom; }
    public Instant availableUntil() { return availableUntil; }
    public List<Skill> skills() { return skills; }
    public List<ServiceArea> serviceAreas() { return serviceAreas; }
    public long version() { return version; }
}
