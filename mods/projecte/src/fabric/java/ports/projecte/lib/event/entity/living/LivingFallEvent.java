/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.event.entity.living;

import net.minecraft.world.entity.LivingEntity;
import ports.projecte.lib.eventbus.api.Cancelable;

@Cancelable
public class LivingFallEvent extends LivingEvent {

    private float distance;
    private float damageMultiplier;

    public LivingFallEvent(LivingEntity entity, float distance, float damageMultiplier) {
        super(entity);
        this.distance = distance;
        this.damageMultiplier = damageMultiplier;
    }

    public float getDistance() {
        return distance;
    }

    public void setDistance(float distance) {
        this.distance = distance;
    }

    public float getDamageMultiplier() {
        return damageMultiplier;
    }

    public void setDamageMultiplier(float damageMultiplier) {
        this.damageMultiplier = damageMultiplier;
    }
}
