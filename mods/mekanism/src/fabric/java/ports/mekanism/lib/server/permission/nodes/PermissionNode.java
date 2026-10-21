/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.server.permission.nodes;

import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * A named permission with a default answer. Nothing on Fabric overrides the default, so the resolver decides.
 */
public final class PermissionNode<T> {

    private final String nodeName;
    private final PermissionType<T> type;
    private final PermissionResolver<T> defaultResolver;
    private final PermissionDynamicContextKey<?>[] dynamics;

    @SafeVarargs
    public PermissionNode(String modID, String nodeName, PermissionType<T> type, PermissionResolver<T> defaultResolver, PermissionDynamicContextKey<?>... dynamics) {
        this.nodeName = modID + "." + nodeName;
        this.type = type;
        this.defaultResolver = defaultResolver;
        this.dynamics = dynamics;
    }

    public String getNodeName() {
        return nodeName;
    }

    public PermissionType<T> getType() {
        return type;
    }

    public PermissionResolver<T> getDefaultResolver() {
        return defaultResolver;
    }

    public PermissionDynamicContextKey<?>[] getDynamics() {
        return dynamics;
    }

    @Override
    public String toString() {
        return nodeName;
    }

    @FunctionalInterface
    public interface PermissionResolver<T> {

        T resolve(@Nullable ServerPlayer player, UUID playerUUID, PermissionDynamicContext<?>... context);
    }
}
