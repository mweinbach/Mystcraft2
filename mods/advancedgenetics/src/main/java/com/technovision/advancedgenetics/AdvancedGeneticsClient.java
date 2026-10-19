package com.technovision.advancedgenetics;

import com.mojang.blaze3d.platform.InputConstants;
import com.technovision.advancedgenetics.api.genetics.Genes;
import com.technovision.advancedgenetics.common.block.bloodpurifier.BloodPurifierScreen;
import com.technovision.advancedgenetics.common.block.cellanalyzer.CellAnalyzerScreen;
import com.technovision.advancedgenetics.common.block.dnadecrypter.DnaDecrypterScreen;
import com.technovision.advancedgenetics.common.block.dnaextractor.DnaExtractorScreen;
import com.technovision.advancedgenetics.common.block.plasmidinfuser.PlasmidInfuserScreen;
import com.technovision.advancedgenetics.common.block.plasmidinjector.PlasmidInjectorScreen;
import com.technovision.advancedgenetics.common.item.CellItem;
import com.technovision.advancedgenetics.common.item.SyringeItem;
import com.technovision.advancedgenetics.component.PlayerGeneticsComponent;
import com.technovision.advancedgenetics.events.KeyInputEvents;
import com.technovision.advancedgenetics.network.KeyPressedPacket;
import com.technovision.advancedgenetics.network.ModNetwork;
import com.technovision.advancedgenetics.registry.ComponentRegistry;
import com.technovision.advancedgenetics.registry.ItemRegistry;
import com.technovision.advancedgenetics.registry.ScreenRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.registries.RegistryObject;
import org.lwjgl.glfw.GLFW;

/**
 * Client-only registration: machine screens, syringe model predicate, cell tints and gene key bindings.
 */
public class AdvancedGeneticsClient {

    public static final KeyMapping TELEPORT_KEY = new KeyMapping(KeyInputEvents.KEY_TELEPORT, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, KeyInputEvents.KEY_CATEGORY_GENETICS);
    public static final KeyMapping DRAGONS_BREATH_KEY = new KeyMapping(KeyInputEvents.KEY_DRAGONS_BREATH, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, KeyInputEvents.KEY_CATEGORY_GENETICS);

    public static void init(IEventBus modBus) {
        modBus.addListener(AdvancedGeneticsClient::onClientSetup);
        modBus.addListener(AdvancedGeneticsClient::onRegisterKeyMappings);
        modBus.addListener(AdvancedGeneticsClient::onRegisterItemColors);
        MinecraftForge.EVENT_BUS.addListener(AdvancedGeneticsClient::onClientTick);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            // Register screens
            MenuScreens.register(ScreenRegistry.CELL_ANALYZER_SCREEN_HANDLER.get(), CellAnalyzerScreen::new);
            MenuScreens.register(ScreenRegistry.DNA_EXTRACTOR_SCREEN_HANDLER.get(), DnaExtractorScreen::new);
            MenuScreens.register(ScreenRegistry.DNA_DECRYPTER_SCREEN_HANDLER.get(), DnaDecrypterScreen::new);
            MenuScreens.register(ScreenRegistry.PLASMID_INFUSER_SCREEN_HANDLER.get(), PlasmidInfuserScreen::new);
            MenuScreens.register(ScreenRegistry.BLOOD_PURIFIER_SCREEN_HANDLER.get(), BloodPurifierScreen::new);
            MenuScreens.register(ScreenRegistry.PLASMID_INJECTOR_SCREEN_HANDLER.get(), PlasmidInjectorScreen::new);

            // Updates predicate for syringe items
            ItemProperties.register(
                    ItemRegistry.SYRINGE.get(),
                    new ResourceLocation(AdvancedGenetics.MOD_ID, "filled"),
                    (itemStack, clientWorld, livingEntity, seed) -> SyringeItem.isFilled(itemStack) ? 1 : 0
            );
        });
    }

    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(TELEPORT_KEY);
        event.register(DRAGONS_BREATH_KEY);
    }

    private static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        // Dynamically color cells
        for (RegistryObject<CellItem> item : ItemRegistry.CELLS.values()) {
            event.register((stack, tintIndex) -> item.get().getColor(stack, tintIndex), item.get());
        }
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        PlayerGeneticsComponent component = ComponentRegistry.getGenetics(client.player);
        if (TELEPORT_KEY.consumeClick() && component.hasGene(Genes.TELEPORT)) {
            ModNetwork.CHANNEL.sendToServer(new KeyPressedPacket(KeyInputEvents.TELEPORT));
        }
        else if (DRAGONS_BREATH_KEY.consumeClick() && component.hasGene(Genes.DRAGONS_BREATH)) {
            ModNetwork.CHANNEL.sendToServer(new KeyPressedPacket(KeyInputEvents.DRAGONS_BREATH));
        }
    }
}
