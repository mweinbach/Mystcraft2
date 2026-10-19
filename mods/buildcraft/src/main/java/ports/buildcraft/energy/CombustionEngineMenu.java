package ports.buildcraft.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.level.material.Fluid;
import ports.buildcraft.lib.BCMenu;

/** Upstream {@code ContainerEngineIron_BC8}: fuel, coolant and residue gauges. */
public class CombustionEngineMenu extends BCMenu {
    private final ContainerData data;

    public CombustionEngineMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, inventory, buffer.readBlockPos(), new SimpleContainerData(7));
    }

    public CombustionEngineMenu(int id, Inventory inventory, BlockPos pos, ContainerData data) {
        super(BCEnergy.COMBUSTION_ENGINE_MENU.get(), id, pos);
        this.data = data;
        addFullPlayerInventory(inventory, 95);
        addDataSlots(data);
    }

    /** Tank index 0 is fuel, 1 coolant and 2 residue. */
    public Fluid tankFluid(int tank) {
        return Registry.FLUID.byId(data.get(tank * 2));
    }

    public int tankAmount(int tank) {
        return data.get(tank * 2 + 1);
    }

    public double heat() {
        return data.get(6) / 100.0;
    }
}
