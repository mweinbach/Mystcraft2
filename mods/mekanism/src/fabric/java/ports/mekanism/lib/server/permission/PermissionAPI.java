/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.server.permission;

import java.util.Collection;
import java.util.Collections;
import java.util.UUID;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.server.level.ServerPlayer;
import ports.mekanism.lib.common.MinecraftForge;
import ports.mekanism.lib.server.permission.events.PermissionGatherEvent;
import ports.mekanism.lib.server.permission.nodes.PermissionDynamicContext;
import ports.mekanism.lib.server.permission.nodes.PermissionNode;
import ports.mekanism.lib.server.permission.nodes.PermissionTypes;

/**
 * Forge's PermissionAPI asks the installed permission handler. On Fabric that role belongs to fabric-permissions-api,
 * which LuckPerms and other permission mods answer: an online player's boolean node goes through
 * {@link Permissions#check}, and the node's default resolver supplies the answer when no provider decides it.
 * fabric-permissions-api has no synchronous offline query and no integer, string or component values, so offline
 * queries and non-boolean nodes keep the default resolver.
 */
public final class PermissionAPI {

    private static Collection<PermissionNode<?>> registeredNodes = Collections.emptySet();

    private PermissionAPI() {
    }

    public static Collection<PermissionNode<?>> getRegisteredNodes() {
        return registeredNodes;
    }

    @SuppressWarnings("unchecked")
    public static <T> T getPermission(ServerPlayer player, PermissionNode<T> node, PermissionDynamicContext<?>... context) {
        T fallback = node.getDefaultResolver().resolve(player, player.getUUID(), context);
        if (node.getType() == PermissionTypes.BOOLEAN && fallback instanceof Boolean defaultValue) {
            return (T) Boolean.valueOf(Permissions.check(player, node.getNodeName(), defaultValue));
        }
        return fallback;
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
