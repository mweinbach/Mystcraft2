package com.agricraft.agricraft.compat;

import com.agricraft.agricraft.AgriCraft;
import com.agricraft.agricraft.compat.jei.AgriCraftJeiPlugin;

/**
 * Which recipe viewers are installed (by API class presence), and the hook that refreshes them once a remote
 * server's plants and mutations arrive. Port-specific: on 1.19.2 AgriCraft's data reaches remote clients in the
 * {@code agricraft:data_registries} packet, after the recipes and tags the viewers build their lists from.
 * Viewer classes are only touched after a presence check, so nothing loads when a viewer is absent.
 */
public final class RecipeViewers {

	public static final String JEI_API = "mezz.jei.api.IModPlugin";
	public static final String EMI_API = "dev.emi.emi.api.EmiPlugin";
	public static final String REI_API = "me.shedaniel.rei.api.common.display.Display";

	/** EMI's reload entry point (not API; called reflectively). */
	static final String EMI_RELOAD = "dev.emi.emi.runtime.EmiReloadManager";
	/** REI's client plugin reload entry point (not API; called reflectively). */
	static final String REI_RELOAD = "me.shedaniel.rei.RoughlyEnoughItemsCoreClient";

	private RecipeViewers() {
	}

	public static boolean present(String className) {
		try {
			Class.forName(className, false, RecipeViewers.class.getClassLoader());
			return true;
		} catch (ClassNotFoundException | LinkageError e) {
			return false;
		}
	}

	/** EMI shows JEI plugins' categories itself, so AgriCraft's JEI plugin stays quiet when EMI is installed. */
	public static boolean emi() {
		return present(EMI_API);
	}

	/**
	 * Called on the client thread after a remote server's AgriCraft data was applied. EMI and REI rebuild their
	 * recipe lists; JEI 11 has no public reload, so AgriCraft's JEI plugin adds the missing recipes and seeds
	 * through JEI's runtime API instead.
	 */
	public static void reloadAfterRemoteData() {
		if (present(EMI_RELOAD)) {
			run("EMI", () -> Class.forName(EMI_RELOAD).getMethod("reload").invoke(null));
		}
		if (present(REI_RELOAD)) {
			// reloadPlugins(lastReload, stage): no rate limit, and a null stage reloads every stage.
			run("REI", () -> {
				Class<?> stage = Class.forName("me.shedaniel.rei.api.common.registry.ReloadStage");
				Class.forName(REI_RELOAD).getMethod("reloadPlugins", org.apache.commons.lang3.mutable.MutableLong.class, stage)
						.invoke(null, null, null);
			});
		}
		if (present(JEI_API)) {
			run("JEI", AgriCraftJeiPlugin::addMissingDisplays);
		}
	}

	private interface Action {
		void run() throws Exception;
	}

	private static void run(String viewer, Action action) {
		try {
			action.run();
		} catch (Throwable e) {
			AgriCraft.LOGGER.warn("Could not refresh {} after the server's AgriCraft data arrived", viewer, e);
		}
	}

}
