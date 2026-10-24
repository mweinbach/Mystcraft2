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

    /** The menu button that switches the node's redstone mode. */
    public static final int BUTTON_REDSTONE = 0;

    private final ContainerData data;
    private final Predicate<Player> valid;
    private final Runnable cycleRedstone;

    public NodeMenu(int containerId, Inventory inventory, Container node, ContainerData data, Predicate<Player> valid) {
        this(containerId, inventory, node, data, valid, () -> { });
    }

    /** {@code cycleRedstone} runs on the server when the screen's redstone button is pressed. */
    public NodeMenu(int containerId, Inventory inventory, Container node, ContainerData data, Predicate<Player> valid,
                    Runnable cycleRedstone) {
        super(TransferRegistry.NODE_MENU.get(), containerId);
        checkContainerSize(node, NodeBlockEntity.SLOTS);
        checkContainerDataCount(data, NodeBlockEntity.DATA_COUNT);
        this.data = data;
        this.valid = valid;
        this.cycleRedstone = cycleRedstone;
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

    public NodeBlockEntity.Redstone redstone() {
        NodeBlockEntity.Redstone[] values = NodeBlockEntity.Redstone.values();
        int index = data.get(3);
        return index >= 0 && index < values.length ? values[index] : NodeBlockEntity.Redstone.IGNORED;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != BUTTON_REDSTONE) return false;
        cycleRedstone.run();
        return true;
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
