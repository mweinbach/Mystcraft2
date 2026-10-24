package ports.tekxitprobe;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.TestReporter;

/**
 * Collects GameTest outcomes from {@link net.minecraft.gametest.framework.GlobalTestReporter}. Results arrive on the
 * server thread and are read from the client thread, so all state is guarded by this object's monitor.
 */
public final class GameTestTally implements TestReporter {
    public record Snapshot(List<String> classes, int expected, int passed, List<String> failedTests, boolean timedOut) {}

    private final List<String> classes;
    private final JsonArray results = new JsonArray();
    private final List<String> failedTests = new ArrayList<>();
    private int expected;
    private int passed;
    private boolean scheduled;
    private boolean timedOut;

    public GameTestTally(List<String> classes) {
        this.classes = List.copyOf(classes);
    }

    public synchronized void scheduled(int expectedTests) {
        expected = expectedTests;
        scheduled = true;
    }

    /** True once every scheduled test has reported (or none were found). */
    public synchronized boolean complete() {
        return scheduled && passed + failedTests.size() >= expected;
    }

    public synchronized void markTimedOut() {
        timedOut = true;
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(classes, expected, passed, List.copyOf(failedTests), timedOut);
    }

    public synchronized JsonObject toJson() {
        JsonObject out = new JsonObject();
        out.add("classes", Verdict.strings(classes));
        out.addProperty("expected", expected);
        out.addProperty("passed", passed);
        out.addProperty("failed", failedTests.size());
        out.addProperty("timedOut", timedOut);
        out.add("results", results.deepCopy());
        return out;
    }

    private synchronized void record(GameTestInfo info, boolean pass) {
        JsonObject entry = new JsonObject();
        entry.addProperty("test", info.getTestName());
        entry.addProperty("passed", pass);
        String error = info.getError() == null ? null : String.valueOf(info.getError().getMessage());
        if (error != null) entry.addProperty("error", error);
        results.add(entry);
        if (pass) passed++;
        else failedTests.add(error == null ? info.getTestName() : info.getTestName() + ": " + error);
        if (pass) TekxitProbe.LOGGER.info("[TEKXIT_PROBE] TEST PASS {}", info.getTestName());
        else TekxitProbe.LOGGER.error("[TEKXIT_PROBE] TEST FAIL {}: {}", info.getTestName(), error);
    }

    @Override
    public void onTestFailed(GameTestInfo info) {
        record(info, false);
    }

    @Override
    public void onTestSuccess(GameTestInfo info) {
        record(info, true);
    }
}
