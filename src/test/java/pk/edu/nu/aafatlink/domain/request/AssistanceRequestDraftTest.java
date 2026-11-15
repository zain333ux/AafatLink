package pk.edu.nu.aafatlink.domain.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class AssistanceRequestDraftTest {
    @Test
    void normalizesValidSubmissionFields() {
        AssistanceRequestDraft draft = new AssistanceRequestDraft(
                DisasterType.FLOOD, null, "  Sector G-10, Islamabad  ",
                "  Water entered the ground floor.  ", "Evacuation and drinking water",
                "Four adults and two children");

        assertEquals("Sector G-10, Islamabad", draft.locationAddress());
        assertEquals("Water entered the ground floor.", draft.description());
    }

    @Test
    void otherDisasterRequiresSpecification() {
        assertThrows(IllegalArgumentException.class, () -> new AssistanceRequestDraft(
                DisasterType.OTHER, " ", "Sector G-10, Islamabad", "Detailed description",
                "Medical assistance", "One adult"));
    }

    @Test
    void predefinedDisasterRejectsOtherSpecification() {
        assertThrows(IllegalArgumentException.class, () -> new AssistanceRequestDraft(
                DisasterType.FIRE, "industrial accident", "Sector G-10, Islamabad",
                "Detailed description", "Fire response", "Two adults"));
    }
}
