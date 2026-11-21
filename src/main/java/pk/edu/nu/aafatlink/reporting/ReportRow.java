package pk.edu.nu.aafatlink.reporting;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** One display-safe report row. Values deliberately exclude personal contact data. */
public final class ReportRow {
    private final Map<String, String> values;

    public ReportRow(Map<String, String> values) {
        Objects.requireNonNull(values, "values");
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public String value(String column) {
        return values.getOrDefault(column, "");
    }

    public Map<String, String> values() {
        return values;
    }
}
