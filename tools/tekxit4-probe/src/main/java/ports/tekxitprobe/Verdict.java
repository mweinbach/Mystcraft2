package ports.tekxitprobe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns server reports and GameTest outcomes into verdict failures. The client run's PASSED/FAILED result is
 * computed only from these lists, so nothing it relies on can be missing or merely logged.
 */
public final class Verdict {
    private Verdict() {}

    /** Failures of a server-check report. A missing or malformed report is itself a failure. */
    public static List<String> serverReportFailures(String side, JsonObject report) {
        List<String> out = new ArrayList<>();
        if (report == null) {
            out.add(side + " server checks did not run");
            return out;
        }
        JsonElement failures = report.get("failures");
        if (failures == null || !failures.isJsonArray()) {
            out.add(side + " server report has no failures list");
            return out;
        }
        for (JsonElement failure : failures.getAsJsonArray()) out.add(side + " server: " + failure.getAsString());
        return out;
    }

    /**
     * Failures of a GameTest run. Every failed test counts, as does a run that found no tests or did not report a
     * result for every scheduled test before it ended (for example on a timeout).
     */
    public static List<String> gameTestFailures(GameTestTally.Snapshot run) {
        List<String> out = new ArrayList<>();
        if (run.expected() == 0) out.add("no GameTests found in " + run.classes());
        for (String failed : run.failedTests()) out.add("GameTest failed: " + failed);
        int reported = run.passed() + run.failedTests().size();
        if (run.expected() > 0 && reported < run.expected())
            out.add("GameTests did not finish: " + reported + "/" + run.expected() + " reported" + (run.timedOut() ? " before the timeout" : ""));
        return out;
    }

    public static JsonArray strings(List<String> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array;
    }
}
