package com.agricraft.agricraft;

import com.agricraft.agricraft.api.AgriApi;
import com.agricraft.agricraft.api.config.AgriCraftConfig;
import com.agricraft.agricraft.common.registry.ModBlockEntityTypes;
import com.agricraft.agricraft.common.registry.ModBlocks;
import com.agricraft.agricraft.common.registry.ModCreativeTabs;
import com.agricraft.agricraft.common.registry.ModItems;
import com.agricraft.agricraft.common.registry.ModMenus;
import com.agricraft.agricraft.common.registry.ModRecipeSerializers;
import com.agricraft.agricraft.common.registry.ModSoundEvents;
import com.mojang.logging.LogUtils;
import com.teamresourceful.resourcefulconfig.common.config.Configurator;
import org.slf4j.Logger;

public class AgriCraft {

	public static final Logger LOGGER = LogUtils.getLogger();
	public static final Configurator CONFIGURATOR = new Configurator();

	public static void init() {
		CONFIGURATOR.registerConfig(AgriCraftConfig.class);

		ModBlocks.BLOCKS.init();
		ModItems.ITEMS.init();
		ModBlockEntityTypes.BLOCK_ENTITY_TYPES.init();
		ModCreativeTabs.init();
		ModMenus.MENUS.init();
		ModRecipeSerializers.RECIPE_SERIALIZERS.init();
		ModSoundEvents.SOUND_EVENTS.init();
		LOGGER.info("Intializing API for " + AgriApi.MOD_ID);
	}

}
