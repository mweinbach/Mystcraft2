package ports.gridwright.transfer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** A configurable filter: use it in hand to edit it, then put it in a node's filter slot. */
public class FilterItem extends Item {

    /** The inventory index of the off hand. */
    public static final int OFFHAND_SLOT = 40;

    public FilterItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            int slot = hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : OFFHAND_SLOT;
            TransferPlatform.openMenu(serverPlayer, new SimpleMenuProvider(
                    (id, inventory, opener) -> new FilterMenu(id, inventory, slot), stack.getHoverName()),
                    buffer -> buffer.writeVarInt(slot));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        ItemFilter filter = ItemFilter.of(stack);
        tooltip.add(Component.translatable(filter.blacklist() ? "tooltip.gridwright.filter.blacklist" : "tooltip.gridwright.filter.whitelist",
                filter.size()).withStyle(ChatFormatting.GRAY));
        if (filter.matchNbt()) tooltip.add(Component.translatable("tooltip.gridwright.filter.nbt").withStyle(ChatFormatting.GRAY));
    }

    /** The filter in {@code inventory} at {@code slot}, or empty if the slot no longer holds one. */
    static ItemStack held(Inventory inventory, int slot) {
        ItemStack stack = slot >= 0 && slot < inventory.getContainerSize() ? inventory.getItem(slot) : ItemStack.EMPTY;
        return stack.getItem() instanceof FilterItem ? stack : ItemStack.EMPTY;
    }
}
