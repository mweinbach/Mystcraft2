package net.mystcraft.fabric;

import net.fabricmc.fabric.api.client.rendering.v1.DimensionRenderingRegistry;
import net.mystcraft.minecraft.client.age.AgeDimensionEffects;
import net.mystcraft.minecraft.client.age.AgeVisualClient;

public final class FabricAgeVisualEffects {
    private FabricAgeVisualEffects() {}
    public static void initialize() {
        DimensionRenderingRegistry.registerDimensionEffects(AgeVisualClient.EFFECTS, new AgeDimensionEffects());
    }
}
