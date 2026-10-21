/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.server.permission;

import java.util.Collection;
import java.util.Collections;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import ports.mekanism.lib.common.MinecraftForge;
import ports.mekanism.lib.server.permission.events.PermissionGatherEvent;
import ports.mekanism.lib.server.permission.nodes.PermissionDynamicContext;
import ports.mekanism.lib.server.permission.nodes.PermissionNode;

/**
 * Answers permission queries with each node's default resolver; Fabric API has no permission handler to defer to.
 */
public final class PermissionAPI {

    private static Collection<PermissionNode<?>> registeredNodes = Collections.emptySet();

    private PermissionAPI() {
    }

    public static Collection<PermissionNode<?>> getRegisteredNodes() {
        return registeredNodes;
    }

    public static <T> T getPermission(ServerPlayer player, PermissionNode<T> node, PermissionDynamicContext<?>... context) {
        return node.getDefaultResolver().resolve(player, player.getUUID(), context);
    }

    public static <T> T getOfflinePermission(UUID player, PermissionNode<T> node, PermissionDynamicContext<?>... context) {
        return node.getDefaultResolver().resolve(null, player, context);
    }

    /**
     * Collects the nodes mods declare. Called by the Fabric entrypoint when a server starts.
     */
    public static void initializePermissionAPI() {
        PermissionGatherEvent.Nodes event = new PermissionGatherEvent.Nodes();
        MinecraftForge.EVENT_BUS.post(event);
        registeredNodes = event.getNodes();
    }
}
