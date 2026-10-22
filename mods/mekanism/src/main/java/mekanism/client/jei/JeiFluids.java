package mekanism.client.jei;

import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.helpers.IPlatformFluidHelper;
import mezz.jei.api.ingredients.IIngredientType;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;

/**
 * Turns Mekanism's millibucket fluid stacks into the fluid ingredient JEI uses on this loader.
 * Forge's ingredient is {@link FluidStack}; Fabric's is JEI's own fluid ingredient, in droplets.
 */
public final class JeiFluids {

    private static IPlatformFluidHelper<?> helper;

    private JeiFluids() {
    }

    public static void bind(IPlatformFluidHelper<?> platformHelper) {
        helper = platformHelper;
    }

    public static IIngredientType<?> type() {
        return helper.getFluidIngredientType();
    }

    public static long toPlatformAmount(int milliBuckets) {
        if (milliBuckets <= 0) {
            return 0;
        }
        return milliBuckets * helper.bucketVolume() / FluidType.BUCKET_VOLUME;
    }

    @SuppressWarnings("unchecked")
    public static <T> T convert(FluidStack stack) {
        IPlatformFluidHelper<T> fluids = (IPlatformFluidHelper<T>) helper;
        CompoundTag tag = stack.hasTag() ? stack.getTag() : null;
        return fluids.create(stack.getFluid(), toPlatformAmount(stack.getAmount()), tag);
    }

    public static List<?> convert(List<FluidStack> stacks) {
        List<Object> converted = new ArrayList<>(stacks.size());
        for (FluidStack stack : stacks) {
            if (!stack.isEmpty()) {
                converted.add(convert(stack));
            }
        }
        return converted;
    }
}
