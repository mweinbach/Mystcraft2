package com.technovision.advancedgenetics.mixin;

import com.technovision.advancedgenetics.api.genetics.Genes;
import com.technovision.advancedgenetics.registry.ComponentRegistry;
import com.technovision.advancedgenetics.util.SpiderClimbUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    /**
     * Lets players with the "Climb Walls" gene climb any solid wall like a spider.
     */
    @Inject(method = "onClimbable", at = @At("RETURN"), cancellable = true)
    private void advancedgenetics$climbWalls(CallbackInfoReturnable<Boolean> info) {
        if (info.getReturnValue() || !((Object) this instanceof Player player) || player.isSpectator()) return;
        if (!ComponentRegistry.getGenetics(player).hasGene(Genes.CLIMB_WALLS)) return;

        BlockPos blockPos = player.blockPosition();
        BlockState blockBelowPlayer = player.level.getBlockState(blockPos.below());
        boolean climbing;
        if (blockBelowPlayer.getBlock() != Blocks.AIR
                && !blockBelowPlayer.is(BlockTags.REPLACEABLE_PLANTS)
                && !blockBelowPlayer.is(BlockTags.FLOWERS)) {
            climbing = SpiderClimbUtil.canStartClimb(player, blockPos);
        } else {
            climbing = SpiderClimbUtil.canContinueClimb(player, blockPos);
        }
        if (climbing) {
            ((LivingEntityAccessor) player).advancedgenetics$setLastClimbablePos(Optional.of(blockPos));
            info.setReturnValue(true);
        }
    }
}
