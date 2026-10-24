package com.agricraft.agricraft.compat.fabric.botania;

import com.agricraft.agricraft.common.block.entity.CropBlockEntity;
import com.agricraft.agricraft.common.registry.ModBlockEntityTypes;
import com.agricraft.agricraft.compat.botania.AgriHornHarvestable;
import com.agricraft.agricraft.compat.botania.BotaniaPlugin;
import com.agricraft.agricraft.compat.botania.ManaGrowthCondition;
import vazkii.botania.api.BotaniaFabricCapabilities;
import vazkii.botania.api.mana.ManaBlockType;
import vazkii.botania.api.mana.ManaNetworkAction;
import vazkii.botania.api.mana.ManaNetworkCallback;

/**
 * Upstream's Fabric Botania hook: the mana growth condition and plant modifier, the Horn of the Wild harvest, and the
 * pool cache cleanup that upstream registered in {@code AgriCraftFabric}. Called only when Botania is loaded and the
 * {@code compat.botania} option is on.
 */
public class BotaniaFabricPlugin {

	public static void init() {
		BotaniaPlugin.init();
		BotaniaFabricCapabilities.HORN_HARVEST.registerForBlockEntities((blockEntity, context) -> {
			if (blockEntity instanceof CropBlockEntity) {
				return AgriHornHarvestable.INSTANCE;
			}
			return null;
		}, ModBlockEntityTypes.CROP.get());
		ManaNetworkCallback.EVENT.register((receiver, type, action) -> {
			if (action == ManaNetworkAction.REMOVE && type == ManaBlockType.POOL) {
				ManaGrowthCondition.removePoll(receiver);
			}
		});
	}

}
