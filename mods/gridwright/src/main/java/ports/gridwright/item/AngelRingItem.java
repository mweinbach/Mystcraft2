package ports.gridwright.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** Creative-style flight anywhere in the inventory, paid for in Grid Power; see {@link AngelRingFlight}. */
public class AngelRingItem extends Item {

    public AngelRingItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.gridwright.angel_ring", AngelRingFlight.formatGp()));
    }
}
