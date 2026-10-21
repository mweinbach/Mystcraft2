/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.event.entity.living;

import net.minecraft.world.entity.LivingEntity;
import ports.mekanism.lib.event.entity.EntityEvent;
import ports.mekanism.lib.eventbus.api.Cancelable;

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
