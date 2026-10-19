package ports.jurassicraft.darts;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import java.util.List;
import javax.annotation.Nullable;

public final class DartItem extends Item {
    public enum Kind { TRANQUILIZER, POISON, LETHAL, POTION }
    private final Kind kind;
    public DartItem(Kind kind, Properties properties) { super(properties); this.kind = kind; }
    public Kind kind() { return kind; }
    @Override public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        if (kind == Kind.POTION) PotionUtils.addPotionTooltip(stack, tooltip, 1.0F);
    }
}
