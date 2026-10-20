package ports.buildcraft.energy;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.level.material.Fluid;
import ports.buildcraft.lib.BCMenu;
import ports.buildcraft.lib.GuiTank;

/**
 * Upstream {@code ContainerEngineIron_BC8}: fuel, coolant and residue gauges. Held containers can be
 * clicked on any of them, and ice on the coolant; shift-clicked containers try each tank in turn.
 */
public class CombustionEngineMenu extends BCMenu {
    private final ContainerData data;

    public CombustionEngineMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, inventory, buffer.readBlockPos(), new SimpleContainerData(7), null);
    }

    public CombustionEngineMenu(int id, Inventory inventory, BlockPos pos, ContainerData data, @Nullable CombustionEngineBlockEntity engine) {
        super(BCEnergy.COMBUSTION_ENGINE_MENU.get(), id, pos);
        this.data = data;
        addFullPlayerInventory(inventory, 95);
        addDataSlots(data);
        if (engine != null) {
            List<GuiTank> tanks = List.of(new GuiTank(engine.tankFuel, engine::setChanged),
                    new GuiTank(engine.tankCoolant, FuelRegistry::solidCoolant, engine::setChanged), new GuiTank(engine.tankResidue, engine::setChanged));
            setGuiTanks(tanks, tanks);
        }
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
