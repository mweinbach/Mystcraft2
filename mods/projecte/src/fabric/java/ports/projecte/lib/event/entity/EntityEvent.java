/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.event.entity;

import net.minecraft.world.entity.Entity;
import ports.projecte.lib.eventbus.api.Event;

public class EntityEvent extends Event {

    private final Entity entity;

    public EntityEvent(Entity entity) {
        this.entity = entity;
    }

    public Entity getEntity() {
        return entity;
    }

    /**
     * Posted at the end of {@code Entity}'s constructor, before the entity has a level position or saved data.
     */
    public static class EntityConstructing extends EntityEvent {

        public EntityConstructing(Entity entity) {
            super(entity);
        }
    }
}
