package ports.buildcraft.builders.snapshot.rules;

import com.google.gson.JsonObject;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.Registry;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import ports.buildcraft.lib.FluidAmount;

/** Upstream {@code FluidStackRef}: a fluid and an amount in millibuckets, defaulting to a bucket. */
public final class FluidStackRef {
    private final NbtRef fluid;
    @Nullable private final NbtRef amount;

    private FluidStackRef(NbtRef fluid, @Nullable NbtRef amount) {
        this.fluid = fluid;
        this.amount = amount;
    }

    public static FluidStackRef fromJson(JsonObject json) {
        NbtRef fluid = NbtRef.fromJson(json.get("fluid"));
        if (fluid == null) throw new IllegalArgumentException("A fluid reference needs a fluid: " + json);
        return new FluidStackRef(fluid, NbtRef.fromJson(json.get("amount")));
    }

    public FluidAmount get(@Nullable Tag nbt) {
        Optional<Fluid> found = fluid.get(nbt).map(Tag::getAsString).map(ResourceLocation::tryParse).flatMap(Registry.FLUID::getOptional);
        if (found.isEmpty()) return FluidAmount.EMPTY;
        long millibuckets = Optional.ofNullable(amount).flatMap(ref -> ref.get(nbt))
                .filter(NumericTag.class::isInstance).map(tag -> (long) ((NumericTag) tag).getAsInt()).orElse(1000L);
        return new FluidAmount(found.get(), millibuckets);
    }
}
