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
import com.technovision.advancedgenetics.registry.ComponentRegistry;
import com.technovision.advancedgenetics.registry.ItemRegistry;
import com.technovision.advancedgenetics.registry.ScreenRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Client-only registration: machine screens, syringe model predicate, cell tints and gene key
 * bindings. Loader client entrypoints call these at their equivalent registration points.
 */
public final class AdvancedGeneticsClient {

    public static final KeyMapping TELEPORT_KEY = new KeyMapping(KeyInputEvents.KEY_TELEPORT, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, KeyInputEvents.KEY_CATEGORY_GENETICS);
    public static final KeyMapping DRAGONS_BREATH_KEY = new KeyMapping(KeyInputEvents.KEY_DRAGONS_BREATH, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, KeyInputEvents.KEY_CATEGORY_GENETICS);
    public static final List<KeyMapping> KEY_MAPPINGS = List.of(TELEPORT_KEY, DRAGONS_BREATH_KEY);

    private AdvancedGeneticsClient() { }

    /** Registers machine screens and the syringe model predicate; run on the client main thread. */
    public static void registerScreensAndProperties() {
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
    }

    /** Dynamically color cells. */
    public static void registerItemColors(BiConsumer<ItemColor, Item> registrar) {
        for (Supplier<CellItem> item : ItemRegistry.CELLS.values()) {
            CellItem cell = item.get();
            registrar.accept(cell::getColor, cell);
        }
    }

    /** Sends gene key presses to the server; call at the end of each client tick. */
    public static void tick(Minecraft client, Consumer<KeyPressedPacket> sender) {
        if (client.player == null) return;
        PlayerGeneticsComponent component = ComponentRegistry.getGenetics(client.player);
        if (TELEPORT_KEY.consumeClick() && component.hasGene(Genes.TELEPORT)) {
            sender.accept(new KeyPressedPacket(KeyInputEvents.TELEPORT));
        }
        else if (DRAGONS_BREATH_KEY.consumeClick() && component.hasGene(Genes.DRAGONS_BREATH)) {
            sender.accept(new KeyPressedPacket(KeyInputEvents.DRAGONS_BREATH));
        }
    }
}
