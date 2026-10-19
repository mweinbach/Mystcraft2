package net.mystcraft.integration;

import net.mystcraft.integration.platform.RegistryBridge;
import net.mystcraft.integration.registry.BookEntityRegistry;
import net.mystcraft.integration.registry.BookstandRegistry;
import net.mystcraft.integration.registry.InkMixerRegistry;
import net.mystcraft.integration.registry.MystcraftSounds;
import net.mystcraft.integration.registry.AgeRegistry;
import net.mystcraft.integration.registry.BinderRegistry;
import net.mystcraft.integration.registry.WritingDeskRegistry;
import net.mystcraft.integration.registry.PortalRegistry;
import net.mystcraft.integration.registry.ExplorationRegistry;
import net.mystcraft.integration.registry.StarFissureRegistry;
import net.mystcraft.integration.registry.ModifierRegistry;

/** Identical registry definitions and dependency ordering for every supported loader. */
public final class MystcraftBootstrap {
    public static final String MOD_ID = "mystcraft";
    private static boolean initialized;
    private MystcraftBootstrap() {}

    public static synchronized void initialize(RegistryBridge bridge) {
        if (initialized) throw new IllegalStateException("Mystcraft already initialized");
        net.mystcraft.minecraft.recipe.ClassicCraftingConfig.linkingBookEnabled();
        net.mystcraft.integration.registry.GenerationRegistry.register(bridge);
        net.mystcraft.integration.registry.InkRegistry.registerFluids(bridge);
        net.mystcraft.integration.registry.InkRegistry.registerBlock(bridge);
        BookstandRegistry.registerBlocks(bridge);
        InkMixerRegistry.registerBlocks(bridge);
        BinderRegistry.registerBlocks(bridge);
        WritingDeskRegistry.registerBlocks(bridge);
        PortalRegistry.registerBlocks(bridge);
        ExplorationRegistry.registerBlocks(bridge);
        StarFissureRegistry.registerBlocks(bridge);
        ModifierRegistry.registerBlocks(bridge);
        net.mystcraft.integration.registry.DecayRegistry.registerBlocks(bridge);
        MystcraftItems.register(bridge);
        net.mystcraft.integration.registry.InkRegistry.registerItems(bridge);
        AgeRegistry.register(bridge);
        WritingDeskRegistry.register(bridge);
        BinderRegistry.register(bridge);
        PortalRegistry.register(bridge);
        ExplorationRegistry.register(bridge);
        StarFissureRegistry.register(bridge);
        ModifierRegistry.register(bridge);
        net.mystcraft.integration.registry.DecayRegistry.register(bridge);
        BookstandRegistry.register(bridge);
        InkMixerRegistry.register(bridge);
        BookEntityRegistry.register(bridge);
        net.mystcraft.integration.registry.MeteorRegistry.register(bridge);
        net.mystcraft.integration.registry.DecayFallingRegistry.register(bridge);
        net.mystcraft.integration.registry.LightningRegistry.register(bridge);
        net.mystcraft.integration.registry.VillageArchivistRegistry.register(bridge);
        net.mystcraft.minecraft.instability.InstabilityRuntime.setMeteorSpawner((level, pos) ->
                net.mystcraft.integration.registry.MeteorRegistry.spawn(level, pos.x, pos.y, pos.z));
        net.mystcraft.integration.registry.BookDisplayRegistry.register(bridge);
        MystcraftSounds.register(bridge);
        net.mystcraft.integration.registry.LinkEffectsRegistry.register(bridge);
        initialized = true;
    }
}
