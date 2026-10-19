package ports.jurassicraft.worldgen;

import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.util.valueproviders.IntProviderType;
import net.minecraft.world.level.levelgen.feature.Feature;
import ports.jurassicraft.platform.Platform;

public final class WorldgenRegistry {
    public static final Supplier<IntProviderType<BinomialCount>> BINOMIAL_COUNT =
            Platform.register(Registry.INT_PROVIDER_TYPE_REGISTRY, "binomial_count", () -> BinomialCount.TYPE);
    public static final Supplier<Feature<?>> TRACKWAY = Platform.register(Registry.FEATURE_REGISTRY, "fossil_trackway", FossilTrackwayFeature::new);
    private WorldgenRegistry() {}
    public static void init() { }
}
