package pk.edu.nu.aafatlink.domain.request;

/** Snapshot of an unregistered caller recorded by a Coordinator for UC01. */
public record RequesterContact(String name, String contactNumber) {
    public RequesterContact {
        name = require(name, "Requester name", 2, 120);
        contactNumber = require(contactNumber, "Requester contact number", 7, 25);
        if (!contactNumber.matches("^[+0-9][0-9 +()\\-]{6,24}$")) {
            throw new IllegalArgumentException("Enter a valid requester contact number.");
        }
    }

    private static String require(String value, String label, int minimum, int maximum) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() < minimum || normalized.length() > maximum) {
            throw new IllegalArgumentException(label + " must contain " + minimum + " to " + maximum + " characters.");
        }
        return normalized;
    }
}
