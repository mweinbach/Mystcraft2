package ports.buildcraft.builders;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import ports.buildcraft.lib.BCMenu;

/**
 * Upstream {@code ContainerFiller}: the 27 resource slots and the player inventory, laid out as
 * {@code filler.json} draws them. Pattern, parameter and toggle changes arrive as menu button clicks.
 */
public class FillerMenu extends BCMenu {
    @Nullable private final FillerBlockEntity filler;

    public FillerMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, inventory, buffer.readBlockPos(), new SimpleContainer(FillerBlockEntity.RESOURCE_SLOTS), null);
    }

    public FillerMenu(int id, Inventory inventory, FillerBlockEntity filler) {
        this(id, inventory, filler.getBlockPos(), filler, filler);
    }

    private FillerMenu(int id, Inventory inventory, BlockPos pos, Container resources, @Nullable FillerBlockEntity filler) {
        super(BCBuilders.FILLER_MENU.get(), id, pos);
        this.filler = filler;
        addFullPlayerInventory(inventory, 153);
        for (int sy = 0; sy < 3; sy++) {
            for (int sx = 0; sx < 9; sx++) {
                addSlot(new Slot(resources, sx + sy * 9, 8 + sx * 18, 85 + sy * 18) {
                    @Override public boolean mayPlace(ItemStack stack) { return stack.getItem() instanceof BlockItem; }
                });
            }
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        return filler != null && filler.handleButton(id);
    }
}
