package ports.jurassicraft.egg;

import ports.jurassicraft.platform.Platform;
import java.util.function.Supplier;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.feature.Feature;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;

/** Standalone nest, trackway and feature registrations. */
public final class FossilNestRegistry {
    public static final Supplier<Feature<?>> FEATURE = Platform.register(Registry.FEATURE_REGISTRY, "fossil_nest", FossilNestFeature::new);
    private static final List<Supplier<Block>> NESTS = List.of(
            nestBlock("nest_fossil", 0, false), nestBlock("nest_fossil_2", 1, false), nestBlock("nest_fossil_3", 2, false));
    private static final List<Supplier<Block>> ENCASED = List.of(
            nestBlock("encased_nest_fossil", 0, true), nestBlock("encased_nest_fossil_2", 1, true),
            nestBlock("encased_nest_fossil_3", 2, true));
    private static final List<Supplier<Block>> TRACKWAYS = List.of(
            trackwayBlock("fossilized_trackway_biped_medium"), trackwayBlock("fossilized_trackway_biped_small"),
            trackwayBlock("fossilized_trackway_raptor"));

    private FossilNestRegistry() {}

    private static Supplier<Block> nestBlock(String name, int variant, boolean encased) {
        Supplier<Block> block = Platform.register(Registry.BLOCK_REGISTRY, name, () -> new FossilNestBlock(variant, encased));
        Platform.register(Registry.ITEM_REGISTRY, name, () -> new BlockItem(block.get(), new Item.Properties().tab(JurassicContent.TAB)));
        return block;
    }

    private static Supplier<Block> trackwayBlock(String name) {
        Supplier<Block> block = Platform.register(Registry.BLOCK_REGISTRY, name, FossilTrackwayBlock::new);
        Platform.register(Registry.ITEM_REGISTRY, name, () -> new BlockItem(block.get(), new Item.Properties().tab(JurassicContent.TAB)));
        return block;
    }

    public static Supplier<Block> nest(int variant, boolean encased) { return (encased ? ENCASED : NESTS).get(variant); }
    public static Supplier<Block> trackway(int variant) { return TRACKWAYS.get(variant); }

    /** Called by the plaster item before its ordinary dinosaur fossil handling. */
    public static InteractionResult tryWrap(UseOnContext context) {
        var level = context.getLevel();
        var player = context.getPlayer();
        Block block = level.getBlockState(context.getClickedPos()).getBlock();
        if (!(block instanceof FossilNestBlock nest) || nest.encased() || player == null
                || !player.mayUseItemAt(context.getClickedPos().relative(context.getClickedFace()),
                                        context.getClickedFace(), context.getItemInHand())) return InteractionResult.PASS;
        if (!level.isClientSide && level.setBlock(context.getClickedPos(),
                nest(nest.variant(), true).get().defaultBlockState(), 3) && !player.getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public static void init() {
    }
}
