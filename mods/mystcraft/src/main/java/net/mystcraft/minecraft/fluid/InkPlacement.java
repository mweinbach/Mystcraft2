package net.mystcraft.minecraft.fluid;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.mystcraft.integration.registry.InkRegistry;
/** Ink is water-like for swimming, but the original custom MaterialLiquid is not vanilla water that evaporates. */
public final class InkPlacement {
    public static boolean inWarmDimension(Player player,Level level,BlockPos pos,BlockHitResult hit){
        var state=level.getBlockState(pos);var ink=InkRegistry.BLACK_INK.get();boolean replace=state.canBeReplaced(ink);
        if(!(state.isAir()||replace||state.getBlock() instanceof LiquidBlockContainer container&&container.canPlaceLiquid(level,pos,state,ink)))return hit!=null&&inWarmDimension(player,level,hit.getBlockPos().relative(hit.getDirection()),null);
        if(state.getBlock() instanceof LiquidBlockContainer container&&container.canPlaceLiquid(level,pos,state,ink)){
            if(!container.placeLiquid(level,pos,state,ink.defaultFluidState()))return false;
        }else{
            if(!level.isClientSide&&replace&&!state.getMaterial().isLiquid())level.destroyBlock(pos,true);
            if(!level.setBlock(pos,ink.defaultFluidState().createLegacyBlock(),11)&&!state.getFluidState().isSource())return false;
        }
        level.playSound(player,pos,SoundEvents.BUCKET_EMPTY,SoundSource.BLOCKS,1F,1F);level.gameEvent(player,GameEvent.FLUID_PLACE,pos);return true;
    }
    private InkPlacement(){}
}
