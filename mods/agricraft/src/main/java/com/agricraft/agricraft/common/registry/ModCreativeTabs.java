package com.agricraft.agricraft.common.registry;

import com.agricraft.agricraft.common.util.Platform;
import net.minecraft.world.item.CreativeModeTab;

/**
 * 1.19.2 creative tabs are not registry entries. Items join a tab through {@code Item.Properties#tab}, which also
 * puts them in the creative search tab; the seed item lists one seed per loaded plant.
 */
public class ModCreativeTabs {

	public static final CreativeModeTab MAIN_TAB = Platform.get().createMainCreativeTab();
	public static final CreativeModeTab SEED_TAB = Platform.get().createSeedsCreativeTab();

	public static void init() {
	}

}
