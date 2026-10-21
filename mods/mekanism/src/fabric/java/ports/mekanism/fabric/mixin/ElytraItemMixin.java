/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gameevent.GameEvent;
import org.spongepowered.asm.mixin.Mixin;
import ports.mekanism.lib.common.extensions.IForgeItem;

/**
 * Vanilla elytra answer the flight hooks. The chest slot check in {@code LivingEntity} calls those hooks and would
 * otherwise leave an elytra unable to fly, because the interface default returns false.
 */
@Mixin(ElytraItem.class)
public abstract class ElytraItemMixin implements IForgeItem {

    @Override
    public boolean canElytraFly(ItemStack stack, LivingEntity entity) {
        return ElytraItem.isFlyEnabled(stack);
    }

    @Override
    public boolean elytraFlightTick(ItemStack stack, LivingEntity entity, int flightTicks) {
        if (!entity.level.isClientSide) {
            int next = flightTicks + 1;
            if (next % 10 == 0) {
                if (next % 20 == 0) {
                    stack.hurtAndBreak(1, entity, living -> living.broadcastBreakEvent(EquipmentSlot.CHEST));
                }
                entity.gameEvent(GameEvent.ELYTRA_GLIDE);
            }
        }
        return true;
    }
}
