package pk.edu.nu.aafatlink.application.dashboard;

import java.util.UUID;

/** Count and first visible record in an operational queue. */
public record DashboardEntry(String queue, long total, UUID recordId, String caption) {}
