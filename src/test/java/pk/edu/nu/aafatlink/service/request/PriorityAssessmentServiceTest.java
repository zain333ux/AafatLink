package pk.edu.nu.aafatlink.service.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import pk.edu.nu.aafatlink.domain.request.AssistanceRequest;
import pk.edu.nu.aafatlink.domain.request.AssistanceRequestDraft;
import pk.edu.nu.aafatlink.domain.request.DisasterType;
import pk.edu.nu.aafatlink.domain.request.RequestPriority;
import pk.edu.nu.aafatlink.domain.request.RequestStatus;

class PriorityAssessmentServiceTest {
    private final PriorityAssessmentService service = new PriorityAssessmentService();

    @Test
    void suggestsCriticalForImmediateThreatToLife() {
        AssistanceRequest request = request("Two people are trapped with severe bleeding",
                "Immediate medical evacuation", "2 adults", Instant.parse("2026-11-20T00:00:00Z"));

        assertEquals(RequestPriority.CRITICAL,
                service.suggestPriority(request, Instant.parse("2026-11-20T01:00:00Z")));
    }

    @Test
    void timeWaitingEscalatesOneLevelAfterTwentyFourHours() {
        AssistanceRequest request = request("Roof damaged after rainfall", "Food and drinking water",
                "One adult", Instant.parse("2026-11-18T00:00:00Z"));

        assertEquals(RequestPriority.HIGH,
                service.suggestPriority(request, Instant.parse("2026-11-20T01:00:00Z")));
    }

    @Test
    void overrideRequiresWrittenJustification() {
        assertThrows(IllegalArgumentException.class,
                () -> service.validateSelection(RequestPriority.MEDIUM, RequestPriority.HIGH, " "));
        assertEquals("Road access worsened after the initial report.",
                service.validateSelection(RequestPriority.MEDIUM, RequestPriority.HIGH,
                        " Road access worsened after the initial report. "));
        assertEquals(null, service.validateSelection(RequestPriority.HIGH, RequestPriority.HIGH, null));
    }

    private static AssistanceRequest request(String description, String assistance, String people, Instant submittedAt) {
        return new AssistanceRequest(UUID.randomUUID(), new AssistanceRequestDraft(DisasterType.FLOOD, null,
                "Sector G-10, Islamabad", description, assistance, people), RequestStatus.VERIFIED,
                null, false, submittedAt, null, 0, null, null, null);
    }
}
