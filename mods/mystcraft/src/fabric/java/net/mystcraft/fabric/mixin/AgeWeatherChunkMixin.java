package net.mystcraft.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.mystcraft.minecraft.age.AgeServerLevel;
import net.mystcraft.minecraft.age.AgeWeatherContext;
import net.mystcraft.minecraft.age.AgeWeatherRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Wraps rather than redirects, so seasons/weather mods may hook the same calls; non-age levels call through unchanged. */
@Mixin(ServerLevel.class)
public abstract class AgeWeatherChunkMixin {
    @WrapOperation(method = "tickChunk", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/biome/Biome;shouldFreeze(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z"))
    private boolean mystcraft$freeze(Biome biome, LevelReader level, BlockPos pos, Operation<Boolean> original) {
        if (!(level instanceof AgeServerLevel)) return original.call(biome, level, pos);
        return AgeWeatherContext.with(level, () -> original.call(biome, level, pos));
    }
    @WrapOperation(method = "tickChunk", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/biome/Biome;shouldSnow(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z"))
    private boolean mystcraft$snow(Biome biome, LevelReader level, BlockPos pos, Operation<Boolean> original) {
        if (!(level instanceof AgeServerLevel age)) return original.call(biome, level, pos);
        if (AgeWeatherRules.precipitation(age.weatherMode(), biome.getPrecipitation(), age.weatherActive()) == Biome.Precipitation.NONE) return false;
        return AgeWeatherContext.with(level, () -> original.call(biome, level, pos));
    }
    @WrapOperation(method = "tickChunk", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/biome/Biome;getPrecipitation()Lnet/minecraft/world/level/biome/Biome$Precipitation;"))
    private Biome.Precipitation mystcraft$precipitation(Biome biome, Operation<Biome.Precipitation> original) {
        Biome.Precipitation natural = original.call(biome);
        return (Object) this instanceof AgeServerLevel age
                ? AgeWeatherRules.precipitation(age.weatherMode(), natural, age.weatherActive()) : natural;
    }
}
