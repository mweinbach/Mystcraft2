package ports.buildcraft.factory;

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
 * Upstream {@code ContainerDistiller}: input, gas and liquid tank gauges. Held containers can be
 * clicked on any of them; shift-clicked containers go to the input.
 */
public class DistillerMenu extends BCMenu {
    public static final int TANK_IN = 0, TANK_GAS = 1, TANK_LIQUID = 2;
    private final ContainerData data;

    public DistillerMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, inventory, buffer.readBlockPos(), new SimpleContainerData(7), null);
    }

    public DistillerMenu(int id, Inventory inventory, BlockPos pos, ContainerData data, @Nullable DistillerBlockEntity distiller) {
        super(BCFactory.DISTILLER_MENU.get(), id, pos);
        this.data = data;
        addFullPlayerInventory(inventory, 79);
        addDataSlots(data);
        if (distiller != null) {
            List<GuiTank> tanks = List.of(new GuiTank(distiller.tankIn, distiller::setChanged),
                    new GuiTank(distiller.tankGasOut, distiller::setChanged), new GuiTank(distiller.tankLiquidOut, distiller::setChanged));
            setGuiTanks(tanks, List.of(tanks.get(TANK_IN)));
        }
    }

    public Fluid tankFluid(int tank) {
        return Registry.FLUID.byId(data.get(tank * 2));
    }

    public int tankAmount(int tank) {
        return data.get(tank * 2 + 1);
    }

    public boolean isActive() {
        return data.get(6) != 0;
    }
}
