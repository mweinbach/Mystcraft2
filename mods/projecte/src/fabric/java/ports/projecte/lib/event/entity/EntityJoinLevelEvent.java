/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.event.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import ports.projecte.lib.eventbus.api.Cancelable;

@Cancelable
public class EntityJoinLevelEvent extends EntityEvent {

    private final Level level;
    private final boolean loadedFromDisk;

    public EntityJoinLevelEvent(Entity entity, Level level) {
        this(entity, level, false);
    }

    public EntityJoinLevelEvent(Entity entity, Level level, boolean loadedFromDisk) {
        super(entity);
        this.level = level;
        this.loadedFromDisk = loadedFromDisk;
    }

    public Level getLevel() {
        return level;
    }

    public boolean loadedFromDisk() {
        return loadedFromDisk;
    }
}
