package ports.tekxitprobe;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraft.gametest.framework.GlobalTestReporter;
import net.minecraft.gametest.framework.TestReporter;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.Vec3;

/**
 * Drives GameTests on a real dedicated server, where no player sees the {@code /test} summary.
 * With {@code -Dtekxitprobe.runTests=ClassA,ClassB} it runs {@code /test runall} for each class after
 * startup, logs every result as {@code [TEKXIT_PROBE] TEST PASS|FAIL}, writes
 * {@code tekxit-probe/gametests.json} and, with {@code -Dtekxitprobe.stopAfterTests=true}, stops the
 * server exactly as the {@code stop} command does.
 */
final class ServerTestDriver implements TestReporter {
    private static final int START_DELAY_TICKS = 100;
    private static final int TIMEOUT_TICKS = 20 * 60 * 10;

    private final List<String> classes;
    private final JsonArray results = new JsonArray();
    private int expected;
    private int passed;
    private int failed;
    private int ticks;
    private boolean started;
    private boolean finished;

    private ServerTestDriver(List<String> classes) {
        this.classes = classes;
    }

    static void install() {
        String property = System.getProperty("tekxitprobe.runTests");
        if (property == null || property.isBlank()) return;
        ServerTestDriver driver = new ServerTestDriver(Arrays.stream(property.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList());
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (server.isDedicatedServer()) ServerTickEvents.END_SERVER_TICK.register(driver::tick);
        });
    }

    private void tick(MinecraftServer server) {
        if (finished) return;
        ticks++;
        if (!started && ticks >= START_DELAY_TICKS) {
            started = true;
            GlobalTestReporter.replaceWith(this);
            BlockPos spawn = server.overworld().getSharedSpawnPos();
            int offset = 0;
            for (String test : classes) {
                expected += (int) GameTestRegistry.getTestFunctionsForClassName(test.toLowerCase(java.util.Locale.ROOT)).size();
                var source = server.createCommandSourceStack().withPermission(4)
                        .withPosition(Vec3.atBottomCenterOf(spawn.offset(24 + offset, 0, 24)));
                TekxitProbe.LOGGER.info("[TEKXIT_PROBE] running /test runall {}", test);
                server.getCommands().performPrefixedCommand(source, "test runall " + test);
                offset += 48;
            }
            TekxitProbe.LOGGER.info("[TEKXIT_PROBE] {} GameTest(s) scheduled from {}", expected, classes);
            if (expected == 0) finish(server, "no tests found");
        } else if (started && passed + failed >= expected) {
            finish(server, "all tests reported");
        } else if (ticks > TIMEOUT_TICKS) {
            finish(server, "timed out after " + (passed + failed) + "/" + expected + " results");
        }
    }

    private void finish(MinecraftServer server, String reason) {
        finished = true;
        JsonObject out = new JsonObject();
        out.add("classes", ServerChecks.strings(new ArrayList<>(classes)));
        out.addProperty("expected", expected);
        out.addProperty("passed", passed);
        out.addProperty("failed", failed);
        out.addProperty("end", reason);
        out.add("results", results);
        TekxitProbe.write("gametests.json", TekxitProbe.GSON.toJson(out));
        TekxitProbe.LOGGER.info("[TEKXIT_PROBE] GameTests {}: {} passed, {} failed of {}", reason, passed, failed, expected);
        if (Boolean.getBoolean("tekxitprobe.stopAfterTests")) {
            TekxitProbe.LOGGER.info("[TEKXIT_PROBE] stopping server");
            server.halt(false);
        }
    }

    private void record(GameTestInfo info, boolean pass) {
        JsonObject entry = new JsonObject();
        entry.addProperty("test", info.getTestName());
        entry.addProperty("required", info.isRequired());
        entry.addProperty("passed", pass);
        if (!pass && info.getError() != null) entry.addProperty("error", String.valueOf(info.getError().getMessage()));
        results.add(entry);
        if (pass) passed++;
        else failed++;
        if (pass) TekxitProbe.LOGGER.info("[TEKXIT_PROBE] TEST PASS {}", info.getTestName());
        else TekxitProbe.LOGGER.error("[TEKXIT_PROBE] TEST FAIL {}: {}", info.getTestName(), info.getError());
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
