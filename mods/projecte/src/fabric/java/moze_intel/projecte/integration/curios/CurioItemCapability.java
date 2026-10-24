/*
 * ProjectE 1.19.2 port - Fabric replacement for an upstream class.
 * Copyright (c) 2026 ProjectE 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package moze_intel.projecte.integration.curios;

import dev.emi.trinkets.api.SlotReference;
import dev.emi.trinkets.api.Trinket;
import moze_intel.projecte.capability.BasicItemCapability;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import ports.projecte.lib.common.capabilities.Capability;
import ports.projecte.lib.common.capabilities.CapabilityManager;
import ports.projecte.lib.common.capabilities.CapabilityToken;

/**
 * The Trinkets counterpart of upstream's Curios capability: while the stack sits in a trinket slot it ticks as if it
 * were in the inventory, as {@code curioTick} does on Forge. Trinkets has no cosmetic slots in 3.4.
 */
public class CurioItemCapability extends BasicItemCapability<Trinket> implements Trinket {

	public static final Capability<Trinket> TRINKET = CapabilityManager.get(new CapabilityToken<>() {});

	@Override
	public Capability<Trinket> getCapability() {
		return TRINKET;
	}

	@Override
	public ItemStack getStack() {
		return super.getStack();
	}

	@Override
	public void tick(ItemStack stack, SlotReference slot, LivingEntity entity) {
		stack.inventoryTick(entity.getCommandSenderWorld(), entity, slot.index(), false);
	}
}
