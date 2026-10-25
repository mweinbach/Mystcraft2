package com.agricraft.agricraft.common.plugin.fabric;

import com.agricraft.agricraft.api.AgriApi;
import com.agricraft.agricraft.api.requirement.AgriSeason;

/**
 * Upstream's Fabric Seasons hook, kept as upstream 4.0.6 ships it: it claims AgriCraft's season logic and reports
 * {@link AgriSeason#ANY} everywhere, so season requirements never block growth on Fabric. It needs no Fabric Seasons
 * classes.
 */
public class FabricSeasonPlugin {

	public static void init() {
		AgriApi.getSeasonLogic().claim(FabricSeasonPlugin.class, (level, pos) -> AgriSeason.ANY);
	}

}
