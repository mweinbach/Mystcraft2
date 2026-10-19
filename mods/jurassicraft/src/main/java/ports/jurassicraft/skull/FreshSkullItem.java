package ports.jurassicraft.skull;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import ports.jurassicraft.assembly.FreshBoneItem;
import ports.jurassicraft.content.Species;

/** Retains fresh-bone genetics and grinder behavior while adding skull placement. */
public final class FreshSkullItem extends FreshBoneItem {
    public FreshSkullItem(Properties properties) { super(Species.TYRANNOSAURUS,properties); }
    @Override public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        SkullPlacement.appendTooltip(stack, tooltip);
    }
    @Override public InteractionResult useOn(UseOnContext context) { return SkullPlacement.place(context); }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand) { return SkullPlacement.toggle(level,player,hand); }
}
