package ports.buildcraft.gametest;

import static ports.buildcraft.gametest.BCTests.require;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Stands in for a protection mod in the shared GameTests. {@code ForgeGameTests} registers listeners on Forge's
 * {@code BlockEvent.BreakEvent} and {@code BlockEvent.EntityPlaceEvent}, and {@code FabricGameTests} on Fabric's
 * {@code PlayerBlockBreakEvents.BEFORE}; each cancels the event at a position a test has refused here and records
 * which player asked, so the tests check both that the loader's events are fired and whom for. Attacks are refused
 * the same way, per entity, through Forge's {@code AttackEntityEvent} and Fabric's {@code AttackEntityCallback}.
 */
public final class TestProtection {
    private static final Set<BlockPos> REFUSED = ConcurrentHashMap.newKeySet();
    private static final Map<BlockPos, String> ASKED = new ConcurrentHashMap<>();
    private static final Set<UUID> REFUSED_TARGETS = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, String> ASKED_TARGETS = new ConcurrentHashMap<>();
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

    /** Called by the loaders' attack listeners: whether to cancel an attack on {@code target}, noting who asked. */
    public static boolean refusesAttack(Entity target, @Nullable Player player) {
        if (!REFUSED_TARGETS.contains(target.getUUID())) return false;
        ASKED_TARGETS.put(target.getUUID(), player == null ? "" : player.getGameProfile().getName());
        return true;
    }

    static void refuseAttacks(GameTestHelper helper, Entity target) {
        require(helper, loader != null, "the loader's test protection listeners must be registered");
        ASKED_TARGETS.remove(target.getUUID());
        REFUSED_TARGETS.add(target.getUUID());
    }

    static void allowAttacks(Entity target) {
        REFUSED_TARGETS.remove(target.getUUID());
    }

    /** The name of the player last refused an attack on {@code target}, or null if none was. */
    @Nullable
    static String refusedAttacker(Entity target) {
        return ASKED_TARGETS.get(target.getUUID());
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
