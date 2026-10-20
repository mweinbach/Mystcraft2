package ports.buildcraft.builders;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import ports.buildcraft.lib.BCMenu;
import ports.buildcraft.lib.GuiTank;

/**
 * Upstream {@code ContainerBuilder}: the snapshot slot, the 27 resource slots and the player inventory, with
 * the four tank gauges. The blueprint's remaining needs are drawn by the screen from the synced builder.
 */
public class BuilderMenu extends BCMenu {
    public static final int SNAPSHOT_X = 80, SNAPSHOT_Y = 27;
    public static final int RESOURCES_X = 8, RESOURCES_Y = 72;
    public static final int INVENTORY_Y = 140;
    private final ContainerData data;

    public BuilderMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, inventory, buffer.readBlockPos(), new SimpleContainer(1 + BuilderBlockEntity.RESOURCE_SLOTS),
                new SimpleContainerData(BuilderBlockEntity.TANKS * 2), null);
    }

    public BuilderMenu(int id, Inventory inventory, BuilderBlockEntity builder) {
        this(id, inventory, builder.getBlockPos(), builder, builder.data, builder);
    }

    private BuilderMenu(int id, Inventory inventory, BlockPos pos, Container container, ContainerData data, @Nullable BuilderBlockEntity builder) {
        super(BCBuilders.BUILDER_MENU.get(), id, pos);
        this.data = data;
        addFullPlayerInventory(inventory, INVENTORY_Y);
        addSlot(new Slot(container, BuilderBlockEntity.SLOT_SNAPSHOT, SNAPSHOT_X, SNAPSHOT_Y) {
            @Override public boolean mayPlace(ItemStack stack) { return stack.getItem() instanceof SnapshotItem snapshotItem && snapshotItem.used; }
            @Override public int getMaxStackSize() { return 1; }
        });
        for (int sy = 0; sy < 3; sy++) {
            for (int sx = 0; sx < 9; sx++) {
                addSlot(new Slot(container, 1 + sx + sy * 9, RESOURCES_X + sx * 18, RESOURCES_Y + sy * 18));
            }
        }
        addDataSlots(data);
        if (builder != null) {
            List<GuiTank> tanks = builder.getTanks().stream().map(tank -> new GuiTank(tank, builder::tanksChanged)).toList();
            setGuiTanks(tanks, List.of());
        }
    }

    public Fluid tankFluid(int tank) {
        return Registry.FLUID.byId(data.get(tank * 2));
    }

    public int tankAmount(int tank) {
        return data.get(tank * 2 + 1);
    }
}
