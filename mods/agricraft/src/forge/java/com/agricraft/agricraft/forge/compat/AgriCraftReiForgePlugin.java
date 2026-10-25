package com.agricraft.agricraft.forge.compat;

import com.agricraft.agricraft.compat.rei.AgriCraftReiPlugin;
import me.shedaniel.rei.forge.REIPluginClient;

/** Forge REI discovers client plugins by this annotation; the shared plugin does the work. */
@REIPluginClient
public class AgriCraftReiForgePlugin extends AgriCraftReiPlugin {
}
