package net.mystcraft.fabric.mixin;

import net.minecraft.world.entity.npc.Villager;
import net.mystcraft.minecraft.exploration.ArchivistCareer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Villager.class)
abstract class ArchivistCareerMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void mystcraft$initializePermanentCareer(CallbackInfo callback) {
        ArchivistCareer.initialize((Villager)(Object)this);
    }
}
