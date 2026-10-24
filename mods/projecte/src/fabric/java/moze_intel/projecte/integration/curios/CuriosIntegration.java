/*
 * ProjectE 1.19.2 port - Fabric replacement for an upstream class.
 * Copyright (c) 2026 ProjectE 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package moze_intel.projecte.integration.curios;

import dev.emi.trinkets.api.SlotReference;
import dev.emi.trinkets.api.Trinket;
import dev.emi.trinkets.api.TrinketInventory;
import dev.emi.trinkets.api.TrinketsApi;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import moze_intel.projecte.PECore;
import net.minecraft.core.Registry;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import ports.projecte.lib.fml.event.lifecycle.InterModEnqueueEvent;
import ports.projecte.lib.items.IItemHandler;
import ports.projecte.lib.items.IItemHandlerModifiable;
import ports.projecte.lib.items.wrapper.CombinedInvWrapper;
import ports.projecte.lib.items.wrapper.InvWrapper;

/**
 * The Trinkets counterpart of upstream's Curios integration. Slots are data driven on Trinkets: the Fabric jar ships
 * {@code data/trinkets/slots}, {@code data/trinkets/entities} and the item tags that let ProjectE's rings, amulets,
 * belts and Klein Stars go in them.
 */
public class CuriosIntegration {

	/**
	 * Every trinket slot the entity has, as one item handler, as {@code getEquippedCurios} gives on Forge.
	 */
	@Nullable
	public static IItemHandler getAll(LivingEntity living) {
		return TrinketsApi.getTrinketComponent(living).map(component -> {
			List<IItemHandlerModifiable> handlers = new ArrayList<>();
			for (Map<String, TrinketInventory> group : component.getInventory().values()) {
				for (TrinketInventory inventory : group.values()) {
					handlers.add(new InvWrapper(inventory));
				}
			}
			return (IItemHandler) new CombinedInvWrapper(handlers.toArray(IItemHandlerModifiable[]::new));
		}).orElse(null);
	}

	/**
	 * Trinkets asks each item's registered {@link Trinket} what to do. ProjectE's items answer through the capability
	 * {@link CurioItemCapability} attaches, so one delegate serves them all and does nothing for items without it.
	 */
	public static void sendIMC(InterModEnqueueEvent event) {
		Trinket delegate = new Trinket() {
			@Override
			public void tick(ItemStack stack, SlotReference slot, LivingEntity entity) {
				stack.getCapability(CurioItemCapability.TRINKET).ifPresent(trinket -> trinket.tick(stack, slot, entity));
			}
		};
		for (Map.Entry<net.minecraft.resources.ResourceKey<Item>, Item> entry : Registry.ITEM.entrySet()) {
			if (entry.getKey().location().getNamespace().equals(PECore.MODID)) {
				TrinketsApi.registerTrinket(entry.getValue(), delegate);
			}
		}
	}
}
