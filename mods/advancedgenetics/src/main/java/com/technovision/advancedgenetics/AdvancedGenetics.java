package com.technovision.advancedgenetics;

import com.mojang.logging.LogUtils;
import com.technovision.advancedgenetics.commands.AddGeneCommand;
import com.technovision.advancedgenetics.commands.ClearGeneCommand;
import com.technovision.advancedgenetics.commands.RemoveGeneCommand;
import com.technovision.advancedgenetics.events.ComponentEvents;
import com.technovision.advancedgenetics.events.GeneticsEvents;
import com.technovision.advancedgenetics.network.ModNetwork;
import com.technovision.advancedgenetics.registry.*;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(AdvancedGenetics.MOD_ID)
public class AdvancedGenetics {

    public static final String MOD_ID = "advancedgenetics";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final CreativeModeTab TAB = new CreativeModeTab(MOD_ID + ".tab") {
        @Override
        public ItemStack makeIcon() {
            return new ItemStack(ItemRegistry.CELL_ANALYZER.get());
        }
    };

    public AdvancedGenetics() {
        // Register and load config
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.COMMON_SPEC, "AdvancedGenetics.toml");

        // Register in-game items, blocks, entities, and GUIs
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        BlockRegistry.BLOCKS.register(modBus);
        ItemRegistry.ITEMS.register(modBus);
        BlockEntityRegistry.BLOCK_ENTITIES.register(modBus);
        ScreenRegistry.MENUS.register(modBus);
        RecipeRegistry.SERIALIZERS.register(modBus);
        RecipeRegistry.TYPES.register(modBus);
        ArgumentTypeRegistry.ARGUMENT_TYPES.register(modBus);
        modBus.addListener(ComponentRegistry::registerCapabilities);
        ModNetwork.register();

        // Register events
        ComponentEvents.registerEvents();
        GeneticsEvents.registerEvents();

        // Register commands
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> {
            AddGeneCommand.register(event.getDispatcher());
            RemoveGeneCommand.register(event.getDispatcher());
            ClearGeneCommand.register(event.getDispatcher());
        });

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> AdvancedGeneticsClient.init(modBus));
    }
}
