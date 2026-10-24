package ports.gridwright.forge.compat;

import ports.gridwright.compat.rei.GridwrightReiPlugin;
import me.shedaniel.rei.forge.REIPluginClient;

/** Forge REI discovers client plugins by this annotation; the shared plugin does the work. */
@REIPluginClient
public class GridwrightReiForgePlugin extends GridwrightReiPlugin { }
