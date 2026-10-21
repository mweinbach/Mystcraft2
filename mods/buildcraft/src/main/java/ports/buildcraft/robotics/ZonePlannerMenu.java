package ports.buildcraft.robotics;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import ports.buildcraft.core.MapLocationItem;
import ports.buildcraft.core.PaintbrushItem;
import ports.buildcraft.lib.BCMenu;
import ports.buildcraft.robotics.zone.ZonePlan;

/** Upstream {@code ContainerZonePlanner}: the brush grid, the import and export pairs, and the player inventory. */
public class ZonePlannerMenu extends BCMenu {
    @Nullable private final ZonePlannerBlockEntity planner;

    public ZonePlannerMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, inventory, buffer.readBlockPos(), new SimpleContainer(ZonePlannerBlockEntity.SLOTS), null);
    }

    public ZonePlannerMenu(int id, Inventory inventory, ZonePlannerBlockEntity planner) {
        this(id, inventory, planner.getBlockPos(), planner.inventory(), planner);
    }

    private ZonePlannerMenu(int id, Inventory inventory, BlockPos pos, Container container,
            @Nullable ZonePlannerBlockEntity planner) {
        super(BCRobotics.ZONE_PLANNER_MENU.get(), id, pos);
        this.planner = planner;
        addFullPlayerInventory(inventory, 88, 146);
        for (int x = 0; x < 4; x++) {
            for (int y = 0; y < 4; y++) {
                int slot = x * 4 + y;
                addSlot(new Slot(container, slot, 8 + x * 18, 146 + y * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) { return PaintbrushItem.isPaintbrush(stack); }
                });
            }
        }
        addSlot(brush(container, ZonePlannerBlockEntity.SLOT_INPUT_BRUSH, 8, 125));
        addSlot(zoneMap(container, ZonePlannerBlockEntity.SLOT_INPUT_MAP, 26, 125));
        addSlot(output(container, ZonePlannerBlockEntity.SLOT_INPUT_RESULT, 74, 125));
        addSlot(brush(container, ZonePlannerBlockEntity.SLOT_OUTPUT_BRUSH, 233, 9));
        addSlot(plainMap(container, ZonePlannerBlockEntity.SLOT_OUTPUT_MAP, 233, 27));
        addSlot(output(container, ZonePlannerBlockEntity.SLOT_OUTPUT_RESULT, 233, 75));
    }

    private static Slot brush(Container container, int index, int x, int y) {
        return new Slot(container, index, x, y) {
            @Override
            public boolean mayPlace(ItemStack stack) { return PaintbrushItem.isPaintbrush(stack); }
        };
    }

    /** Upstream's input map must be a single stack that already carries {@code chunkMapping}. */
    private static Slot zoneMap(Container container, int index, int x, int y) {
        return new Slot(container, index, x, y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof MapLocationItem && stack.getCount() == 1
                        && stack.getTag() != null && stack.getTag().contains("chunkMapping");
            }
        };
    }

    /** Upstream's output map is one map location, blank or recorded. */
    private static Slot plainMap(Container container, int index, int x, int y) {
        return new Slot(container, index, x, y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof MapLocationItem && stack.getCount() == 1;
            }
        };
    }

    private static Slot output(Container container, int index, int x, int y) {
        return new Slot(container, index, x, y) {
            @Override
            public boolean mayPlace(ItemStack stack) { return false; }
        };
    }

    void applyPlan(int index, ZonePlan plan) {
        if (planner != null) planner.applyPlan(index, plan);
    }
}
