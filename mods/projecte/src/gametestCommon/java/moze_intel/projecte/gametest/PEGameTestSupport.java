/*
 * ProjectE 1.19.2 port - in-world tests shared by the Forge and Fabric builds.
 * Copyright (c) 2026 ProjectE 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package moze_intel.projecte.gametest;

import com.mojang.authlib.GameProfile;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.api.capabilities.PECapabilities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.FakePlayerFactory;

/**
 * Helpers for the shared GameTests. Each test gets its own fake player, so knowledge and EMC never leak between tests
 * that run in the same batch.
 */
public final class PEGameTestSupport {

    public static final String TEMPLATE = "projecte:empty";

    private PEGameTestSupport() {
    }

    /** A server-side fake player named after the test, standing on the test's origin and looking straight up. */
    public static ServerPlayer player(GameTestHelper helper, String name) {
        UUID id = UUID.nameUUIDFromBytes(("projecte_gametest_" + name).getBytes(StandardCharsets.UTF_8));
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(id, "pe_" + name));
        BlockPos origin = helper.absolutePos(BlockPos.ZERO.above());
        player.moveTo(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5, 0, -90);
        player.getInventory().clearContent();
        IKnowledgeProvider knowledge = knowledge(player);
        knowledge.clearKnowledge();
        knowledge.setEmc(BigInteger.ZERO);
        return player;
    }

    public static IKnowledgeProvider knowledge(Player player) {
        return player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY)
              .orElseThrow(() -> new IllegalStateException("Player " + player.getScoreboardName() + " has no ProjectE knowledge"));
    }

    /** Fails the current {@code succeedWhen} attempt; vanilla 1.19.2's helper has no message-taking assertTrue. */
    public static void require(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    public static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            helper.fail(message);
        }
    }
}
