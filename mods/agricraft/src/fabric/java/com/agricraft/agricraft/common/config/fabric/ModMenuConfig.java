package com.agricraft.agricraft.common.config.fabric;

import com.agricraft.agricraft.AgriCraft;
import com.agricraft.agricraft.api.config.AgriCraftConfig;
import com.teamresourceful.resourcefulconfig.client.ConfigScreen;
import com.teamresourceful.resourcefulconfig.common.config.ResourcefulConfig;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Upstream's ModMenu entry: ResourcefulConfig's screen for AgriCraft's config, returning to the mod list on close. */
public class ModMenuConfig implements ModMenuApi {

	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return parent -> {
			ResourcefulConfig config = AgriCraft.CONFIGURATOR.getConfig(AgriCraftConfig.class);
			if (config == null) {
				return null;
			}
			return new ConfigScreen(parent, null, config);
		};
	}

}
