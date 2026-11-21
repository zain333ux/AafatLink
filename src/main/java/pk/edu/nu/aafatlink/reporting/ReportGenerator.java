package pk.edu.nu.aafatlink.reporting;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import pk.edu.nu.aafatlink.domain.auth.AuthSession;
import pk.edu.nu.aafatlink.repository.ReportRepository;

/** Polymorphic report strategy from the reviewed GRASP design. */
public abstract class ReportGenerator {
    private final ReportType type;
    private final List<String> columns;
    private final Clock clock;

    protected ReportGenerator(ReportType type, List<String> columns, Clock clock) {
        this.type = Objects.requireNonNull(type, "type");
        this.columns = List.copyOf(columns);
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public final ReportType type() {
        return type;
    }

    public final GeneratedReport generateReport(AuthSession session, ReportFilters filters,
            ReportRepository repository) {
        List<ReportRow> rows = List.copyOf(fetch(session, filters, repository));
        Map<String, String> summary = rows.isEmpty() ? Map.of() : summarize(rows);
        return new GeneratedReport(type, columns, rows, summary, clock.instant());
    }

    protected abstract List<ReportRow> fetch(AuthSession session, ReportFilters filters,
            ReportRepository repository);

    protected Map<String, String> summarize(List<ReportRow> rows) {
        return Map.of("Rows", Integer.toString(rows.size()));
    }

    protected static long sum(List<ReportRow> rows, String column) {
        return rows.stream().mapToLong(row -> Long.parseLong(row.value(column))).sum();
    }
}
