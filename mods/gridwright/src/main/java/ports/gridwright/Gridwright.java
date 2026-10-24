package ports.gridwright;

import com.mojang.logging.LogUtils;
import ports.gridwright.platform.Platform;
import ports.gridwright.registry.ModAutomation;
import ports.gridwright.registry.ModBlockEntities;
import ports.gridwright.registry.ModBlocks;
import ports.gridwright.registry.ModItems;
import ports.gridwright.registry.ModMenus;
import ports.gridwright.registry.ModPower;
import ports.gridwright.registry.ModRecipes;
import ports.gridwright.registry.ModUtilities;
import ports.gridwright.transfer.TransferRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

/**
 * Shared mod definition. Loader entrypoints install a {@link Platform} backend, then call {@link #init()}.
 */
public final class Gridwright {

    public static final String MOD_ID = "gridwright";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final CreativeModeTab TAB = Platform.creativeTab(new ResourceLocation(MOD_ID, "tab"),
            () -> new ItemStack(ModBlocks.SOLAR_PANEL.get()));

    private Gridwright() { }

    /** Declares every registry entry in dependency order: blocks, then items, then everything else. */
    public static void init() {
        ModBlocks.init();
        ModItems.init();
        ModBlockEntities.init();
        ModMenus.init();
        ModRecipes.init();
        ModUtilities.init();
        TransferRegistry.init();
        ModPower.init();
        ModAutomation.init();
    }
}
