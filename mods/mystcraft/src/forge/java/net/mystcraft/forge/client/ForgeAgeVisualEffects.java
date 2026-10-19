package net.mystcraft.forge.client;

import net.mystcraft.minecraft.client.age.AgeDimensionEffects;
import net.mystcraft.minecraft.client.age.AgeVisualClient;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** A dedicated effects ID scopes all presentation hooks to Mystcraft Ages. */
@Mod.EventBusSubscriber(modid = "mystcraft", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ForgeAgeVisualEffects {
    private ForgeAgeVisualEffects() {}
    @SubscribeEvent public static void register(RegisterDimensionSpecialEffectsEvent event) {
        event.register(AgeVisualClient.EFFECTS, new ForgeAgeDimensionEffects());
    }
}
