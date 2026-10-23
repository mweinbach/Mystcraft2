package ports.buildcraft.gametest;

import static ports.buildcraft.gametest.BCTests.require;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;

/**
 * Stands in for a protection mod in the shared GameTests. {@code ForgeGameTests} registers listeners on Forge's
 * {@code BlockEvent.BreakEvent} and {@code BlockEvent.EntityPlaceEvent}, and {@code FabricGameTests} on Fabric's
 * {@code PlayerBlockBreakEvents.BEFORE}; each cancels the event at a position a test has refused here and records
 * which player asked, so the tests check both that the loader's events are fired and whom for.
 */
public final class TestProtection {
    private static final Set<BlockPos> REFUSED = ConcurrentHashMap.newKeySet();
    private static final Map<BlockPos, String> ASKED = new ConcurrentHashMap<>();
    @Nullable
    private static volatile String loader;

    private TestProtection() {}

    /** Called once each loader's listeners are registered. */
    public static void installed(String loaderName) {
        loader = loaderName;
    }

    /** Called by the loader listeners: whether to cancel the event at {@code pos}, noting who asked. */
    public static boolean refuses(BlockPos pos, @Nullable Player player) {
        if (!REFUSED.contains(pos)) return false;
        ASKED.put(pos.immutable(), player == null ? "" : player.getGameProfile().getName());
        return true;
    }

    static void refuse(GameTestHelper helper, BlockPos pos) {
        require(helper, loader != null, "the loader's test protection listeners must be registered");
        BlockPos absolute = helper.absolutePos(pos);
        ASKED.remove(absolute);
        REFUSED.add(absolute);
    }

    static void allow(GameTestHelper helper, BlockPos pos) {
        REFUSED.remove(helper.absolutePos(pos));
    }

    /** The name of the player last refused at {@code pos}, or null if none was. */
    @Nullable
    static String refusedPlayer(GameTestHelper helper, BlockPos pos) {
        return ASKED.get(helper.absolutePos(pos));
    }
}
