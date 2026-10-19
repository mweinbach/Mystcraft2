package net.mystcraft.fabric.mixin;

import net.minecraft.server.level.WorldGenRegion;
import net.mystcraft.minecraft.age.generation.RegionWriteBounds;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read vanilla's actual permitted radius; this accessor never changes it. */
@Mixin(WorldGenRegion.class)
public interface WorldGenRegionBoundsMixin extends RegionWriteBounds {
    @Override @Accessor("writeRadiusCutoff") int mystcraft$getWriteRadiusCutoff();
}
