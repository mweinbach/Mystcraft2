package ports.jurassicraft.egg;

import java.util.List;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;
import ports.jurassicraft.config.JurassicConfig;

public final class FossilNestRegistry {
    public static final Supplier<Feature<?>> FEATURE = feature("fossil_nest", new FossilNestFeature());
    private static final List<Supplier<Block>> NESTS = List.of(
            nestBlock("nest_fossil", 0, false), nestBlock("nest_fossil_2", 1, false), nestBlock("nest_fossil_3", 2, false));
    private static final List<Supplier<Block>> ENCASED = List.of(
            nestBlock("encased_nest_fossil", 0, true), nestBlock("encased_nest_fossil_2", 1, true), nestBlock("encased_nest_fossil_3", 2, true));
    private static final List<Supplier<Block>> TRACKWAYS = List.of(
            block("fossilized_trackway_biped_medium", new FossilTrackwayBlock()),
            block("fossilized_trackway_biped_small", new FossilTrackwayBlock()),
            block("fossilized_trackway_raptor", new FossilTrackwayBlock()));

    private FossilNestRegistry() {}
    private static ResourceLocation id(String name) { return new ResourceLocation(PortMod.MOD_ID, name); }
    private static Supplier<Feature<?>> feature(String name, Feature<?> value) {
        Registry.register(Registry.FEATURE, id(name), value);
        return () -> value;
    }
    private static Supplier<Block> nestBlock(String name, int variant, boolean encased) {
        return block(name, new FossilNestBlock(variant, encased));
    }
    private static Supplier<Block> block(String name, Block value) {
        Registry.register(Registry.BLOCK, id(name), value);
        Registry.register(Registry.ITEM, id(name), new BlockItem(value, new Item.Properties().tab(JurassicContent.TAB)));
        return () -> value;
    }
    public static Supplier<Block> nest(int variant, boolean encased) { return (encased ? ENCASED : NESTS).get(variant); }
    public static Supplier<Block> trackway(int variant) { return TRACKWAYS.get(variant); }

    public static InteractionResult tryWrap(UseOnContext context) {
        var level = context.getLevel();
        var player = context.getPlayer();
        Block block = level.getBlockState(context.getClickedPos()).getBlock();
        if (!(block instanceof FossilNestBlock nest) || nest.encased() || player == null
                || !player.mayUseItemAt(context.getClickedPos().relative(context.getClickedFace()),
                        context.getClickedFace(), context.getItemInHand())) return InteractionResult.PASS;
        if (!level.isClientSide && level.setBlock(context.getClickedPos(), nest(nest.variant(), true).get().defaultBlockState(), 3)
                && !player.getAbilities().instabuild) context.getItemInHand().shrink(1);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public static void init() {
        ResourceKey<PlacedFeature> feature = ResourceKey.create(Registry.PLACED_FEATURE_REGISTRY, id("fossil_nest"));
        BiomeModifications.addFeature(BiomeSelectors.foundInOverworld().and(context -> JurassicConfig.generation("nestFossilGeneration")),
                GenerationStep.Decoration.UNDERGROUND_ORES, feature);
    }
}
