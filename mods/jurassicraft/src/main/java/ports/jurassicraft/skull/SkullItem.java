package ports.jurassicraft.skull;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** Fossilized Tyrannosaurus skull, using the normal item interaction order. */
public final class SkullItem extends Item {
    public SkullItem(Properties properties) { super(properties); }
    @Override public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        SkullPlacement.appendTooltip(stack, tooltip);
    }
    @Override public InteractionResult useOn(UseOnContext context) { return SkullPlacement.place(context); }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand) { return SkullPlacement.toggle(level,player,hand); }
}
