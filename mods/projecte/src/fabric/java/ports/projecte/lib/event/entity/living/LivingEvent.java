/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.event.entity.living;

import net.minecraft.world.entity.LivingEntity;
import ports.projecte.lib.event.entity.EntityEvent;
import ports.projecte.lib.eventbus.api.Cancelable;

public class LivingEvent extends EntityEvent {

    private final LivingEntity livingEntity;

    public LivingEvent(LivingEntity entity) {
        super(entity);
        this.livingEntity = entity;
    }

    @Override
    public LivingEntity getEntity() {
        return livingEntity;
    }

    @Cancelable
    public static class LivingTickEvent extends LivingEvent {

        public LivingTickEvent(LivingEntity entity) {
            super(entity);
        }
    }

    public static class LivingJumpEvent extends LivingEvent {

        public LivingJumpEvent(LivingEntity entity) {
            super(entity);
        }
    }
}
