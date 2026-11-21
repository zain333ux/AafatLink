package pk.edu.nu.aafatlink.reporting;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import pk.edu.nu.aafatlink.domain.auth.AuthSession;
import pk.edu.nu.aafatlink.repository.ReportRepository;
import pk.edu.nu.aafatlink.repository.RepositoryException;

/** Validates UC11 filters, selects a strategy and owns CSV export. */
public final class ReportService {
    private final ReportRepository repository;
    private final Map<ReportType, ReportGenerator> generators;

    public ReportService(ReportRepository repository, List<ReportGenerator> generators) {
        this.repository = Objects.requireNonNull(repository, "repository");
        LinkedHashMap<ReportType, ReportGenerator> mapped = new LinkedHashMap<>();
        for (ReportGenerator generator : generators) {
            if (mapped.put(generator.type(), generator) != null) {
                throw new IllegalArgumentException("Duplicate report generator: " + generator.type());
            }
        }
        if (mapped.size() != ReportType.values().length) {
            throw new IllegalArgumentException("Every report type requires one generator.");
        }
        this.generators = Map.copyOf(mapped);
    }

    public static ReportService standard(ReportRepository repository) {
        Clock clock = Clock.systemUTC();
        return new ReportService(repository, List.of(new RequestStatusReport(clock),
                new OutstandingRequestsReport(clock), new VolunteerWorkloadReport(clock),
                new ResponsePerformanceReport(clock)));
    }

    public GeneratedReport generateReport(AuthSession session, ReportType type, ReportFilters filters) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(type, "type");
        validateFilters(Objects.requireNonNull(filters, "filters"));
        return generators.get(type).generateReport(session, filters, repository);
    }

    public void validateFilters(ReportFilters filters) {
        if ((filters.startDate() == null) != (filters.endDate() == null)) {
            throw new IllegalArgumentException("Choose both a start date and an end date.");
        }
        if (filters.startDate() != null && filters.startDate().isAfter(filters.endDate())) {
            throw new IllegalArgumentException("Start date must not be after end date.");
        }
    }

    public void exportCsv(GeneratedReport report, Path destination) {
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(destination, "destination");
        StringBuilder csv = new StringBuilder();
        csv.append(report.columns().stream().map(ReportService::escape).collect(Collectors.joining(",")))
                .append("\r\n");
        for (ReportRow row : report.rows()) {
            csv.append(report.columns().stream().map(column -> escape(row.value(column)))
                    .collect(Collectors.joining(","))).append("\r\n");
        }
        try {
            Files.writeString(destination, csv, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (IOException exception) {
            throw new RepositoryException("The CSV file could not be written. The report remains available.", exception);
        }
    }

    private static String escape(String value) {
        String safe = Objects.requireNonNullElse(value, "");
        if (safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")) {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }
}
