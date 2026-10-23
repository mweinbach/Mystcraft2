package ports.jurassicraft.genetics;

import java.util.Arrays;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.ForgeGameTestHooks;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;
import ports.jurassicraft.PortMod;

/** Registers {@link LabExtensionGameTests}' fixtures, only in GameTest runs that include this mod. */
@Mod.EventBusSubscriber(modid = PortMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ForgeLabExtensionFixtures {
    private ForgeLabExtensionFixtures() {}

    @SubscribeEvent
    public static void registerFixtures(RegisterEvent event) {
        if (!ForgeGameTestHooks.isGametestEnabled()) return;
        var namespaces = Arrays.stream(System.getProperty("forge.enabledGameTestNamespaces", "").split(","))
                .filter(name -> !name.isBlank()).toList();
        if (!namespaces.isEmpty() && !namespaces.contains(PortMod.MOD_ID)) return;
        event.register(ForgeRegistries.Keys.BLOCKS, registry -> LabExtensionGameTests.registerBlocks(registry::register));
        event.register(ForgeRegistries.Keys.ITEMS, registry -> LabExtensionGameTests.registerItems(registry::register));
    }
}
