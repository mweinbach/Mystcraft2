/*
 * Mekanism 1.19.2 port - in-world tests shared by the Forge and Fabric builds.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import static mekanism.gametest.CoreMachineGameTests.TEMPLATE;
import static mekanism.gametest.CoreMachineGameTests.check;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import mekanism.api.Upgrade;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/**
 * An anchor upgrade keeps its chunk loaded through the loader's chunk tickets, and the saved ticket comes back after
 * the runtime ticket is dropped the way a server start does.
 */
@LevelTickingOnly
public class ChunkLoadingGameTests {

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void anchorUpgradeKeepsItsChunkLoaded(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, MekanismBlocks.TELEPORTER.getBlock());
        TileEntityMekanism teleporter = (TileEntityMekanism) helper.getBlockEntity(pos);
        check(teleporter.getComponent().supports(Upgrade.ANCHOR), "The teleporter does not support the anchor upgrade");
        check(teleporter.getComponent().addUpgrades(Upgrade.ANCHOR, 1) == 1, "The anchor upgrade was not installed");
        BlockPos absolute = helper.absolutePos(pos);
        ServerLevel level = helper.getLevel();
        ChunkPos chunk = new ChunkPos(absolute);
        helper.runAtTickTime(10, () -> {
            check(TestPlatform.anchorChunkLoaded(level, absolute, chunk),
                  "Anchor upgrade did not keep its chunk loaded: " + chunkTicketDebug(level, chunk));
            String restored = TestPlatform.anchorChunkRestored(level, absolute, chunk);
            check(restored == null, restored);
            helper.destroyBlock(pos);
        });
        helper.runAtTickTime(20, () -> {
            check(!TestPlatform.anchorChunkLoaded(level, absolute, chunk),
                  "Chunk ticket remained after the teleporter was removed: " + chunkTicketDebug(level, chunk));
            helper.succeed();
        });
    }

    /**
     * Every ticket on the chunk. {@code DistanceManager#getTicketDebugString} is protected and only names the strongest
     * ticket, which in a GameTest is the test harness, so the anchor ticket would be invisible.
     */
    static String chunkTicketDebug(ServerLevel level, ChunkPos chunk) {
        String reported = level.getChunkSource().getChunkDebugData(chunk).replace('\n', ' ');
        try {
            Field managerField = ServerChunkCache.class.getDeclaredField("distanceManager");
            managerField.setAccessible(true);
            Object manager = managerField.get(level.getChunkSource());
            Field ticketsField = DistanceManager.class.getDeclaredField("tickets");
            ticketsField.setAccessible(true);
            Object map = ticketsField.get(manager);
            Object set = map.getClass().getMethod("get", long.class).invoke(map, chunk.toLong());
            if (!(set instanceof Iterable<?> tickets) || !tickets.iterator().hasNext()) {
                return reported + " | no_ticket";
            }
            StringBuilder listed = new StringBuilder();
            for (Object ticket : tickets) {
                if (!listed.isEmpty()) {
                    listed.append(", ");
                }
                listed.append(ticket);
            }
            return reported + " | " + listed;
        } catch (ReflectiveOperationException e) {
            return reported + " | " + e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    static boolean namesTicket(String text, String ticketName) {
        int from = 0;
        while (from < text.length()) {
            int at = text.indexOf(ticketName, from);
            if (at < 0) {
                return false;
            }
            int end = at + ticketName.length();
            if (end >= text.length() || !isTicketNameChar(text.charAt(end))) {
                return true;
            }
            from = at + 1;
        }
        return false;
    }

    private static boolean isTicketNameChar(char c) {
        return c == '_' || c == ':' || c == '-' || Character.isLetterOrDigit(c);
    }
}
