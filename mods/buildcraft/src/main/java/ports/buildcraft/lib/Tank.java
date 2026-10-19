package ports.buildcraft.lib;

import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/** A single-fluid tank measured in millibuckets, mirroring upstream {@code buildcraft.lib.fluid.Tank}. */
public class Tank {
    private final String name;
    private final int capacity;
    private final Predicate<Fluid> filter;
    private Fluid fluid = Fluids.EMPTY;
    private int amount;

    public Tank(String name, int capacity, Predicate<Fluid> filter) {
        this.name = name;
        this.capacity = capacity;
        this.filter = filter;
    }

    public Tank(String name, int capacity) {
        this(name, capacity, fluid -> true);
    }

    public String name() { return name; }
    public int capacity() { return capacity; }
    public Fluid fluid() { return fluid; }
    public int amount() { return amount; }
    public boolean isEmpty() { return amount <= 0; }
    public int space() { return capacity - amount; }
    public FluidAmount contents() { return new FluidAmount(fluid, amount); }

    public boolean accepts(Fluid candidate) {
        return candidate != Fluids.EMPTY && filter.test(candidate);
    }

    public int fill(Fluid incoming, int max, boolean simulate) {
        incoming = Fluids2.still(incoming);
        if (max <= 0 || !accepts(incoming) || (amount > 0 && incoming != fluid)) return 0;
        int filled = Math.min(max, capacity - amount);
        if (!simulate && filled > 0) {
            fluid = incoming;
            amount += filled;
        }
        return filled;
    }

    public FluidAmount drain(@Nullable Fluid wanted, int max, boolean simulate) {
        if (max <= 0 || amount <= 0) return FluidAmount.EMPTY;
        if (wanted != null && Fluids2.still(wanted) != fluid) return FluidAmount.EMPTY;
        int drained = Math.min(max, amount);
        Fluid drainedFluid = fluid;
        if (!simulate) {
            amount -= drained;
            if (amount == 0) fluid = Fluids.EMPTY;
        }
        return new FluidAmount(drainedFluid, drained);
    }

    public void set(Fluid newFluid, int newAmount) {
        newAmount = Math.max(0, Math.min(capacity, newAmount));
        if (newAmount == 0 || newFluid == Fluids.EMPTY) {
            fluid = Fluids.EMPTY;
            amount = 0;
        } else {
            fluid = Fluids2.still(newFluid);
            amount = newAmount;
        }
    }

    public void clear() {
        set(Fluids.EMPTY, 0);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (amount > 0) {
            tag.putString("fluid", Registry.FLUID.getKey(fluid).toString());
            tag.putInt("amount", amount);
        }
        return tag;
    }

    public void load(CompoundTag tag) {
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("fluid"));
        set(id == null ? Fluids.EMPTY : Registry.FLUID.get(id), tag.getInt("amount"));
    }

    public long snapshotValue() {
        return ((long) Registry.FLUID.getId(fluid) << 32) | (amount & 0xFFFFFFFFL);
    }

    public void restoreValue(long value) {
        set(Registry.FLUID.byId((int) (value >>> 32)), (int) value);
    }
}
