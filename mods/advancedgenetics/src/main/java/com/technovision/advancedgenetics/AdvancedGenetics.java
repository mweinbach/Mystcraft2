package com.technovision.advancedgenetics;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import com.technovision.advancedgenetics.commands.AddGeneCommand;
import com.technovision.advancedgenetics.commands.ClearGeneCommand;
import com.technovision.advancedgenetics.commands.RemoveGeneCommand;
import com.technovision.advancedgenetics.platform.Platform;
import com.technovision.advancedgenetics.registry.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

/**
 * Shared mod definition. Loader entrypoints install a {@link Platform} backend, then call {@link #init()}.
 */
public final class AdvancedGenetics {

    public static final String MOD_ID = "advancedgenetics";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final CreativeModeTab TAB = Platform.creativeTab(new ResourceLocation(MOD_ID, "tab"),
            () -> new ItemStack(ItemRegistry.CELL_ANALYZER.get()));

    private AdvancedGenetics() { }

    /** Declares every registry entry in dependency order: blocks, then items, then everything else. */
    public static void init() {
        BlockRegistry.init();
        ItemRegistry.init();
        BlockEntityRegistry.init();
        ScreenRegistry.init();
        RecipeRegistry.init();
        ArgumentTypeRegistry.init();
    }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        AddGeneCommand.register(dispatcher);
        RemoveGeneCommand.register(dispatcher);
        ClearGeneCommand.register(dispatcher);
    }
}
