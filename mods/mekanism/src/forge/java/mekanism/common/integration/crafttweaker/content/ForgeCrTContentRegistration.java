package mekanism.common.integration.crafttweaker.content;

import java.util.Optional;
import mekanism.common.integration.MekanismHooks;
import net.minecraftforge.data.loading.DatagenModLoader;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.javafmlmod.FMLModContainer;

/**
 * Upstream's CraftTweaker content block from {@code Mekanism}'s constructor, moved here because it names Forge's mod
 * container type. {@link MekanismHooks} resolves this class by name only when CraftTweaker is loaded.
 */
public final class ForgeCrTContentRegistration {

    private ForgeCrTContentRegistration() {
    }

    public static void register(IEventBus modEventBus) {
        if (DatagenModLoader.isRunningDataGen()) {
            return;
        }
        //Attempt to grab the mod event bus for CraftTweaker so that we can register our custom content in their namespace
        // to make it clearer which chemicals were added by CraftTweaker, and which are added by actual mods.
        // Gracefully fallback to our event bus if something goes wrong with getting CrT's and just then have the log have
        // warnings about us registering things in their namespace.
        IEventBus crtModEventBus = modEventBus;
        Optional<? extends ModContainer> crtModContainer = ModList.get().getModContainerById(MekanismHooks.CRAFTTWEAKER_MOD_ID);
        if (crtModContainer.isPresent()) {
            ModContainer container = crtModContainer.get();
            if (container instanceof FMLModContainer modContainer) {
                crtModEventBus = modContainer.getEventBus();
            }
        }
        //Register our CrT listener at lowest priority to try and ensure they get later ids than our normal registries
        crtModEventBus.addListener(EventPriority.LOWEST, CrTContentUtils::registerCrTContent);
    }
}
