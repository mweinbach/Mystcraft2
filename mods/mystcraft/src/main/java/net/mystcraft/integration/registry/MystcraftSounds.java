package net.mystcraft.integration.registry;

import java.util.function.Supplier;
import net.mystcraft.integration.platform.RegistryBridge;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/** Original classic linking sound, retained under its original resource name. */
public final class MystcraftSounds {
    public static Supplier<SoundEvent> LINK;
    public static Supplier<SoundEvent> PORTAL;
    public static Supplier<SoundEvent> POP, DISARM, FOLLOWING, INTRA;
    private MystcraftSounds() {}

    private static Supplier<SoundEvent> sound(RegistryBridge bridge, String name) {
        return bridge.registerSoundEvent("linking." + name, () -> new SoundEvent(new ResourceLocation("mystcraft", "linking." + name)));
    }
    public static void register(RegistryBridge bridge) {
        POP = sound(bridge, "pop");
        DISARM = sound(bridge, "link-disarm");
        FOLLOWING = sound(bridge, "link-following");
        INTRA = sound(bridge, "link-intra");
        LINK = bridge.registerSoundEvent("linking.link",
            () -> new SoundEvent(new ResourceLocation("mystcraft", "linking.link")));
        PORTAL = bridge.registerSoundEvent("linking.link-portal",
            () -> new SoundEvent(new ResourceLocation("mystcraft", "linking.link-portal")));
    }
}
