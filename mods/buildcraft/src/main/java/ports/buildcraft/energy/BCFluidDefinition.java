package ports.buildcraft.energy;

import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.LiquidBlock;
import ports.buildcraft.BuildCraft;

/**
 * One heat stage of an upstream {@code BCEnergyFluids} row, with upstream's per-heat adjustments: viscosity
 * falls by a quarter per stage, the fluid turns gaseous (negative density) at its boiling heat, spreads
 * further and is 20 degrees warmer per stage.
 */
public final class BCFluidDefinition {
    /** Registry name: the family name at heat 0, {@code <family>_heat_<n>} above. */
    public final String name;
    public final BCFluidFamily family;
    public final int heat;
    public final int density;
    public final int viscosity;
    public final int quanta;
    public final int lightColour;
    public final int darkColour;
    public final int temperature;
    BCFluid source;
    BCFluid flowing;
    Supplier<LiquidBlock> block;
    Supplier<Item> bucket;

    BCFluidDefinition(BCFluidFamily family, int heat) {
        this.family = family;
        this.heat = heat;
        this.name = family.name + (heat == 0 ? "" : "_heat_" + heat);
        this.density = family.density * (heat >= family.boilPoint ? -1 : 1);
        this.viscosity = family.viscosity * (4 - heat) / 4;
        this.quanta = family.spread + (family.spread > 6 ? heat : heat / 2);
        this.lightColour = family.lightColour;
        this.darkColour = family.darkColour;
        this.temperature = 300 + 20 * heat;
    }

    public BCFluid source() { return source; }
    public BCFluid flowing() { return flowing; }
    public LiquidBlock block() { return block.get(); }
    public Item bucket() { return bucket.get(); }

    public ResourceLocation stillTexture() { return BuildCraft.id("block/fluids/" + name + "_still"); }
    public ResourceLocation flowingTexture() { return BuildCraft.id("block/fluids/" + name + "_flow"); }
    public boolean isGaseous() { return density < 0; }
    /** Upstream viscosity is relative to water's 1000 (water ticks every 5 game ticks). */
    public int tickDelay() { return Math.max(1, viscosity / 200); }
    /** Upstream quanta are 1.12 flow distances; 1.19.2 expresses spread as a per-block level drop. */
    public int dropOff() { return quanta >= 6 ? 1 : 2; }
}
