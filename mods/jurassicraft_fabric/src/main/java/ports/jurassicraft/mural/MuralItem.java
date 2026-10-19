package ports.jurassicraft.mural;

import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import ports.jurassicraft.content.JurassicContent;

public final class MuralItem extends Item {
    public MuralItem() { super(new Item.Properties().tab(JurassicContent.TAB)); }
    @Override public InteractionResult useOn(UseOnContext context) {
        Direction side = context.getClickedFace(); Player player = context.getPlayer();
        if (!side.getAxis().isHorizontal() || player == null || player.isSpectator()) return InteractionResult.PASS;
        var pos = context.getClickedPos().relative(side);
        if (!player.mayUseItemAt(pos, side, context.getItemInHand())) return InteractionResult.FAIL;
        var mural = MuralEntity.create(context.getLevel(), pos, side);
        if (mural.isEmpty()) return InteractionResult.PASS;
        if (!context.getLevel().isClientSide) {
            if (!context.getLevel().addFreshEntity(mural.get())) return InteractionResult.FAIL;
            if (!player.getAbilities().instabuild) context.getItemInHand().shrink(1);
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }
}
