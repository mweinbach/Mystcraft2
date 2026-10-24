package ports.tekxitprobe;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

/**
 * Test-only boot probe for a disposable Tekxit 4 copy with the Mystcraft2 ports installed.
 * On every server start (dedicated GameTest server or the client's integrated server) it records what
 * the ports registered and loaded, and whether Item Alchemy sees the merged EMC values. Results go to
 * {@code tekxit-probe/} in the game directory and to log lines prefixed {@code [TEKXIT_PROBE]}.
 */
public final class TekxitProbe implements ModInitializer {
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static volatile JsonObject lastServerReport;

    public static Path outDir() {
        return FabricLoader.getInstance().getGameDir().resolve("tekxit-probe");
    }

    public static JsonObject lastServerReport() {
        return lastServerReport;
    }

    public static void write(String name, String text) {
        try {
            Files.createDirectories(outDir());
            Files.writeString(outDir().resolve(name), text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("[TEKXIT_PROBE] could not write {}", name, e);
        }
    }

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            JsonObject report = ServerChecks.run(server);
            lastServerReport = report;
            String side = server.isDedicatedServer() ? "dedicated" : server.getClass().getSimpleName();
            write("server-report-" + side + ".json", GSON.toJson(report));
            int failures = report.getAsJsonArray("failures").size();
            LOGGER.info("[TEKXIT_PROBE] server checks on {}: {} failure(s), {} warning(s)", side, failures,
                    report.getAsJsonArray("warnings").size());
            report.getAsJsonArray("failures").forEach(f -> LOGGER.error("[TEKXIT_PROBE] FAIL {}", f.getAsString()));
            report.getAsJsonArray("warnings").forEach(w -> LOGGER.warn("[TEKXIT_PROBE] WARN {}", w.getAsString()));
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server ->
                write("server-stopped.txt", Instant.now() + " " + server.getClass().getSimpleName() + " stopped\n"));
        ServerTestDriver.install();
    }
}
