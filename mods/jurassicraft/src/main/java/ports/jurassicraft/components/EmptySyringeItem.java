package ports.jurassicraft.components;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.BushBlock;

/** Collects plant cells by using the syringe on a bush or other small plant. */
public final class EmptySyringeItem extends Item {
    public EmptySyringeItem(Properties properties) { super(properties); }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var player = context.getPlayer();
        if (player == null || !(context.getLevel().getBlockState(context.getClickedPos()).getBlock() instanceof BushBlock))
            return InteractionResult.PASS;
        if (!context.getLevel().isClientSide) {
            ItemStack filled = ItemUtils.createFilledResult(context.getItemInHand(), player,
                    new ItemStack(ComponentsRegistry.PLANT_CELLS.get()));
            player.setItemInHand(context.getHand(), filled);
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }
}
