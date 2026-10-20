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
 * Upstream {@code ContainerIntegrationTable}: the centre slot ringed by the eight to integrate, the current
 * recipe's output on display, and the result slot.
 */
public class IntegrationTableMenu extends LaserTableMenu {
    public static final int INVENTORY_Y = 109;
    private static final int[] RING = {0, 1, 2, 3, -1, 4, 5, 6, 7};

    public IntegrationTableMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, inventory, buffer.readBlockPos(), new SimpleContainer(IntegrationTableBlockEntity.SLOTS), new SimpleContainer(1), null);
    }

    public IntegrationTableMenu(int id, Inventory inventory, IntegrationTableBlockEntity table) {
        this(id, inventory, table.getBlockPos(), table, new DisplayContainer(1, i -> table.getOutput()), table);
    }

    private IntegrationTableMenu(int id, Inventory inventory, BlockPos pos, Container container, Container display,
            @Nullable IntegrationTableBlockEntity table) {
        super(BCSilicon.INTEGRATION_TABLE_MENU.get(), id, pos, table);
        addFullPlayerInventory(inventory, INVENTORY_Y);
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                int ring = RING[x + y * 3];
                int slot = ring < 0 ? IntegrationTableBlockEntity.TARGET_SLOT : IntegrationTableBlockEntity.TO_INTEGRATE_START + ring;
                addSlot(new Slot(container, slot, 19 + x * 25, 24 + y * 25));
            }
        }
        machineSlotEnd = slots.size();
        addSlot(new DisplaySlot(display, 0, 101, 36));
        addSlot(new OutputSlot(container, IntegrationTableBlockEntity.RESULT_SLOT, 138, 49));
    }
}
