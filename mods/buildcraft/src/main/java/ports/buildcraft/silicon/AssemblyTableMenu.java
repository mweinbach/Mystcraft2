package ports.buildcraft.silicon;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import ports.buildcraft.lib.DisplayContainer;

/**
 * Upstream {@code ContainerAssemblyTable}: the twelve ingredient slots and twelve display slots listing the
 * recipes they allow, each with its state. Left-clicking a listed recipe saves or unsaves it.
 */
public class AssemblyTableMenu extends LaserTableMenu {
    public static final int INVENTORY_Y = 123;
    private final ContainerData states;

    public AssemblyTableMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, inventory, buffer.readBlockPos(), new SimpleContainer(AssemblyTableBlockEntity.SLOTS),
                new SimpleContainer(AssemblyTableBlockEntity.SLOTS), new SimpleContainerData(AssemblyTableBlockEntity.SLOTS), null);
    }

    public AssemblyTableMenu(int id, Inventory inventory, AssemblyTableBlockEntity table) {
        this(id, inventory, table.getBlockPos(), table, new DisplayContainer(AssemblyTableBlockEntity.SLOTS, i -> instruction(table, i)),
                stateData(table), table);
    }

    private AssemblyTableMenu(int id, Inventory inventory, BlockPos pos, Container inputs, Container display, ContainerData states,
            @Nullable AssemblyTableBlockEntity table) {
        super(BCSilicon.ASSEMBLY_TABLE_MENU.get(), id, pos, table);
        this.states = states;
        addFullPlayerInventory(inventory, INVENTORY_Y);
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 3; x++) addSlot(new Slot(inputs, x + y * 3, 8 + x * 18, 36 + y * 18));
        }
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 3; x++) addSlot(new DisplaySlot(display, x + y * 3, 116 + x * 18, 36 + y * 18));
        }
        machineSlotEnd = machineSlotStart + AssemblyTableBlockEntity.SLOTS;
        addDataSlots(states);
    }

    private static ItemStack instruction(AssemblyTableBlockEntity table, int index) {
        List<AssemblyTableBlockEntity.Instruction> instructions = new ArrayList<>(table.recipesStates().keySet());
        return index < instructions.size() ? instructions.get(index).output : ItemStack.EMPTY;
    }

    private static ContainerData stateData(AssemblyTableBlockEntity table) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                List<EnumAssemblyRecipeState> values = new ArrayList<>(table.recipesStates().values());
                return index < values.size() ? values.get(index).ordinal() : -1;
            }

            @Override public void set(int index, int value) {}
            @Override public int getCount() { return AssemblyTableBlockEntity.SLOTS; }
        };
    }

    /** The state of the recipe listed in display slot {@code index}, or null where none is listed. */
    @Nullable
    public EnumAssemblyRecipeState state(int index) {
        int ordinal = states.get(index);
        EnumAssemblyRecipeState[] values = EnumAssemblyRecipeState.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
    }

    @Override
    protected void displayClicked(int index, int button, ClickType clickType, Player player) {
        if (button == 0 && clickType == ClickType.PICKUP && table instanceof AssemblyTableBlockEntity assembly) assembly.toggle(index);
    }
}
