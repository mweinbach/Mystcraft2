package com.technovision.advancedgenetics.mixin.client;

import com.technovision.advancedgenetics.api.genetics.Genes;
import com.technovision.advancedgenetics.registry.ComponentRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class MinecraftMixin {

    /**
     * Makes all living entities glow for a player if they have the "Mob Sight" gene.
     */
    @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
    private void advancedgenetics$mobSight(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        LocalPlayer player = ((Minecraft) (Object) this).player;
        if (player != null && player != entity && entity instanceof LivingEntity) {
            if (ComponentRegistry.getGenetics(player).hasGene(Genes.MOB_SIGHT)) {
                cir.setReturnValue(true);
            }
        }
    }
}
