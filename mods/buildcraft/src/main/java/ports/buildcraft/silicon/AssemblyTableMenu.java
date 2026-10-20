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
 * recipes they allow, each with its state. Left-clicking a listed recipe saves or unsaves it. Upstream listed only
 * the first twelve recipes; here each viewer pages through the rest twelve at a time.
 */
public class AssemblyTableMenu extends LaserTableMenu {
    public static final int INVENTORY_Y = 123;
    public static final int BUTTON_PREVIOUS_PAGE = 0;
    public static final int BUTTON_NEXT_PAGE = 1;
    private static final int SLOTS = AssemblyTableBlockEntity.SLOTS;
    private static final int DATA_PAGE = SLOTS;
    private static final int DATA_PAGES = SLOTS + 1;
    private static final int DATA_COUNT = SLOTS + 2;
    private final ContainerData data;
    private int page;

    public AssemblyTableMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, inventory, buffer.readBlockPos(), new SimpleContainer(SLOTS), new SimpleContainerData(DATA_COUNT), null);
    }

    public AssemblyTableMenu(int id, Inventory inventory, AssemblyTableBlockEntity table) {
        this(id, inventory, table.getBlockPos(), table, null, table);
    }

    private AssemblyTableMenu(int id, Inventory inventory, BlockPos pos, Container inputs, @Nullable ContainerData clientData,
            @Nullable AssemblyTableBlockEntity table) {
        super(BCSilicon.ASSEMBLY_TABLE_MENU.get(), id, pos, table);
        data = table == null ? clientData : serverData(table);
        Container display = table == null ? new SimpleContainer(SLOTS) : new DisplayContainer(SLOTS, i -> listed(table, i).output);
        addFullPlayerInventory(inventory, INVENTORY_Y);
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 3; x++) addSlot(new Slot(inputs, x + y * 3, 8 + x * 18, 36 + y * 18));
        }
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 3; x++) addSlot(new DisplaySlot(display, x + y * 3, 116 + x * 18, 36 + y * 18));
        }
        machineSlotEnd = machineSlotStart + SLOTS;
        addDataSlots(data);
    }

    private static int pageCount(AssemblyTableBlockEntity table) {
        return Math.max(1, (table.recipesStates().size() + SLOTS - 1) / SLOTS);
    }

    private int currentPage(AssemblyTableBlockEntity table) {
        return Math.min(page, pageCount(table) - 1);
    }

    private record Listed(ItemStack output, int state) {
        static final Listed NONE = new Listed(ItemStack.EMPTY, -1);
    }

    private Listed listed(AssemblyTableBlockEntity table, int slot) {
        int index = currentPage(table) * SLOTS + slot;
        List<AssemblyTableBlockEntity.Instruction> instructions = new ArrayList<>(table.recipesStates().keySet());
        if (index >= instructions.size()) return Listed.NONE;
        AssemblyTableBlockEntity.Instruction instruction = instructions.get(index);
        return new Listed(instruction.output, table.recipesStates().get(instruction).ordinal());
    }

    private ContainerData serverData(AssemblyTableBlockEntity table) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                if (index == DATA_PAGE) return currentPage(table);
                if (index == DATA_PAGES) return pageCount(table);
                return listed(table, index).state();
            }

            @Override public void set(int index, int value) {}
            @Override public int getCount() { return DATA_COUNT; }
        };
    }

    /** The state of the recipe listed in display slot {@code index}, or null where none is listed. */
    @Nullable
    public EnumAssemblyRecipeState state(int index) {
        int ordinal = data.get(index);
        EnumAssemblyRecipeState[] values = EnumAssemblyRecipeState.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
    }

    /** The page of recipes shown, counting from zero. */
    public int page() {
        return data.get(DATA_PAGE);
    }

    public int pages() {
        return Math.max(1, data.get(DATA_PAGES));
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(table instanceof AssemblyTableBlockEntity assembly)) return false;
        int current = currentPage(assembly);
        if (id == BUTTON_PREVIOUS_PAGE) page = Math.max(0, current - 1);
        else if (id == BUTTON_NEXT_PAGE) page = Math.min(pageCount(assembly) - 1, current + 1);
        else return false;
        return true;
    }

    @Override
    protected void displayClicked(int index, int button, ClickType clickType, Player player) {
        if (button == 0 && clickType == ClickType.PICKUP && table instanceof AssemblyTableBlockEntity assembly) {
            assembly.toggle(currentPage(assembly) * SLOTS + index);
        }
    }
}
