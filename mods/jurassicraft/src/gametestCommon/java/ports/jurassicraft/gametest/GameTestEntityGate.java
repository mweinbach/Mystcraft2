package ports.jurassicraft.gametest;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Starts each GameTest only once its structure's chunks have loaded entities and tick them.
 * Vanilla starts a test one tick after placing it, while newly forced chunks load their entity
 * sections asynchronously; until then entity queries silently miss spawned entities.
 */
public final class GameTestEntityGate {
    private static boolean installed;

    private GameTestEntityGate() {}

    /**
     * Call once tests are registered and before the first server tick runs them. The test server
     * already holds these {@link TestFunction} instances in its batches, so each body is replaced
     * in place rather than the registry entry.
     */
    public static synchronized void install() {
        if (installed) return;
        installed = true;
        Field body = Arrays.stream(TestFunction.class.getDeclaredFields())
                .filter(field -> field.getType() == Consumer.class)
                .findFirst().orElseThrow(() -> new IllegalStateException("TestFunction has no test body field"));
        body.setAccessible(true);
        try {
            for (TestFunction test : GameTestRegistry.getAllTestFunctions()) {
                @SuppressWarnings("unchecked")
                Consumer<GameTestHelper> original = (Consumer<GameTestHelper>) body.get(test);
                body.set(test, gate(original));
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("cannot gate GameTests", e);
        }
    }

    private static Consumer<GameTestHelper> gate(Consumer<GameTestHelper> test) {
        return helper -> helper.startSequence()
                .thenWaitUntil(() -> requireEntitiesLoaded(helper))
                .thenExecute(() -> run(helper, test));
    }

    /**
     * Sequence steps swallow assertion failures and retry, so a failure is rethrown from a
     * scheduled task, which reports it like an ungated test. The body itself must not run in
     * that task loop: its own scheduled tasks would modify the map being iterated.
     */
    private static void run(GameTestHelper helper, Consumer<GameTestHelper> test) {
        try {
            test.accept(helper);
        } catch (RuntimeException | Error failure) {
            helper.runAfterDelay(0, () -> { throw failure; });
        }
    }

    private static void requireEntitiesLoaded(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        BoundingBox box = helper.getLevel().getBlockEntity(origin) instanceof StructureBlockEntity structure
                ? StructureUtils.getStructureBoundingBox(structure) : new BoundingBox(origin);
        var level = helper.getLevel();
        for (int x = box.minX() >> 4; x <= box.maxX() >> 4; x++) for (int z = box.minZ() >> 4; z <= box.maxZ() >> 4; z++) {
            // Loaded sections stay hidden from entity queries until the chunk is entity-ticking.
            if (!level.areEntitiesLoaded(ChunkPos.asLong(x, z)) || !level.isPositionEntityTicking(new BlockPos(x << 4, origin.getY(), z << 4)))
                throw new GameTestAssertException("waiting for test entities to load");
        }
    }
}
