package ports.gridwright.transfer;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/** A node's screen: four upgrade slots, a filter slot and the player inventory. */
public class NodeMenu extends AbstractContainerMenu {

    public static final int SLOT_Y = 22;
    public static final int UPGRADE_X = 26;
    public static final int FILTER_X = 134;

    private final ContainerData data;
    private final Predicate<Player> valid;

    public NodeMenu(int containerId, Inventory inventory, Container node, ContainerData data, Predicate<Player> valid) {
        super(TransferRegistry.NODE_MENU.get(), containerId);
        checkContainerSize(node, NodeBlockEntity.SLOTS);
        checkContainerDataCount(data, NodeBlockEntity.DATA_COUNT);
        this.data = data;
        this.valid = valid;
        for (int slot = 0; slot < NodeBlockEntity.UPGRADE_SLOTS; slot++) addSlot(rule(node, slot, UPGRADE_X + slot * 18));
        addSlot(rule(node, NodeBlockEntity.FILTER_SLOT, FILTER_X));
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, column, 8 + column * 18, 142));
        addDataSlots(data);
    }

    public static NodeMenu client(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        buffer.readBlockPos();
        return new NodeMenu(containerId, inventory, new SimpleContainer(NodeBlockEntity.SLOTS),
                new SimpleContainerData(NodeBlockEntity.DATA_COUNT), player -> true);
    }

    private static Slot rule(Container node, int nodeSlot, int x) {
        return new Slot(node, nodeSlot, x, SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return NodeBlockEntity.accepts(getContainerSlot(), stack);
            }

            @Override
            public int getMaxStackSize() {
                return getContainerSlot() == NodeBlockEntity.FILTER_SLOT ? 1 : 64;
            }
        };
    }

    /** Grid Power the upgrades ask for. */
    public double cost() {
        return (data.get(0) & 0xFFFF) / 10.0;
    }

    public NodeBlockEntity.Power power() {
        NodeBlockEntity.Power[] values = NodeBlockEntity.Power.values();
        int index = data.get(1);
        return index >= 0 && index < values.length ? values[index] : NodeBlockEntity.Power.NONE;
    }

    /** Inventories or tanks the node's pipes reached when it last looked. */
    public int targets() {
        return data.get(2) & 0xFFFF;
    }

    @Override
    public boolean stillValid(Player player) {
        return valid.test(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < NodeBlockEntity.SLOTS) {
            if (!moveItemStackTo(stack, NodeBlockEntity.SLOTS, slots.size(), true)) return ItemStack.EMPTY;
        } else if (NodeBlockEntity.accepts(NodeBlockEntity.FILTER_SLOT, stack)) {
            if (!moveItemStackTo(stack, NodeBlockEntity.FILTER_SLOT, NodeBlockEntity.SLOTS, false)) return ItemStack.EMPTY;
        } else if (NodeBlockEntity.accepts(0, stack)) {
            if (!moveItemStackTo(stack, 0, NodeBlockEntity.UPGRADE_SLOTS, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return original;
    }
}
