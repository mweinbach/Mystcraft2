package ports.jurassicraft.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraftforge.common.world.BiomeModifier;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import ports.jurassicraft.PortMod;

public final class WorldgenRegistry {
    private static final DeferredRegister<Codec<? extends BiomeModifier>> MODIFIERS =
            DeferredRegister.create(ForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, PortMod.MOD_ID);
    private static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(ForgeRegistries.FEATURES, PortMod.MOD_ID);
    public static final RegistryObject<Codec<? extends BiomeModifier>> FILTER =
            MODIFIERS.register("worldgen_filter", () -> Codec.unit(WorldgenBiomeModifier::new));
    public static final RegistryObject<Feature<?>> TRACKWAY = FEATURES.register("fossil_trackway", FossilTrackwayFeature::new);
    private WorldgenRegistry() {}
    public static void register(IEventBus bus) { MODIFIERS.register(bus); FEATURES.register(bus); }
}
