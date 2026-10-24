package ports.gridwright.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import ports.gridwright.grid.GridManager;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test stand-ins for a claim mod and a GP source. Positions marked protected are refused by listeners
 * each loader's test setup installs on its real break and right-click events (Forge
 * {@code BlockEvent.BreakEvent} and {@code RightClickBlock}; Fabric {@code PlayerBlockBreakEvents.BEFORE}
 * and {@code UseBlockCallback}), exactly as a claim mod would refuse a player.
 */
public final class TestProtection {

    private static final Set<BlockPos> PROTECTED = ConcurrentHashMap.newKeySet();
    private static final Set<BlockPos> USE_DENIED = ConcurrentHashMap.newKeySet();

    private TestProtection() { }

    /** Protects the block at {@code relative} in {@code helper}'s arena. */
    public static BlockPos protect(GameTestHelper helper, BlockPos relative) {
        BlockPos absolute = helper.absolutePos(relative).immutable();
        PROTECTED.add(absolute);
        return absolute;
    }

    public static boolean isProtected(BlockPos absolute) {
        return PROTECTED.contains(absolute);
    }

    /** Refuses right-clicks (only) on the block at {@code relative}; breaking it stays allowed. */
    public static BlockPos denyUse(GameTestHelper helper, BlockPos relative) {
        BlockPos absolute = helper.absolutePos(relative).immutable();
        USE_DENIED.add(absolute);
        return absolute;
    }

    /** Whether right-clicks on {@code absolute} are refused: protected or use-denied. */
    public static boolean isUseDenied(BlockPos absolute) {
        return PROTECTED.contains(absolute) || USE_DENIED.contains(absolute);
    }

    /** Adds {@code gp} of production to {@code owner}'s grid on every tick of the test, standing in for producers. */
    static void power(GameTestHelper helper, GameTestSupport.Owner owner, double gp) {
        GridManager manager = GameTestSupport.grid(helper);
        helper.onEachTick(() -> manager.addProduction(owner.gridKey(), "gridwright:test_source", gp));
    }
}
