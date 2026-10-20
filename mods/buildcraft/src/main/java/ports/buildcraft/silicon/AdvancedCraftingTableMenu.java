package ports.buildcraft.silicon;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import ports.buildcraft.lib.DisplayContainer;

/**
 * Upstream {@code ContainerAdvancedCraftingTable}: the blueprint's result on display, the materials, the results
 * and the phantom blueprint grid.
 */
public class AdvancedCraftingTableMenu extends LaserTableMenu {
    public static final int INVENTORY_Y = 153;

    public AdvancedCraftingTableMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, inventory, buffer.readBlockPos(), new SimpleContainer(AdvancedCraftingTableBlockEntity.SLOTS), new SimpleContainer(1), null);
    }

    public AdvancedCraftingTableMenu(int id, Inventory inventory, AdvancedCraftingTableBlockEntity table) {
        this(id, inventory, table.getBlockPos(), table, new DisplayContainer(1, i -> table.assumedResult()), table);
    }

    private AdvancedCraftingTableMenu(int id, Inventory inventory, BlockPos pos, Container container, Container display,
            @Nullable AdvancedCraftingTableBlockEntity table) {
        super(BCSilicon.ADVANCED_CRAFTING_TABLE_MENU.get(), id, pos, table);
        addFullPlayerInventory(inventory, INVENTORY_Y);
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 5; x++) {
                addSlot(new Slot(container, AdvancedCraftingTableBlockEntity.MATERIALS_START + x + y * 5, 15 + x * 18, 85 + y * 18));
            }
        }
        machineSlotEnd = slots.size();
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                addSlot(new OutputSlot(container, AdvancedCraftingTableBlockEntity.RESULTS_START + x + y * 3, 109 + x * 18, 85 + y * 18));
            }
        }
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                addSlot(new PhantomSlot(container, AdvancedCraftingTableBlockEntity.BLUEPRINT_START + x + y * 3, 33 + x * 18, 16 + y * 18));
            }
        }
        addSlot(new DisplaySlot(display, 0, 127, 33));
    }
}
