package ports.buildcraft.lib;

import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * Upstream {@code FluidSmoother} on the client: a synced copy of a server {@link Tank} whose rendered
 * amount eases toward the latest synced amount.
 */
public final class ClientTank {
    private final int capacity;
    private Fluid fluid = Fluids.EMPTY;
    private float amount;
    private float lastAmount;
    private int target;

    public ClientTank(int capacity) {
        this.capacity = capacity;
    }

    /** Writes what {@link #read} expects for {@code tank} under {@code key}. */
    public static void write(CompoundTag tag, String key, Tank tank) {
        CompoundTag tankTag = new CompoundTag();
        tankTag.putString("fluid", Registry.FLUID.getKey(tank.fluid()).toString());
        tankTag.putInt("amount", tank.amount());
        tag.put(key, tankTag);
    }

    public void read(CompoundTag tag, String key) {
        CompoundTag tankTag = tag.getCompound(key);
        ResourceLocation id = ResourceLocation.tryParse(tankTag.getString("fluid"));
        boolean first = fluid == Fluids.EMPTY;
        fluid = id == null ? Fluids.EMPTY : Registry.FLUID.get(id);
        target = tankTag.getInt("amount");
        if (first || fluid == Fluids.EMPTY) amount = lastAmount = target;
    }

    public void tick() {
        lastAmount = amount;
        float diff = target - amount;
        amount = Math.abs(diff) < 1 ? target : amount + diff / 4;
    }

    public int capacity() { return capacity; }
    public Fluid fluid() { return fluid; }
    /** The latest synced amount, without smoothing. */
    public int syncedAmount() { return target; }

    public float amount(float partialTicks) {
        return lastAmount + (amount - lastAmount) * partialTicks;
    }
}
