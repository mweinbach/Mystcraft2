package ports.gridwright.worker;

import ports.gridwright.registry.ModMenus;
import net.minecraft.core.BlockPos;
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

/** The screen of a quarry or mechanical block: its {@link WorkerLayout}'s slots and the player inventory. */
public class WorkerMenu extends AbstractContainerMenu {

    private final Container container;
    private final Container rules;
    private final ContainerData data;
    private final WorkerLayout layout;

    /** {@code rules} decides what each slot accepts; on the client it is the client's block entity. */
    public WorkerMenu(int containerId, Inventory inventory, Container container, Container rules, ContainerData data,
                      WorkerLayout layout) {
        super(ModMenus.WORKER.get(), containerId);
        checkContainerSize(container, layout.size());
        checkContainerDataCount(data, WorkerBlockEntity.DATA_COUNT);
        this.container = container;
        this.rules = rules;
        this.data = data;
        this.layout = layout;
        for (int i = 0; i < layout.size(); i++) {
            int index = i;
            WorkerLayout.Slot slot = layout.slot(i);
            addSlot(new Slot(container, i, slot.x(), slot.y()) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return rules.canPlaceItem(index, stack);
                }
            });
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, WorkerLayout.INVENTORY_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, column, 8 + column * 18, WorkerLayout.INVENTORY_Y + 58));
        addDataSlots(data);
    }

    /** The client side: the layout comes from the block entity at the opening position. */
    public static WorkerMenu client(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        WorkerBlockEntity worker = inventory.player.level.getBlockEntity(pos) instanceof WorkerBlockEntity found ? found : null;
        WorkerLayout layout = worker != null ? worker.layout() : WorkerLayout.QUANTUM_QUARRY;
        SimpleContainer slots = new SimpleContainer(layout.size());
        return new WorkerMenu(containerId, inventory, slots, worker != null ? worker : slots,
                new SimpleContainerData(WorkerBlockEntity.DATA_COUNT), layout);
    }

    public WorkerLayout layout() {
        return layout;
    }

    /** A synchronised value; slots carry 16 bits each, so wider values span two slots. */
    public int get(int index) {
        return data.get(index) & 0xFFFF;
    }

    public long getWide(int lowIndex) {
        return (long) get(lowIndex) | ((long) get(lowIndex + 1) << 16);
    }

    public WorkerBlockEntity.Status status() {
        WorkerBlockEntity.Status[] values = WorkerBlockEntity.Status.values();
        int index = get(0);
        return index < values.length ? values[index] : WorkerBlockEntity.Status.IDLE;
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int machineSlots = layout.size();
        if (index < machineSlots) {
            if (!moveItemStackTo(stack, machineSlots, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            boolean moved = false;
            for (int target = 0; target < layout.firstOutput() && !stack.isEmpty(); target++) {
                if (rules.canPlaceItem(target, stack)) moved |= moveItemStackTo(stack, target, target + 1, false);
            }
            if (!moved) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return original;
    }
}
