package ports.buildcraft.energy;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import ports.buildcraft.lib.FluidAmount;
import ports.buildcraft.lib.Fluids2;
import ports.buildcraft.lib.Power;

/** Upstream {@code BCEnergyRecipes} fuels and coolants for the combustion engine. */
public final class FuelRegistry {
    /** Upstream {@code TIME_BASE}: a multiple of 3, 5, 16 and 1000. */
    private static final int TIME_BASE = 240_000;
    private static final List<Fuel> FUELS = new ArrayList<>();

    /**
     * @param powerPerCycle micro-MJ produced per tick of burning
     * @param totalBurningTime ticks of burning per 1000 mB
     * @param residue residue left per 1000 mB, or empty for clean fuels
     */
    public record Fuel(Supplier<? extends Fluid> fluid, long powerPerCycle, int totalBurningTime, FluidAmount residue) {}

    private FuelRegistry() {}

    static void init() {
        FUELS.clear();
        addFuel(BCEnergy.FUEL_LIGHT::source, 4, 6, 6);
        addDirtyFuel(BCEnergy.OIL::source, 8, 3, 4);
    }

    private static void addFuel(Supplier<? extends Fluid> fluid, int amountDiff, int multiplier, int boostOver4) {
        int totalTime = TIME_BASE * boostOver4 / 4 / multiplier / amountDiff;
        FUELS.add(new Fuel(fluid, multiplier * Power.MJ, totalTime, FluidAmount.EMPTY));
    }

    private static void addDirtyFuel(Supplier<? extends Fluid> fluid, int amountDiff, int multiplier, int boostOver4) {
        int totalTime = TIME_BASE * boostOver4 / 4 / multiplier / amountDiff;
        FluidAmount residue = new FluidAmount(BCEnergy.OIL_RESIDUE.source(), 1000 / amountDiff);
        FUELS.add(new Fuel(fluid, multiplier * Power.MJ, totalTime, residue));
    }

    @Nullable
    public static Fuel fuel(Fluid fluid) {
        Fluid still = Fluids2.still(fluid);
        for (Fuel fuel : FUELS) {
            if (fuel.fluid().get() == still) return fuel;
        }
        return null;
    }

    /** Degrees of cooling per mB; upstream registers only water (0.0023). */
    public static float coolant(Fluid fluid) {
        return Fluids2.still(fluid) == Fluids.WATER ? 0.0023f : 0;
    }

    /** Upstream solid coolants: ice gives 1000 mB of water x1.5, packed ice x2. */
    public static FluidAmount solidCoolant(ItemStack stack) {
        if (stack.is(Items.ICE)) return new FluidAmount(Fluids.WATER, 1500);
        if (stack.is(Items.PACKED_ICE)) return new FluidAmount(Fluids.WATER, 2000);
        return FluidAmount.EMPTY;
    }

    public static List<Fuel> fuels() {
        return List.copyOf(FUELS);
    }
}
