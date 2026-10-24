package ports.gridwright.machine;

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

/** The screen of a generator or the resonator: an input slot, an output slot and the player inventory. */
public class MachineMenu extends AbstractContainerMenu {

    private static final int MACHINE_SLOTS = 2;

    private final Container container;
    private final Container rules;
    private final ContainerData data;
    private final MachineLayout layout;

    public MachineMenu(int containerId, Inventory inventory, Container container, ContainerData data, MachineLayout layout) {
        this(containerId, inventory, container, container, data, layout);
    }

    /** {@code rules} decides what the input slot accepts; on the client it is the client's block entity. */
    private MachineMenu(int containerId, Inventory inventory, Container container, Container rules, ContainerData data,
                        MachineLayout layout) {
        super(ModMenus.MACHINE.get(), containerId);
        checkContainerSize(container, MACHINE_SLOTS);
        checkContainerDataCount(data, layout.dataCount());
        this.container = container;
        this.rules = rules;
        this.data = data;
        this.layout = layout;
        addSlot(new Slot(container, 0, MachineLayout.INPUT_X, MachineLayout.SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return rules.canPlaceItem(0, stack);
            }
        });
        addSlot(new Slot(container, 1, MachineLayout.OUTPUT_X, MachineLayout.SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, column, 8 + column * 18, 142));
        addDataSlots(data);
    }

    /** The client side: the machine's layout comes from the block entity at the opening position. */
    public static MachineMenu client(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        MachineBlockEntity machine = inventory.player.level.getBlockEntity(pos) instanceof MachineBlockEntity found ? found : null;
        MachineLayout layout = machine != null ? machine.layout() : MachineLayout.GENERATOR;
        SimpleContainer slots = new SimpleContainer(MACHINE_SLOTS);
        return new MachineMenu(containerId, inventory, slots, machine != null ? machine : slots,
                new SimpleContainerData(layout.dataCount()), layout);
    }

    public MachineLayout layout() {
        return layout;
    }

    /** A synchronised value; slots carry 16 bits each, so wider values span two slots. */
    public int get(int index) {
        return data.get(index) & 0xFFFF;
    }

    public long getWide(int lowIndex) {
        return (long) get(lowIndex) | ((long) get(lowIndex + 1) << 16);
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
        if (index < MACHINE_SLOTS) {
            if (!moveItemStackTo(stack, MACHINE_SLOTS, slots.size(), true)) return ItemStack.EMPTY;
        } else if (rules.canPlaceItem(0, stack)) {
            if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
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
