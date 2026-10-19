package net.mystcraft.integration.registry;

import net.mystcraft.integration.platform.RegistryBridge;
import net.mystcraft.minecraft.age.generation.ClassicChunkGenerator;

public final class GenerationRegistry {
    private GenerationRegistry() {}
    public static void register(RegistryBridge bridge) {
        bridge.registerChunkGeneratorCodec("classic", () -> ClassicChunkGenerator.CODEC);
        bridge.registerBiomeSourceCodec("classic", () -> net.mystcraft.minecraft.age.generation.ClassicBiomeSource.CODEC);
    }
}
