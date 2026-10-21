/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.event.entity.living;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import ports.mekanism.lib.eventbus.api.Cancelable;

@Cancelable
public class ShieldBlockEvent extends LivingEvent {

    private final DamageSource source;
    private final float originalBlocked;
    private float dmgBlocked;
    private boolean shieldTakesDamage = true;

    public ShieldBlockEvent(LivingEntity blocker, DamageSource source, float blocked) {
        super(blocker);
        this.source = source;
        this.originalBlocked = blocked;
        this.dmgBlocked = blocked;
    }

    public DamageSource getDamageSource() {
        return source;
    }

    public float getOriginalBlockedDamage() {
        return originalBlocked;
    }

    public float getBlockedDamage() {
        return dmgBlocked;
    }

    public void setBlockedDamage(float blocked) {
        this.dmgBlocked = Math.min(blocked, originalBlocked);
    }

    public boolean shieldTakesDamage() {
        return shieldTakesDamage;
    }

    public void setShieldTakesDamage(boolean damage) {
        this.shieldTakesDamage = damage;
    }
}
