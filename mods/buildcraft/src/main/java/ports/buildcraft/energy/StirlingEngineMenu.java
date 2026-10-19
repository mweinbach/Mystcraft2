package ports.buildcraft.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import ports.buildcraft.lib.BCMenu;
import ports.buildcraft.platform.Platform;

/** Upstream {@code ContainerEngineStone_BC8}: one fuel slot above the player inventory. */
public class StirlingEngineMenu extends BCMenu {
    private final ContainerData data;

    public StirlingEngineMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, inventory, buffer.readBlockPos(), new SimpleContainer(1), new SimpleContainerData(4));
    }

    public StirlingEngineMenu(int id, Inventory inventory, StirlingEngineBlockEntity engine, ContainerData data) {
        this(id, inventory, engine.getBlockPos(), engine, data);
    }

    private StirlingEngineMenu(int id, Inventory inventory, BlockPos pos, Container container, ContainerData data) {
        super(BCEnergy.STIRLING_ENGINE_MENU.get(), id, pos);
        this.data = data;
        addFullPlayerInventory(inventory, 84);
        addSlot(new Slot(container, 0, 80, 41) {
            @Override public boolean mayPlace(ItemStack stack) { return Platform.get().burnTime(stack) > 0; }
        });
        addDataSlots(data);
    }

    public int burnTime() { return data.get(0); }
    public int totalBurnTime() { return data.get(1); }
    public double heat() { return data.get(2) / 100.0; }
    public int storedMj() { return data.get(3); }

    /** Flame height in pixels, as upstream {@code GuiEngineStone_BC8}. */
    public int flameHeight(int max) {
        int total = totalBurnTime();
        if (total <= 0) return 0;
        return burnTime() * max / total;
    }
}
