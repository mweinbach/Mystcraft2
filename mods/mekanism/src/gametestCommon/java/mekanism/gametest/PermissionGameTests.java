/*
 * Mekanism 1.19.2 port - in-world tests shared by the Forge and Fabric builds.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import static mekanism.gametest.CoreMachineGameTests.TEMPLATE;
import static mekanism.gametest.CoreMachineGameTests.check;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import mekanism.common.base.MekanismPermissions;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.permission.PermissionAPI;
import net.minecraftforge.server.permission.nodes.PermissionNode;

/**
 * With no permission mod installed, Mekanism's nodes answer with their default resolvers: Forge's default handler on
 * Forge, and on Fabric the same resolvers behind fabric-permissions-api, which falls back to them when no provider
 * decides. The nodes cover both default answers for a player who is not an operator.
 */
public class PermissionGameTests {

    @GameTest(template = TEMPLATE)
    public static void nodesKeepTheirDefaultsWithoutAPermissionMod(GameTestHelper helper) {
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
              new GameProfile(UUID.randomUUID(), "permission-test"), null);
        check(!player.server.getPlayerList().isOp(player.getGameProfile()), "The test player is an operator");
        expect(player, MekanismPermissions.BYPASS_SECURITY, false);
        expect(player, MekanismPermissions.COMMAND.node(), true);
        expect(player, MekanismPermissions.COMMAND_DEBUG.node(), false);
        expect(player, MekanismPermissions.COMMAND_BUILD_REMOVE.node(), true);
        check(!PermissionAPI.getOfflinePermission(player.getUUID(), MekanismPermissions.BYPASS_SECURITY),
              "The offline bypass_security permission changed from its default");
        helper.succeed();
    }

    private static void expect(ServerPlayer player, PermissionNode<Boolean> node, boolean expected) {
        boolean resolved = node.getDefaultResolver().resolve(player, player.getUUID());
        check(resolved == expected, node.getNodeName() + "'s default resolver answered " + resolved);
        boolean actual = PermissionAPI.getPermission(player, node);
        check(actual == resolved, node.getNodeName() + " answered " + actual + " instead of its default " + resolved);
    }
}
