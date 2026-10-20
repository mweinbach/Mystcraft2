package ports.buildcraft.factory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.level.material.Fluid;
import ports.buildcraft.lib.BCMenu;

/** Upstream {@code ContainerDistiller}: input, gas and liquid tank gauges. */
public class DistillerMenu extends BCMenu {
    public static final int TANK_IN = 0, TANK_GAS = 1, TANK_LIQUID = 2;
    private final ContainerData data;

    public DistillerMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, inventory, buffer.readBlockPos(), new SimpleContainerData(7));
    }

    public DistillerMenu(int id, Inventory inventory, BlockPos pos, ContainerData data) {
        super(BCFactory.DISTILLER_MENU.get(), id, pos);
        this.data = data;
        addFullPlayerInventory(inventory, 79);
        addDataSlots(data);
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
