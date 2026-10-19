package net.mystcraft.forge.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.mystcraft.minecraft.age.AgeServerLevel;
import net.mystcraft.minecraft.age.AgeWeatherContext;
import net.mystcraft.minecraft.age.AgeWeatherRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerLevel.class)
public abstract class AgeWeatherChunkMixin {
    @Redirect(method = "tickChunk", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/biome/Biome;shouldFreeze(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z"))
    private boolean mystcraft$freeze(Biome biome, LevelReader level, BlockPos pos) {
        return AgeWeatherContext.with(level, () -> biome.shouldFreeze(level, pos));
    }
    @Redirect(method = "tickChunk", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/biome/Biome;shouldSnow(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z"))
    private boolean mystcraft$snow(Biome biome, LevelReader level, BlockPos pos) {
        if (level instanceof AgeServerLevel age && AgeWeatherRules.precipitation(age.weatherMode(), biome.getPrecipitation(), age.weatherActive()) == Biome.Precipitation.NONE) return false;
        return AgeWeatherContext.with(level, () -> biome.shouldSnow(level, pos));
    }
    @Redirect(method = "tickChunk", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/biome/Biome;getPrecipitation()Lnet/minecraft/world/level/biome/Biome$Precipitation;"))
    private Biome.Precipitation mystcraft$precipitation(Biome biome) {
        return (Object) this instanceof AgeServerLevel age
                ? AgeWeatherRules.precipitation(age.weatherMode(), biome.getPrecipitation(), age.weatherActive()) : biome.getPrecipitation();
    }
}
