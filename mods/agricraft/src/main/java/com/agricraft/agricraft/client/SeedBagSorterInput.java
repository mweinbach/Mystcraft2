package com.agricraft.agricraft.client;

import com.agricraft.agricraft.common.item.SeedBagItem;
import com.agricraft.agricraft.common.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.function.IntConsumer;

/**
 * Shift-scrolling with a seed bag in the main hand cycles its sorter. Upstream changed only the client's copy of the
 * bag on Fabric, so the server kept planting in the old order; the loaders now also send the choice to the server
 * ({@link SeedBagItem#selectSorter}).
 */
public final class SeedBagSorterInput {

	private SeedBagSorterInput() {
	}

	/**
	 * @param delta  scroll steps
	 * @param server sends the chosen sorter index to the server
	 * @return true if the scroll was used by the seed bag and must not change the hotbar slot
	 */
	public static boolean onScroll(int delta, IntConsumer server) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || !player.isShiftKeyDown()) {
			return false;
		}
		ItemStack seedBag = player.getMainHandItem();
		if (!seedBag.is(ModItems.SEED_BAG.get())) {
			return false;
		}
		if (delta == 0) {
			return true;
		}
		// update the client's copy straight away; the server applies the same index to the real bag
		SeedBagItem.changeSorter(seedBag, delta);
		int sorter = seedBag.getOrCreateTag().getInt("sorter");
		server.accept(sorter);
		String id = SeedBagItem.SORTERS.get(sorter).getId().toString().replace(":", ".");
		player.displayClientMessage(Component.translatable("agricraft.tooltip.bag.sorter")
				.append(Component.translatable("agricraft.tooltip.bag.sorter." + id)), true);
		return true;
	}

}
