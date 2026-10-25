package com.technovision.advancedgenetics.gametest;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.entity.Visibility;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import static com.technovision.advancedgenetics.gametest.GameTestSupport.check;
import static com.technovision.advancedgenetics.gametest.GameTestSupport.getField;

/**
 * Waits and ticking that do not depend on how far the server has promoted a test's chunks.
 *
 * <p>The GameTest server places a batch's structures and starts the tests on the next tick. A busy server may not yet
 * have run the chunk promotions it queued, so a test can start in a chunk the level still treats as not loaded:
 * <ul>
 *   <li>Its entities are hidden. Level queries skip them: {@code getEntities}, {@code assertEntityPresent},
 *   {@code killAllEntities} and an explosion's choice of victims. Fabric's {@code ENTITY_LOAD} has not fired.</li>
 *   <li>Its block entities are not ticked.</li>
 * </ul>
 * The same approach as the GameTestTicking helpers in Gridwright, Mekanism and AgriCraft (#153).
 */
public final class GameTestTicking {

    private static final Map<GameTestHelper, Boolean> DRIVEN_AREAS = new WeakHashMap<>();

    private GameTestTicking() { }

    /**
     * A sequence that waits until the level shows the entities in every chunk of the test area, so that entities the
     * test spawns or causes are found by level queries and Fabric's {@code ENTITY_LOAD} fires for them. Start the test's
     * work in the sequence. The test's timeout bounds the wait.
     */
    public static GameTestSequence awaitEntitiesVisible(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BoundingBox area = bounds(helper);
        return helper.startSequence().thenWaitUntil(() -> {
            List<ChunkPos> hidden = hiddenChunks(level, area);
            check(hidden.isEmpty(), "The level still hides the entities in chunks " + hidden);
        });
    }

    /**
     * From now until the test times out, ticks every block entity inside the test's bounds once on each game tick its
     * chunk did not tick it. A machine then advances one step per game tick, as it would in a ticking chunk. Calling it
     * again for the same test does nothing.
     */
    public static void keepTestAreaTicking(GameTestHelper helper) {
        if (DRIVEN_AREAS.put(helper, Boolean.TRUE) != null) return;
        ServerLevel level = helper.getLevel();
        BoundingBox area = bounds(helper);
        // onEachTick runs from GameTestTicker, after the levels have ticked their block entities for this game tick.
        helper.onEachTick(() -> tickSkipped(level, area));
    }

    /**
     * Chunks of {@code area} whose entities level queries skip. A section's entities are visible once
     * {@code ChunkMap} has told the entity manager that the chunk is at least border-loaded; that runs as a queued main
     * thread task, which a server running behind may leave for later ticks.
     */
    @SuppressWarnings("unchecked")
    private static List<ChunkPos> hiddenChunks(ServerLevel level, BoundingBox area) {
        Object entityManager = getField(ServerLevel.class, "entityManager", level);
        Long2ObjectMap<Visibility> visibility =
                (Long2ObjectMap<Visibility>) getField(PersistentEntitySectionManager.class, "chunkVisibility", entityManager);
        List<ChunkPos> hidden = new ArrayList<>();
        for (int chunkX = SectionPos.blockToSectionCoord(area.minX()); chunkX <= SectionPos.blockToSectionCoord(area.maxX()); chunkX++) {
            for (int chunkZ = SectionPos.blockToSectionCoord(area.minZ()); chunkZ <= SectionPos.blockToSectionCoord(area.maxZ()); chunkZ++) {
                // The map's default value is HIDDEN.
                if (!visibility.get(ChunkPos.asLong(chunkX, chunkZ)).isAccessible()) hidden.add(new ChunkPos(chunkX, chunkZ));
            }
        }
        return hidden;
    }

    /** Runs the block entity's ticker once, as {@code LevelChunk}'s ticking wrapper would. */
    @SuppressWarnings("unchecked")
    private static void tickOnce(BlockEntity blockEntity) {
        Level level = blockEntity.getLevel();
        if (blockEntity.isRemoved() || level == null) return;
        BlockPos pos = blockEntity.getBlockPos();
        if (level.getBlockEntity(pos) != blockEntity) return;
        BlockState state = level.getBlockState(pos);
        if (!blockEntity.getType().isValid(state)) return;
        BlockEntityTicker<BlockEntity> ticker = state.getTicker(level, (BlockEntityType<BlockEntity>) blockEntity.getType());
        if (ticker != null) ticker.tick(level, pos, state, blockEntity);
    }

    private static void tickSkipped(ServerLevel level, BoundingBox area) {
        for (int chunkX = SectionPos.blockToSectionCoord(area.minX()); chunkX <= SectionPos.blockToSectionCoord(area.maxX()); chunkX++) {
            for (int chunkZ = SectionPos.blockToSectionCoord(area.minZ()); chunkZ <= SectionPos.blockToSectionCoord(area.maxZ()); chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null || levelTicks(level, chunk)) continue;
                for (BlockEntity blockEntity : List.copyOf(chunk.getBlockEntities().values())) {
                    if (area.isInside(blockEntity.getBlockPos())) tickOnce(blockEntity);
                }
            }
        }
    }

    /** Mirrors {@code Level.tickBlockEntities} and {@code LevelChunk.isTicking} in 1.19.2. */
    private static boolean levelTicks(ServerLevel level, LevelChunk chunk) {
        long pos = chunk.getPos().toLong();
        return level.shouldTickBlocksAt(pos) && chunk.getFullStatus().isOrAfter(ChunkHolder.FullChunkStatus.TICKING)
                && level.areEntitiesLoaded(pos);
    }

    /**
     * The test structure's blocks. {@code GameTestHelper.getBounds} looks the structure block up in the level, and a test
     * may have built over it (it sits at relative 0,0,0). {@code GameTestInfo} keeps the block entity it spawned, which
     * still knows its position, size and rotation.
     */
    private static BoundingBox bounds(GameTestHelper helper) {
        GameTestInfo info = (GameTestInfo) getField(GameTestHelper.class, "testInfo", helper);
        StructureBlockEntity structure = (StructureBlockEntity) getField(GameTestInfo.class, "structureBlockEntity", info);
        if (structure == null) throw new IllegalStateException("The GameTest has no structure yet");
        return StructureUtils.getStructureBoundingBox(structure);
    }
}
