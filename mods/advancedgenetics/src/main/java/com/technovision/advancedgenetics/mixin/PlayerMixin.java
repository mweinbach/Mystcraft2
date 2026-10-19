package com.technovision.advancedgenetics.mixin;

import com.technovision.advancedgenetics.api.genetics.Genes;
import com.technovision.advancedgenetics.registry.ComponentRegistry;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public class PlayerMixin {

    /**
     * Keep inventory if player has "Keep Inventory" gene
     */
    @Inject(method = "dropEquipment", at = @At("HEAD"), cancellable = true)
    private void advancedgenetics$keepInventory(CallbackInfo ci) {
        Player player = (Player) (Object) this;
        if (ComponentRegistry.getGenetics(player).hasGene(Genes.KEEP_INVENTORY)) {
            ci.cancel();
        }
    }

    /**
     * Allow walking through cobwebs if player has "Web Walking" gene
     */
    @Inject(method = "makeStuckInBlock", at = @At("HEAD"), cancellable = true)
    private void advancedgenetics$webWalking(BlockState state, Vec3 multiplier, CallbackInfo ci) {
        Player player = (Player) (Object) this;
        if (ComponentRegistry.getGenetics(player).hasGene(Genes.WEB_WALKING)) {
            ci.cancel();
        }
    }
}
