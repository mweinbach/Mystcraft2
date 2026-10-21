/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.event.entity.living;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.LevelAccessor;
import ports.mekanism.lib.eventbus.api.Cancelable;

/**
 * Forge's living-spawn event. The tools module listens for {@link SpecialSpawn}. This port does not post it, so
 * Mekanism armor and swords do not appear on naturally spawned mobs.
 */
public class LivingSpawnEvent extends LivingEvent {

    private final LevelAccessor level;

    public LivingSpawnEvent(LivingEntity entity, LevelAccessor level) {
        super(entity);
        this.level = level;
    }

    public LevelAccessor getLevel() {
        return level;
    }

    @Cancelable
    public static class SpecialSpawn extends LivingSpawnEvent {

        public SpecialSpawn(LivingEntity entity, LevelAccessor level) {
            super(entity, level);
        }
    }
}
