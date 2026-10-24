package ports.gridwright.transfer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import ports.gridwright.grid.GridBlockEntity;

import javax.annotation.Nullable;
import java.util.List;

/** A node upgrade; see {@link UpgradeKind}. */
public class UpgradeItem extends Item {

    private final UpgradeKind kind;

    public UpgradeItem(UpgradeKind kind, Properties properties) {
        super(properties.stacksTo(kind.maxCounted() > 1 ? 64 : 1));
        this.kind = kind;
    }

    public UpgradeKind kind() {
        return kind;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.gridwright." + kind.id()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.gridwright.upgrade.cost", GridBlockEntity.format(kind.gp()),
                kind.maxCounted()).withStyle(ChatFormatting.DARK_AQUA));
    }
}
