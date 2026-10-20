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
     * @param residuePerBucket mB of residue left per 1000 mB, or 0 for clean fuels
     */
    public record Fuel(Supplier<? extends Fluid> fluid, long powerPerCycle, int totalBurningTime, int residuePerBucket) {
        /** Fluids resolve after registration, so the residue is looked up when it is needed. */
        public FluidAmount residue() {
            return residuePerBucket <= 0 ? FluidAmount.EMPTY : new FluidAmount(BCEnergy.OIL_RESIDUE.heat(0).source(), residuePerBucket);
        }
    }

    private FuelRegistry() {}

    /** Only the cool stage of each fluid burns, as upstream registers {@code getFirstOrNull} of each family. */
    static void init() {
        FUELS.clear();
        addFuel(BCEnergy.FUEL_GASEOUS, RefineryRecipes.GAS, 8, 4);
        addFuel(BCEnergy.FUEL_LIGHT, RefineryRecipes.LIGHT, 6, 6);
        addFuel(BCEnergy.FUEL_DENSE, RefineryRecipes.DENSE, 4, 12);

        addFuel(BCEnergy.FUEL_MIXED_LIGHT, RefineryRecipes.GAS_LIGHT, 3, 5);
        addFuel(BCEnergy.FUEL_MIXED_HEAVY, RefineryRecipes.LIGHT_DENSE, 5, 8);
        addDirtyFuel(BCEnergy.OIL_DENSE, RefineryRecipes.DENSE_RESIDUE, 4, 4);

        addFuel(BCEnergy.OIL_DISTILLED, RefineryRecipes.GAS_LIGHT_DENSE, 1, 5);
        addDirtyFuel(BCEnergy.OIL_HEAVY, RefineryRecipes.LIGHT_DENSE_RESIDUE, 2, 4);

        addDirtyFuel(BCEnergy.OIL, RefineryRecipes.OIL, 3, 4);
    }

    private static void addFuel(BCFluidFamily family, int amountDiff, int multiplier, int boostOver4) {
        int totalTime = TIME_BASE * boostOver4 / 4 / multiplier / amountDiff;
        FUELS.add(new Fuel(family.heat(0)::source, multiplier * Power.MJ, totalTime, 0));
    }

    private static void addDirtyFuel(BCFluidFamily family, int amountDiff, int multiplier, int boostOver4) {
        int totalTime = TIME_BASE * boostOver4 / 4 / multiplier / amountDiff;
        FUELS.add(new Fuel(family.heat(0)::source, multiplier * Power.MJ, totalTime, 1000 / amountDiff));
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
