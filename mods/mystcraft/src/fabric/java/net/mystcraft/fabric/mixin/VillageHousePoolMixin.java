package net.mystcraft.fabric.mixin;

import java.util.List;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.mystcraft.minecraft.exploration.VillageArchivistPools;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(StructureTemplatePool.class)
public abstract class VillageHousePoolMixin {
    @Inject(method = "getShuffledTemplates", at = @At("RETURN"), cancellable = true)
    private void mystcraft$archivistHouse(RandomSource random, CallbackInfoReturnable<List<StructurePoolElement>> callback) {
        callback.setReturnValue(VillageArchivistPools.expand((StructureTemplatePool) (Object) this, callback.getReturnValue(), random));
    }
    @Inject(method = "getMaxSize", at = @At("RETURN"), cancellable = true)
    private void mystcraft$houseBounds(StructureTemplateManager templates, CallbackInfoReturnable<Integer> callback) {
        if (VillageArchivistPools.houses((StructureTemplatePool) (Object) this)) callback.setReturnValue(Math.max(12, callback.getReturnValue()));
    }
}
