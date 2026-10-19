package ports.jurassicraft.skull;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** Fossilized Tyrannosaurus skull, using the normal item interaction order. */
public final class SkullItem extends Item {
    public SkullItem(Properties properties) { super(properties); }
    @Override public void appendHoverText(ItemStack stack, @javax.annotation.Nullable Level level, java.util.List<net.minecraft.network.chat.Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        SkullPlacement.appendTooltip(stack, tooltip);
    }
    @Override public InteractionResult useOn(UseOnContext context) { return SkullPlacement.place(context); }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand) { return SkullPlacement.toggle(level,player,hand); }
}
