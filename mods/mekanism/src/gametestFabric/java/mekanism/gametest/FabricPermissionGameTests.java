/*
 * Mekanism 1.19.2 port - Fabric-only in-world tests.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import static mekanism.gametest.CoreMachineGameTests.TEMPLATE;
import static mekanism.gametest.CoreMachineGameTests.check;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import me.lucko.fabric.api.permissions.v0.PermissionCheckEvent;
import mekanism.common.base.MekanismPermissions;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import ports.mekanism.lib.server.permission.PermissionAPI;

/**
 * A Fabric permission mod such as LuckPerms answers {@link PermissionCheckEvent}. This stand-in provider decides two
 * Mekanism nodes for one test player only, and must override both defaults: grant a node that defaults to false and
 * deny one that defaults to true. The shared {@link PermissionGameTests} covers the no-provider defaults.
 */
public class FabricPermissionGameTests {

    private static final UUID PROVIDED = UUID.randomUUID();
    private static boolean providerRegistered;

    @GameTest(template = TEMPLATE)
    public static void permissionModDecidesMekanismNodes(GameTestHelper helper) {
        registerProvider();
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
              new GameProfile(PROVIDED, "permission-provider-test"), null);
        check(!MekanismPermissions.BYPASS_SECURITY.getDefaultResolver().resolve(player, PROVIDED),
              "bypass_security already defaults to true for the test player");
        check(PermissionAPI.getPermission(player, MekanismPermissions.BYPASS_SECURITY),
              "The permission provider's grant of mekanism.bypass_security was ignored");
        check(MekanismPermissions.COMMAND_BUILD_REMOVE.node().getDefaultResolver().resolve(player, PROVIDED),
              "command.build.remove no longer defaults to true");
        check(!PermissionAPI.getPermission(player, MekanismPermissions.COMMAND_BUILD_REMOVE.node()),
              "The permission provider's denial of mekanism.command.build.remove was ignored");
        //A node the provider leaves undecided keeps its default
        check(!PermissionAPI.getPermission(player, MekanismPermissions.COMMAND_DEBUG.node()),
              "mekanism.command.debug changed although the provider did not decide it");
        helper.succeed();
    }

    private static synchronized void registerProvider() {
        if (providerRegistered) {
            return;
        }
        providerRegistered = true;
        PermissionCheckEvent.EVENT.register((source, permission) -> {
            if (!(source instanceof CommandSourceStack stack) || stack.getEntity() == null || !PROVIDED.equals(stack.getEntity().getUUID())) {
                return TriState.DEFAULT;
            }
            if (permission.equals(MekanismPermissions.BYPASS_SECURITY.getNodeName())) {
                return TriState.TRUE;
            }
            if (permission.equals(MekanismPermissions.COMMAND_BUILD_REMOVE.node().getNodeName())) {
                return TriState.FALSE;
            }
            return TriState.DEFAULT;
        });
    }
}
