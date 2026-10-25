/*
 * Mekanism 1.19.2 port - in-world tests shared by the Forge and Fabric builds.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Ticking that does not depend on how far the server has promoted a test's chunk.
 *
 * <p>The level ticks a block entity only once its chunk is block-ticking with its entities loaded, and an entity only
 * once its section is entity-ticking. A busy GameTest server starves chunk promotion, so a freshly placed test area can
 * sit unticked for most of its timeout. A test that waits for a block entity's work then times out. The helpers here
 * tick block entities themselves, at most once per game tick and only on ticks the level skipped them. A test sees the
 * ticks it would see in a ticking chunk. Entities are only waited on, never ticked.
 *
 * <p>{@link SharedGameTests} calls {@link #keepTestAreaTicking} before every test except those marked
 * {@link LevelTickingOnly}, which check the level's own chunk loading and ticking.
 */
public final class GameTestTicking {

    private static final Map<GameTestHelper, Boolean> DRIVEN_AREAS = new WeakHashMap<>();
    private static Field testInfo;
    private static Field structureBlockEntity;

    private GameTestTicking() { }

    /** Runs the block entity's ticker once, as {@code LevelChunk}'s ticking wrapper would. False if it has none or is gone. */
    @SuppressWarnings("unchecked")
    public static boolean tickOnce(BlockEntity blockEntity) {
        Level level = blockEntity.getLevel();
        if (blockEntity.isRemoved() || level == null) return false;
        BlockPos pos = blockEntity.getBlockPos();
        if (level.getBlockEntity(pos) != blockEntity) return false;
        BlockState state = level.getBlockState(pos);
        if (!blockEntity.getType().isValid(state)) return false;
        BlockEntityTicker<BlockEntity> ticker = state.getTicker(level, (BlockEntityType<BlockEntity>) blockEntity.getType());
        if (ticker == null) return false;
        ticker.tick(level, pos, state, blockEntity);
        return true;
    }

    /**
     * Ticks {@code blockEntity} directly, within the current game tick, until {@code done} holds or it has been ticked
     * {@code maxTicks} times. Returns whether {@code done} held. For a test about one block entity's own work.
     */
    public static boolean tickUntil(GameTestHelper helper, BlockEntity blockEntity, int maxTicks, BooleanSupplier done) {
        check(blockEntity.getLevel() == helper.getLevel(), "The block entity at " + blockEntity.getBlockPos() + " is not in the test level");
        for (int tick = 0; tick < maxTicks && !done.getAsBoolean(); tick++) {
            check(tickOnce(blockEntity), "The block entity at " + blockEntity.getBlockPos() + " has no ticker");
        }
        return done.getAsBoolean();
    }

    /**
     * From now until the test times out, ticks every block entity inside the test's bounds once on each game tick its
     * chunk did not tick it. Block entities that work together (a generator feeding a machine through a cable) then
     * advance on the same game ticks, as they would in a ticking chunk. Calling it again for the same test does nothing.
     */
    public static void keepTestAreaTicking(GameTestHelper helper) {
        if (DRIVEN_AREAS.put(helper, Boolean.TRUE) != null) return;
        ServerLevel level = helper.getLevel();
        BoundingBox area = bounds(helper);
        // onEachTick runs from GameTestTicker, after the levels have ticked their block entities for this game tick.
        helper.onEachTick(() -> tickSkipped(level, area));
    }

    /**
     * A sequence that waits until {@code entity} has run {@code ticks} more of its own ticks. Count the entity's ticks,
     * not the test's: the entity sits still until its section is entity-ticking. The test's timeout bounds the wait.
     */
    public static GameTestSequence awaitEntityTicked(GameTestHelper helper, Entity entity, int ticks) {
        int start = entity.tickCount;
        return helper.startSequence().thenWaitUntil(() -> check(entity.tickCount - start >= ticks,
                "The " + entity.getType().getDescription().getString() + " was ticked " + (entity.tickCount - start) + " of " + ticks + " times"));
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
     * still knows its position, size and rotation. Both fields are private; both dev runtimes use official names.
     */
    private static BoundingBox bounds(GameTestHelper helper) {
        try {
            if (testInfo == null) {
                Field info = GameTestHelper.class.getDeclaredField("testInfo");
                info.setAccessible(true);
                Field structure = GameTestInfo.class.getDeclaredField("structureBlockEntity");
                structure.setAccessible(true);
                testInfo = info;
                structureBlockEntity = structure;
            }
            StructureBlockEntity structure = (StructureBlockEntity) structureBlockEntity.get(testInfo.get(helper));
            if (structure == null) {
                throw new IllegalStateException("The GameTest has no structure yet");
            }
            return StructureUtils.getStructureBoundingBox(structure);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot read the GameTest structure", e);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
