package ports.buildcraft.builders;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import ports.buildcraft.lib.BCMenu;

/** Upstream {@code ContainerReplacer}: the blueprint slot, the two schematic slots and the player inventory. */
public class ReplacerMenu extends BCMenu {
    public static final int INVENTORY_Y = 159;
    private final Container container;

    public ReplacerMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, inventory, buffer.readBlockPos(), new SimpleContainer(3));
    }

    public ReplacerMenu(int id, Inventory inventory, ReplacerBlockEntity replacer) {
        this(id, inventory, replacer.getBlockPos(), replacer);
    }

    private ReplacerMenu(int id, Inventory inventory, BlockPos pos, Container container) {
        super(BCBuilders.REPLACER_MENU.get(), id, pos);
        this.container = container;
        addFullPlayerInventory(inventory, INVENTORY_Y);
        addSlot(new ReplacerSlot(container, ReplacerBlockEntity.SLOT_SNAPSHOT, 8, 115));
        addSlot(new ReplacerSlot(container, ReplacerBlockEntity.SLOT_FROM, 8, 137));
        addSlot(new ReplacerSlot(container, ReplacerBlockEntity.SLOT_TO, 56, 137));
    }

    /** The recorded blueprint, as synced to the client for the preview. */
    public ItemStack snapshot() {
        return container.getItem(ReplacerBlockEntity.SLOT_SNAPSHOT);
    }

    private static final class ReplacerSlot extends Slot {
        ReplacerSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return ReplacerBlockEntity.accepts(getContainerSlot(), stack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}
