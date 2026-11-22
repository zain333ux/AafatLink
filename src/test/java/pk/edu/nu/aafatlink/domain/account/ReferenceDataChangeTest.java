package pk.edu.nu.aafatlink.domain.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ReferenceDataChangeTest {
    @Test
    void normalizesAnAuditableUpdate() {
        ReferenceDataChange change = new ReferenceDataChange(ReferenceDataType.SERVICE_AREA,
                "12", "  Islamabad Urban  ", true, 3, "  Boundary corrected  ");

        assertEquals("Islamabad Urban", change.name());
        assertEquals("Boundary corrected", change.reason());
    }

    @Test
    void preventsUnreviewedDisasterTypeCodes() {
        assertThrows(IllegalArgumentException.class, () -> new ReferenceDataChange(
                ReferenceDataType.DISASTER_TYPE, null, "Cyclone", true, 0, "New category"));
    }
}
