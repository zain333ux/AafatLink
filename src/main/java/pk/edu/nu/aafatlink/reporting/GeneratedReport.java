package pk.edu.nu.aafatlink.reporting;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Complete on-screen UC11 result. Empty reports carry no fabricated summary totals. */
public record GeneratedReport(ReportType type, List<String> columns, List<ReportRow> rows,
        Map<String, String> summary, Instant generatedAt) {
    public GeneratedReport {
        Objects.requireNonNull(type, "type");
        columns = List.copyOf(columns);
        rows = List.copyOf(rows);
        summary = Collections.unmodifiableMap(new LinkedHashMap<>(summary));
        Objects.requireNonNull(generatedAt, "generatedAt");
    }
}
