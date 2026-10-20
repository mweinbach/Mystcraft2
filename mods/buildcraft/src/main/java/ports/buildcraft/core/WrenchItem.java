package ports.buildcraft.core;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Upstream {@code ItemWrench_Neptune}. Most blocks read the wrench themselves when used; blocks that
 * use clicks for something else implement {@link CustomRotationHandler}, which the wrench calls when
 * the block did not take the click. It is also added to the {@code c:wrenches} and
 * {@code forge:tools/wrench} tags.
 */
public class WrenchItem extends Item {
    public WrenchItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof CustomRotationHandler handler)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        InteractionResult result = handler.attemptRotation(level, pos, state, context.getClickedFace());
        playSlideSound(level, pos, state, result);
        return result;
    }

    /** Upstream {@code SoundUtil.playSlideSound}. */
    private static void playSlideSound(Level level, BlockPos pos, BlockState state, InteractionResult result) {
        if (result == InteractionResult.PASS) return;
        SoundType sound = state.getSoundType();
        level.playSound(null, pos, result.consumesAction() ? SoundEvents.PISTON_CONTRACT : SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS,
                (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
    }
}
