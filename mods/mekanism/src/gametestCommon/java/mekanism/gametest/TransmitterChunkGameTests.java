/*
 * Mekanism 1.19.2 port - in-world tests shared by the Forge and Fabric builds.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import static mekanism.gametest.CoreMachineGameTests.TEMPLATE;
import static mekanism.gametest.CoreMachineGameTests.check;

import java.util.Comparator;
import mekanism.common.content.network.transmitter.Transmitter;
import mekanism.common.lib.transmitter.DynamicNetwork;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.tile.transmitter.TileEntityTransmitter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * {@code TransmitterNetworkRegistry} listens for {@code ChunkTicketLevelUpdatedEvent}. When a chunk's ticket level
 * rises past {@link ChunkMap#MAX_VIEW_DISTANCE} the chunk stays in memory but is no longer accessible, and Mekanism
 * separates its transmitters from their networks; when the level comes back the transmitters rejoin. Without the
 * event the cables would stay "loaded" in a network nothing ticks.
 */
@LevelTickingOnly
public class TransmitterChunkGameTests {

    /**
     * Holds a chunk at level 34, one past the last accessible level, so the chunk is kept but not loaded. A region
     * ticket's level is {@code 33 - distance}.
     */
    private static final TicketType<ChunkPos> INACCESSIBLE = TicketType.create("mekanism_gametest_inaccessible",
          Comparator.comparingLong(ChunkPos::toLong));
    private static final int INACCESSIBLE_DISTANCE = -1;
    /** Far west of the test grid, which grows east and south, so no test structure or spawn chunk shares the chunk. */
    private static final int CHUNK_OFFSET = -256;

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void cableNetworkFollowsChunkTicketLevel(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        ChunkPos chunk = new ChunkPos(SectionPos.blockToSectionCoord(origin.getX()) + CHUNK_OFFSET,
              SectionPos.blockToSectionCoord(origin.getZ()));
        BlockPos first = new BlockPos(chunk.getMinBlockX() + 7, origin.getY() + 2, chunk.getMinBlockZ() + 7);
        BlockPos second = first.east();
        TileEntityTransmitter[] cables = new TileEntityTransmitter[2];
        check(level.getChunkSource().getChunkNow(chunk.x, chunk.z) == null, "The far chunk " + chunk + " is already loaded");
        level.setChunkForced(chunk.x, chunk.z, true);
        helper.startSequence()
              .thenWaitUntil(() -> check(level.getChunkSource().getChunkNow(chunk.x, chunk.z) != null,
                    "The forced chunk " + chunk + " did not load"))
              .thenExecute(() -> {
                  level.setBlockAndUpdate(first, MekanismBlocks.BASIC_UNIVERSAL_CABLE.getBlock().defaultBlockState());
                  level.setBlockAndUpdate(second, MekanismBlocks.BASIC_UNIVERSAL_CABLE.getBlock().defaultBlockState());
                  cables[0] = cable(level.getBlockEntity(first));
                  cables[1] = cable(level.getBlockEntity(second));
              })
              .thenWaitUntil(() -> checkConnected(cables, "after the cables were placed"))
              .thenExecute(() -> {
                  //Drop the chunk from forced (31) to 34 in one distance manager update: kept in memory, not accessible
                  level.getChunkSource().addRegionTicket(INACCESSIBLE, chunk, INACCESSIBLE_DISTANCE, chunk);
                  level.setChunkForced(chunk.x, chunk.z, false);
              })
              .thenWaitUntil(() -> {
                  for (TileEntityTransmitter cable : cables) {
                      check(!cable.isRemoved(), "The cable was unloaded instead of kept in an inaccessible chunk");
                      check(!cable.isLoaded(), "The cable at " + cable.getBlockPos() + " still counts as loaded at ticket level 34");
                      check(!cable.getTransmitter().hasTransmitterNetwork(),
                            "The cable at " + cable.getBlockPos() + " is still in a network at ticket level 34");
                  }
              })
              .thenExecute(() -> {
                  level.setChunkForced(chunk.x, chunk.z, true);
                  level.getChunkSource().removeRegionTicket(INACCESSIBLE, chunk, INACCESSIBLE_DISTANCE, chunk);
              })
              .thenWaitUntil(() -> checkConnected(cables, "after the chunk was forced again"))
              .thenExecute(() -> {
                  level.setBlockAndUpdate(first, Blocks.AIR.defaultBlockState());
                  level.setBlockAndUpdate(second, Blocks.AIR.defaultBlockState());
                  level.setChunkForced(chunk.x, chunk.z, false);
              })
              .thenSucceed();
    }

    private static TileEntityTransmitter cable(BlockEntity tile) {
        check(tile instanceof TileEntityTransmitter, "No universal cable tile: " + tile);
        return (TileEntityTransmitter) tile;
    }

    private static void checkConnected(TileEntityTransmitter[] cables, String when) {
        DynamicNetwork<?, ?, ?> network = null;
        for (TileEntityTransmitter cable : cables) {
            Transmitter<?, ?, ?> transmitter = cable.getTransmitter();
            check(cable.isLoaded(), "The cable at " + cable.getBlockPos() + " is not loaded " + when);
            check(transmitter.hasTransmitterNetwork(), "The cable at " + cable.getBlockPos() + " has no network " + when);
            if (network == null) {
                network = transmitter.getTransmitterNetwork();
            }
            check(transmitter.getTransmitterNetwork() == network, "The cables are in different networks " + when);
        }
        check(network != null && network.transmittersSize() == 2,
              "The cable network has " + (network == null ? 0 : network.transmittersSize()) + " transmitters " + when);
    }
}
