/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.event.entity.living;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.LevelAccessor;
import org.jetbrains.annotations.Nullable;
import ports.mekanism.lib.eventbus.api.Cancelable;

/**
 * Forge's living-spawn event. {@link SpecialSpawn} is posted at the start of {@code Mob.finalizeSpawn}, which is
 * the point Forge's spawn callers reach just before they finalize a mob. The tools module listens for it.
 */
public class LivingSpawnEvent extends LivingEvent {

    private final LevelAccessor level;
    private final double x;
    private final double y;
    private final double z;

    public LivingSpawnEvent(LivingEntity entity, LevelAccessor level, double x, double y, double z) {
        super(entity);
        this.level = level;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public LevelAccessor getLevel() {
        return level;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    /**
     * Cancelling this event skips the rest of {@code Mob.finalizeSpawn}. {@code spawner} is null: that method does
     * not receive the {@link BaseSpawner} Forge passes when a mob spawner is the caller.
     */
    @Cancelable
    public static class SpecialSpawn extends LivingSpawnEvent {

        @Nullable
        private final BaseSpawner spawner;
        private final MobSpawnType spawnReason;

        public SpecialSpawn(Mob entity, LevelAccessor level, double x, double y, double z, @Nullable BaseSpawner spawner, MobSpawnType spawnReason) {
            super(entity, level, x, y, z);
            this.spawner = spawner;
            this.spawnReason = spawnReason;
        }

        @Nullable
        public BaseSpawner getSpawner() {
            return spawner;
        }

        public MobSpawnType getSpawnReason() {
            return spawnReason;
        }
    }
}
