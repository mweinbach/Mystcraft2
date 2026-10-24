package ports.gridwright.item;

import ports.gridwright.platform.Platform;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
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

/**
 * A portable double chest. Its contents live in the item. A bag can never go inside a bag (or a shulker
 * box or bundle), nor can shulker boxes go inside it, and the bag cannot be moved, swapped or dropped
 * while its own screen is open; see {@link BagMenu}.
 */
public class BagOfHoldingItem extends Item {

    public static final int ROWS = 6;
    public static final int SIZE = ROWS * 9;

    public BagOfHoldingItem(Properties properties) {
        super(properties);
    }

    /** Whether {@code stack} may be stored in a bag: anything that may go in a shulker box. */
    public static boolean canHold(ItemStack stack) {
        return stack.getItem().canFitInsideContainerItems();
    }

    @Override
    public boolean canFitInsideContainerItems() {
        return false;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            int slot = hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : Inventory.SLOT_OFFHAND;
            Platform.openMenu(serverPlayer, new SimpleMenuProvider((id, inventory, opener) -> BagMenu.server(id, inventory, slot),
                    stack.getHoverName()), buffer -> buffer.writeVarInt(slot));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.gridwright.bag_of_holding", storedStacks(stack), SIZE).withStyle(ChatFormatting.GRAY));
    }

    /** How many slots of the bag hold something. */
    public static int storedStacks(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : tag.getList("Items", Tag.TAG_COMPOUND).size();
    }
}
