package ports.jurassicraft.content;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;
import ports.jurassicraft.egg.FossilNestRegistry;

/** Wrap a fossil deposit before mining it, preserving its species. */
public final class PlasterItem extends Item {
    public PlasterItem(Properties properties) { super(properties); }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var nestResult = FossilNestRegistry.tryWrap(context);
        if (nestResult != InteractionResult.PASS) return nestResult;
        var level = context.getLevel();
        var player = context.getPlayer();
        BlockState state = level.getBlockState(context.getClickedPos());
        if (!(state.getBlock() instanceof FossilBlock fossil) || player == null
                || !player.mayUseItemAt(context.getClickedPos().relative(context.getClickedFace()),
                        context.getClickedFace(), context.getItemInHand())) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && level.setBlock(context.getClickedPos(),
                JurassicContent.ENCASED_FOSSILS.get(fossil.species()).get().defaultBlockState(), 3)
                && !player.getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
