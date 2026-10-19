package ports.jurassicraft.egg;

import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;

/** Standalone nest, trackway and feature registrations. */
public final class FossilNestRegistry {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, PortMod.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, PortMod.MOD_ID);
    private static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registry.FEATURE_REGISTRY, PortMod.MOD_ID);
    public static final RegistryObject<Feature<?>> FEATURE = FEATURES.register("fossil_nest", FossilNestFeature::new);
    private static final List<RegistryObject<Block>> NESTS = List.of(
            nestBlock("nest_fossil", 0, false), nestBlock("nest_fossil_2", 1, false), nestBlock("nest_fossil_3", 2, false));
    private static final List<RegistryObject<Block>> ENCASED = List.of(
            nestBlock("encased_nest_fossil", 0, true), nestBlock("encased_nest_fossil_2", 1, true),
            nestBlock("encased_nest_fossil_3", 2, true));
    private static final List<RegistryObject<Block>> TRACKWAYS = List.of(
            trackwayBlock("fossilized_trackway_biped_medium"), trackwayBlock("fossilized_trackway_biped_small"),
            trackwayBlock("fossilized_trackway_raptor"));

    private FossilNestRegistry() {}

    private static RegistryObject<Block> nestBlock(String name, int variant, boolean encased) {
        RegistryObject<Block> block = BLOCKS.register(name, () -> new FossilNestBlock(variant, encased));
        ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties().tab(JurassicContent.TAB)));
        return block;
    }

    private static RegistryObject<Block> trackwayBlock(String name) {
        RegistryObject<Block> block = BLOCKS.register(name, FossilTrackwayBlock::new);
        ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties().tab(JurassicContent.TAB)));
        return block;
    }

    public static RegistryObject<Block> nest(int variant, boolean encased) { return (encased ? ENCASED : NESTS).get(variant); }
    public static RegistryObject<Block> trackway(int variant) { return TRACKWAYS.get(variant); }

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

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        FEATURES.register(bus);
    }
}
