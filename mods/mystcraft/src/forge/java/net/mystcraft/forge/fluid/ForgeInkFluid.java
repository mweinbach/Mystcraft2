package net.mystcraft.forge.fluid;
import java.util.function.Supplier;
import net.minecraftforge.fluids.FluidType;
import net.mystcraft.minecraft.fluid.ClassicInkFluid;
/** Forge only adds the native FluidType lookup; all flow behavior is shared. */
public final class ForgeInkFluid extends ClassicInkFluid {
    private final Supplier<FluidType> type;
    public ForgeInkFluid(boolean source,Supplier<FluidType> type){super(source);this.type=type;}
    @Override public FluidType getFluidType(){return type.get();}
}
