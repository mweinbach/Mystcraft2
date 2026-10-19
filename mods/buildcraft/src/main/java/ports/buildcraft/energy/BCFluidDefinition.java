package ports.buildcraft.energy;

import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.LiquidBlock;
import ports.buildcraft.BuildCraft;

/**
 * One row of upstream {@code BCEnergyFluids}' table at heat 0: density, viscosity, spread and the
 * light/dark colours its sprites are recoloured with.
 */
public final class BCFluidDefinition {
    public final String name;
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

    BCFluidDefinition(String name, int density, int viscosity, int quanta, int lightColour, int darkColour) {
        this.name = name;
        this.density = density;
        this.viscosity = viscosity;
        this.quanta = quanta;
        this.lightColour = lightColour;
        this.darkColour = darkColour;
        this.temperature = 300;
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
