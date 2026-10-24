package ports.tekxitprobe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The client verdict must not pass when the server checks or GameTests did not. */
class VerdictTest {
    private static final List<String> CLASSES = List.of("TekxitProbeGameTests", "TekxitCompatibilityGameTests");

    private static JsonObject report(String... failures) {
        JsonObject report = new JsonObject();
        JsonArray list = new JsonArray();
        for (String failure : failures) list.add(failure);
        report.add("failures", list);
        return report;
    }

    @Test
    void serverReportFailuresAreCarriedOver() {
        assertEquals(List.of("integrated server: recipe parse error"),
                Verdict.serverReportFailures("integrated", report("recipe parse error")));
        assertEquals(List.of(), Verdict.serverReportFailures("integrated", report()));
    }

    @Test
    void missingOrMalformedServerReportFails() {
        assertEquals(1, Verdict.serverReportFailures("integrated", null).size());
        assertEquals(1, Verdict.serverReportFailures("integrated", new JsonObject()).size());
    }

    @Test
    void completeGreenRunPasses() {
        assertEquals(List.of(), Verdict.gameTestFailures(new GameTestTally.Snapshot(CLASSES, 7, 7, List.of(), false)));
    }

    @Test
    void failedTestsFail() {
        List<String> failures = Verdict.gameTestFailures(new GameTestTally.Snapshot(CLASSES, 7, 6, List.of("portsbooted: boom"), false));
        assertEquals(List.of("GameTest failed: portsbooted: boom"), failures);
    }

    @Test
    void unfinishedOrTimedOutRunFails() {
        List<String> failures = Verdict.gameTestFailures(new GameTestTally.Snapshot(CLASSES, 7, 3, List.of(), true));
        assertEquals(1, failures.size());
        assertTrue(failures.get(0).contains("3/7") && failures.get(0).contains("timeout"), failures.get(0));
    }

    @Test
    void runWithNoTestsFails() {
        assertEquals(1, Verdict.gameTestFailures(new GameTestTally.Snapshot(CLASSES, 0, 0, List.of(), false)).size());
    }
}
