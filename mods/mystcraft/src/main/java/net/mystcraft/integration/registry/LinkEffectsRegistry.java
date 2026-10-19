package net.mystcraft.integration.registry;

import java.util.function.Supplier;
import net.minecraft.core.particles.SimpleParticleType;
import net.mystcraft.integration.platform.RegistryBridge;

public final class LinkEffectsRegistry {
    public static Supplier<SimpleParticleType> LINK;
    private LinkEffectsRegistry() {}
    public static void register(RegistryBridge bridge) { LINK = bridge.registerSimpleParticle("link"); }
}
