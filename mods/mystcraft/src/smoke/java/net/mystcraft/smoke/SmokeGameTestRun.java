package net.mystcraft.smoke;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs the {@code mystcraft_smoke} console command inside the headless GameTest server, so CI can run the suite
 * without a dedicated server. The GameTest server, like the port GameTest runs, does not read {@code eula.txt}.
 * The command and its checks are unchanged: this issues it at operator level 4, as the console does, and turns its
 * {@code MYSTCRAFT_SMOKE_COMPLETE} and {@code MYSTCRAFT_SMOKE_FAILURE} output into the GameTest result.
 */
public final class SmokeGameTestRun implements CommandSource {
    /** The whole suite, including fixture chunk preparation, must finish within 10 minutes of server ticks. */
    public static final int TIMEOUT_TICKS = 12000;
    private static final Logger LOG = LoggerFactory.getLogger("mystcraft-smoke");
    private final List<String> failures = new ArrayList<>();
    private String complete;

    private SmokeGameTestRun() {}

    public static void start(GameTestHelper helper) {
        SmokeGameTestRun run = new SmokeGameTestRun();
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        CommandSourceStack source = new CommandSourceStack(run, Vec3.atLowerCornerOf(level.getSharedSpawnPos()), Vec2.ZERO,
                level, 4, "MystcraftSmoke", Component.literal("MystcraftSmoke"), server, null);
        if (server.getCommands().performPrefixedCommand(source, "mystcraft_smoke") != 1) {
            throw new GameTestAssertException("mystcraft_smoke did not start: " + run.failures);
        }
        helper.onEachTick(run::requireNoFailure);
        helper.succeedWhen(run::requireComplete);
    }

    private void requireNoFailure() {
        if (!failures.isEmpty()) throw new GameTestAssertException(String.join("; ", failures));
    }

    private void requireComplete() {
        if (complete == null) throw new GameTestAssertException("mystcraft_smoke is still running");
        LOG.info("MYSTCRAFT_SMOKE_GAMETEST {}", complete);
    }

    @Override public void sendSystemMessage(Component message) {
        String text = message.getString();
        LOG.info(text);
        if (text.startsWith("MYSTCRAFT_SMOKE_COMPLETE")) complete = text;
        else if (!text.startsWith("MYSTCRAFT_SMOKE_PASS") && !text.startsWith("MYSTCRAFT_SMOKE_PREPARING")) failures.add(text);
    }

    @Override public boolean acceptsSuccess() { return true; }
    @Override public boolean acceptsFailure() { return true; }
    @Override public boolean shouldInformAdmins() { return false; }
}
