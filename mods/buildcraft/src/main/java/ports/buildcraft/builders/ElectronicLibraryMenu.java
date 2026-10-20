package ports.buildcraft.builders;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import ports.buildcraft.lib.BCMenu;

/** Upstream {@code ContainerElectronicLibrary}: the two copy rows and the player inventory. */
public class ElectronicLibraryMenu extends BCMenu {
    public static final int INVENTORY_Y = 138;
    @Nullable private final ElectronicLibraryBlockEntity library;

    public ElectronicLibraryMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, inventory, buffer.readBlockPos(), new SimpleContainer(4), null);
    }

    public ElectronicLibraryMenu(int id, Inventory inventory, ElectronicLibraryBlockEntity library) {
        this(id, inventory, library.getBlockPos(), library, library);
    }

    private ElectronicLibraryMenu(int id, Inventory inventory, BlockPos pos, Container container, @Nullable ElectronicLibraryBlockEntity library) {
        super(BCBuilders.ELECTRONIC_LIBRARY_MENU.get(), id, pos);
        this.library = library;
        addFullPlayerInventory(inventory, INVENTORY_Y);
        addSlot(new LibrarySlot(container, ElectronicLibraryBlockEntity.SLOT_DOWN_OUT, 175, 57));
        addSlot(new LibrarySlot(container, ElectronicLibraryBlockEntity.SLOT_DOWN_IN, 219, 57));
        addSlot(new LibrarySlot(container, ElectronicLibraryBlockEntity.SLOT_UP_IN, 175, 79));
        addSlot(new LibrarySlot(container, ElectronicLibraryBlockEntity.SLOT_UP_OUT, 219, 79));
    }

    /** Server side only. */
    @Nullable
    public ElectronicLibraryBlockEntity library() {
        return library;
    }

    private static final class LibrarySlot extends Slot {
        LibrarySlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return ElectronicLibraryBlockEntity.accepts(getContainerSlot(), stack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}
