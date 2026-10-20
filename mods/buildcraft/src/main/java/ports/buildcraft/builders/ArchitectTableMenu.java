package ports.buildcraft.builders;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.lib.BCMenu;

/** Upstream {@code ContainerArchitectTable}: the snapshot slots and the player inventory, plus the name message. */
public class ArchitectTableMenu extends BCMenu {
    public static final ResourceLocation NAME_CHANNEL = BuildCraft.id("architect_name");
    /** Upstream's name field kept {@code GuiTextField}'s default limit. */
    public static final int MAX_NAME_LENGTH = 32;
    @Nullable private final ArchitectTableBlockEntity table;

    public ArchitectTableMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, inventory, buffer.readBlockPos(), new SimpleContainer(2), null);
    }

    public ArchitectTableMenu(int id, Inventory inventory, ArchitectTableBlockEntity table) {
        this(id, inventory, table.getBlockPos(), table, table);
    }

    private ArchitectTableMenu(int id, Inventory inventory, BlockPos pos, Container container, @Nullable ArchitectTableBlockEntity table) {
        super(BCBuilders.ARCHITECT_TABLE_MENU.get(), id, pos);
        this.table = table;
        addFullPlayerInventory(inventory, 88, 84);
        addSlot(new Slot(container, ArchitectTableBlockEntity.SLOT_IN, 135, 35) {
            @Override public boolean mayPlace(ItemStack stack) { return stack.getItem() instanceof SnapshotItem; }
        });
        addSlot(new Slot(container, ArchitectTableBlockEntity.SLOT_OUT, 194, 35) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
        });
    }

    /** Upstream {@code readMessage(ID_NAME)}: renames the table whose GUI the sender has open. */
    public static void receiveName(ServerPlayer player, FriendlyByteBuf buffer) {
        String name = buffer.readUtf(MAX_NAME_LENGTH);
        if (player.containerMenu instanceof ArchitectTableMenu menu && menu.table != null && menu.stillValid(player)) {
            menu.table.setName(name);
        }
    }
}
