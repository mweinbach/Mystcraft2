package ports.buildcraft.silicon.gate;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Upstream {@code ItemGateCopier}: holds one gate's configuration, copied from a gate and pasted onto others.
 * Sneak-using it clears it. Upstream's empty and full model variants are the {@code full} item property.
 */
public class GateCopierItem extends Item {
    private static final String NBT_DATA = "gate_data";

    public GateCopierItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Nullable
    public static CompoundTag getCopiedGateData(ItemStack stack) {
        return stack.getTagElement(NBT_DATA);
    }

    public static void setCopiedGateData(ItemStack stack, CompoundTag data) {
        stack.addTagElement(NBT_DATA, data);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !player.isShiftKeyDown() || getCopiedGateData(stack) == null) return InteractionResultHolder.pass(stack);
        stack.removeTagKey(NBT_DATA);
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        if (getCopiedGateData(stack) != null) tooltip.add(Component.translatable("buildcraft.item.nonclean.usage"));
    }
}
