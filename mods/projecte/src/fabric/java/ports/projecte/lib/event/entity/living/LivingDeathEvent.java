/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.event.entity.living;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import ports.projecte.lib.eventbus.api.Cancelable;

@Cancelable
public class LivingDeathEvent extends LivingEvent {

    private final DamageSource source;

    public LivingDeathEvent(LivingEntity entity, DamageSource source) {
        super(entity);
        this.source = source;
    }

    public DamageSource getSource() {
        return source;
    }
}
