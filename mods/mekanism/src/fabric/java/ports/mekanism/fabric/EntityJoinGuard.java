/*
 * Mekanism 1.19.2 port - Fabric loader adapter.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric;

/**
 * Forge posts a player's {@code EntityJoinLevelEvent} from {@code ServerLevel.addPlayer}, then adds the player through
 * {@code PersistentEntitySectionManager} without posting a second time. The section-manager mixin reads this flag.
 */
public final class EntityJoinGuard {

    public static final ThreadLocal<Boolean> SKIP = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private EntityJoinGuard() {
    }
}
