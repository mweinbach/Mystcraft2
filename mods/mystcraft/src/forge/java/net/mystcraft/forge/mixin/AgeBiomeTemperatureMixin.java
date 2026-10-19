package net.mystcraft.forge.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import net.mystcraft.minecraft.client.age.AgeBiomeTemperature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Biome.class)
public interface AgeBiomeTemperatureMixin extends AgeBiomeTemperature {
    @Invoker("getTemperature")
    float mystcraft$temperature(BlockPos pos);
}
