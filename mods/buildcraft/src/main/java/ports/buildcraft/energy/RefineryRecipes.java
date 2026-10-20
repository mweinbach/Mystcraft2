package ports.buildcraft.energy;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import ports.buildcraft.lib.FluidAmount;
import ports.buildcraft.lib.Fluids2;
import ports.buildcraft.lib.Power;

/**
 * Upstream {@code BCEnergyRecipes}' refinery recipes: distillation splits a fluid into a gas and a liquid
 * at one heat stage, and the heat exchanger moves fluids between heat stages. Fluids only exist once
 * registries are populated, so the tables are built on first use.
 */
public final class RefineryRecipes {
    // Relative amounts: the oil used in refining returns this many parts of each product.
    public static final int OIL = 8;
    public static final int GAS = 16;
    public static final int LIGHT = 4;
    public static final int DENSE = 2;
    public static final int RESIDUE = 1;
    public static final int GAS_LIGHT = 10;
    public static final int LIGHT_DENSE = 5;
    public static final int DENSE_RESIDUE = 2;
    public static final int LIGHT_DENSE_RESIDUE = 3;
    public static final int GAS_LIGHT_DENSE = 8;

    /** @param powerRequired micro-MJ per recipe run */
    public record Distillation(FluidAmount in, FluidAmount outGas, FluidAmount outLiquid, long powerRequired) {}

    /**
     * A heat exchanger step. Upstream's per-recipe amounts are ignored by the exchanger, which moves the
     * same amount through both sides, so only the fluids and heats are kept.
     *
     * @param out the fluid after the step, or null when the input is used up (water, lava)
     */
    public record HeatStep(Fluid in, @Nullable Fluid out, int heatFrom, int heatTo) {}

    private record Tables(Map<Fluid, Distillation> distillation, Map<Fluid, HeatStep> heatable, Map<Fluid, HeatStep> coolable) {}

    private static volatile Tables tables;

    private RefineryRecipes() {}

    @Nullable
    public static Distillation distillation(Fluid fluid) {
        return tables().distillation.get(Fluids2.still(fluid));
    }

    public static Collection<Distillation> distillations() {
        return tables().distillation.values();
    }

    /** The step that heats {@code fluid}, which makes it usable as the exchanger's heatant. */
    @Nullable
    public static HeatStep heatable(Fluid fluid) {
        return tables().heatable.get(Fluids2.still(fluid));
    }

    /** The step that cools {@code fluid}, which makes it usable as the exchanger's coolant. */
    @Nullable
    public static HeatStep coolable(Fluid fluid) {
        return tables().coolable.get(Fluids2.still(fluid));
    }

    private static Tables tables() {
        Tables built = tables;
        if (built == null) tables = built = build();
        return built;
    }

    private static Tables build() {
        Map<Fluid, Distillation> distillation = new HashMap<>();
        BCFluidFamily crude = BCEnergy.OIL, distilled = BCEnergy.OIL_DISTILLED, mixedLight = BCEnergy.FUEL_MIXED_LIGHT;
        BCFluidFamily gas = BCEnergy.FUEL_GASEOUS, heavy = BCEnergy.OIL_HEAVY, mixedHeavy = BCEnergy.FUEL_MIXED_HEAVY;
        BCFluidFamily light = BCEnergy.FUEL_LIGHT, denseOil = BCEnergy.OIL_DENSE, denseFuel = BCEnergy.FUEL_DENSE;
        BCFluidFamily residue = BCEnergy.OIL_RESIDUE;

        distil(distillation, crude, OIL, gas, GAS, heavy, LIGHT_DENSE_RESIDUE, 0, 32);
        distil(distillation, crude, OIL, mixedLight, GAS_LIGHT, denseOil, DENSE_RESIDUE, 1, 16);
        distil(distillation, crude, OIL, distilled, GAS_LIGHT_DENSE, residue, RESIDUE, 2, 12);

        distil(distillation, distilled, GAS_LIGHT_DENSE, gas, GAS, mixedHeavy, LIGHT_DENSE, 0, 24);
        distil(distillation, distilled, GAS_LIGHT_DENSE, mixedLight, GAS_LIGHT, denseFuel, DENSE, 1, 16);

        distil(distillation, mixedLight, GAS_LIGHT, gas, GAS, light, LIGHT, 0, 24);

        distil(distillation, heavy, LIGHT_DENSE_RESIDUE, light, LIGHT, denseOil, DENSE_RESIDUE, 1, 16);
        distil(distillation, heavy, LIGHT_DENSE_RESIDUE, mixedHeavy, LIGHT_DENSE, residue, RESIDUE, 2, 12);

        distil(distillation, mixedHeavy, LIGHT_DENSE, light, LIGHT, denseFuel, DENSE, 1, 16);

        distil(distillation, denseOil, DENSE_RESIDUE, denseFuel, DENSE, residue, RESIDUE, 2, 12);

        Map<Fluid, HeatStep> heatable = new HashMap<>();
        Map<Fluid, HeatStep> coolable = new HashMap<>();
        for (BCFluidFamily family : new BCFluidFamily[] {crude, distilled, heavy, mixedLight, mixedHeavy, denseOil, gas, light, denseFuel, residue}) {
            for (int heat = 0; heat < BCFluidFamily.HEATS - 1; heat++) {
                Fluid cool = family.heat(heat).source();
                Fluid hot = family.heat(heat + 1).source();
                heatable.put(cool, new HeatStep(cool, hot, heat, heat + 1));
                coolable.put(hot, new HeatStep(hot, cool, heat + 1, heat));
            }
        }
        heatable.put(Fluids.WATER, new HeatStep(Fluids.WATER, null, 0, 1));
        coolable.put(Fluids.LAVA, new HeatStep(Fluids.LAVA, null, 4, 2));
        return new Tables(Map.copyOf(distillation), Map.copyOf(heatable), Map.copyOf(coolable));
    }

    /** Upstream {@code addDistillation}: amounts and power are divided by their highest common factor. */
    private static void distil(Map<Fluid, Distillation> recipes, BCFluidFamily in, int inAmount, BCFluidFamily outGas, int gasAmount,
            BCFluidFamily outLiquid, int liquidAmount, int heat, long mj) {
        int hcf = gcd(gcd(inAmount, gasAmount), liquidAmount);
        Fluid input = in.heat(heat).source();
        if (recipes.containsKey(input)) throw new IllegalStateException("Already added distillation recipe for " + in.heat(heat).name);
        recipes.put(input, new Distillation(new FluidAmount(input, inAmount / hcf),
                new FluidAmount(outGas.heat(heat).source(), gasAmount / hcf),
                new FluidAmount(outLiquid.heat(heat).source(), liquidAmount / hcf),
                mj * Power.MJ / hcf));
    }

    private static int gcd(int a, int b) {
        return b == 0 ? a : gcd(b, a % b);
    }
}
