package ports.buildcraft.core;

import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Material;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.core.statements.BCCoreStatements;
import ports.buildcraft.platform.Reg;

/** Upstream BuildCraft Core: gears, the wrench, fragile fluid shards, the creative water spring, markers and volume boxes. */
public final class BCCore {
    public static final Supplier<Item> GEAR_WOOD = gear("wood");
    public static final Supplier<Item> GEAR_STONE = gear("stone");
    public static final Supplier<Item> GEAR_IRON = gear("iron");
    public static final Supplier<Item> GEAR_GOLD = gear("gold");
    public static final Supplier<Item> GEAR_DIAMOND = gear("diamond");
    public static final Supplier<WrenchItem> WRENCH = Reg.item("wrench", () -> new WrenchItem(BuildCraft.itemProperties().stacksTo(1)));
    public static final Supplier<FragileFluidShardItem> FRAGILE_FLUID_SHARD = Reg.item("fragile_fluid_shard",
            () -> new FragileFluidShardItem(new Item.Properties().stacksTo(1)));
    /** Upstream {@code EnumSpring.WATER}: puts a water source above itself every 5 ticks. */
    public static final Supplier<SpringBlock> WATER_SPRING = Reg.block("water_spring", () -> new SpringBlock(springProperties(), 5, SpringBlock.ALWAYS,
            Blocks.WATER::defaultBlockState));
    public static final Supplier<Item> WATER_SPRING_ITEM = Reg.blockItem("water_spring", WATER_SPRING, BuildCraft.itemProperties());

    public static final Supplier<MarkerVolumeBlock> MARKER_VOLUME = Reg.block("marker_volume", () -> new MarkerVolumeBlock(markerProperties()));
    public static final Supplier<MarkerPathBlock> MARKER_PATH = Reg.block("marker_path", () -> new MarkerPathBlock(markerProperties()));
    public static final Supplier<Item> MARKER_VOLUME_ITEM = Reg.blockItem("marker_volume", MARKER_VOLUME, BuildCraft.itemProperties());
    public static final Supplier<Item> MARKER_PATH_ITEM = Reg.blockItem("marker_path", MARKER_PATH, BuildCraft.itemProperties());
    public static final Supplier<MarkerConnectorItem> MARKER_CONNECTOR = Reg.item("marker_connector",
            () -> new MarkerConnectorItem(BuildCraft.itemProperties()));
    /** Upstream has no recipe for the volume box; it is a creative item, as are the machines that read it. */
    public static final Supplier<VolumeBoxItem> VOLUME_BOX = Reg.item("volume_box", () -> new VolumeBoxItem(BuildCraft.itemProperties()));
    public static final Supplier<BlockEntityType<MarkerVolumeBlockEntity>> MARKER_VOLUME_TYPE = Reg.blockEntity("marker_volume",
            () -> BlockEntityType.Builder.of(MarkerVolumeBlockEntity::new, MARKER_VOLUME.get()).build(null));
    public static final Supplier<BlockEntityType<MarkerPathBlockEntity>> MARKER_PATH_TYPE = Reg.blockEntity("marker_path",
            () -> BlockEntityType.Builder.of(MarkerPathBlockEntity::new, MARKER_PATH.get()).build(null));

    private BCCore() {}

    public static void init() {
        BCCoreStatements.init();
    }

    /** The conventional Fabric gear tag for {@code material}, using 1.19.2-era {@code c:<material>_gears} names. */
    public static TagKey<Item> commonGearTag(String material) {
        return TagKey.create(Registry.ITEM_REGISTRY, new ResourceLocation("c", material + "_gears"));
    }

    /** The Forge gear tag for {@code material}. */
    public static TagKey<Item> forgeGearTag(String material) {
        return TagKey.create(Registry.ITEM_REGISTRY, new ResourceLocation("forge", "gears/" + material));
    }

    /** Upstream {@code BlockSpring}: unbreakable like bedrock, dropping nothing. */
    public static BlockBehaviour.Properties springProperties() {
        return BlockBehaviour.Properties.of(Material.STONE).strength(-1.0F, 3_600_000.0F).sound(SoundType.STONE).noLootTable()
                .isValidSpawn((state, level, pos, entity) -> false);
    }

    /** Upstream {@code BlockMarkerBase}: circuit material, hardness 0.25, no collision. */
    private static BlockBehaviour.Properties markerProperties() {
        return BlockBehaviour.Properties.of(Material.DECORATION).strength(0.25F).sound(SoundType.STONE).noCollission();
    }

    private static Supplier<Item> gear(String material) {
        return Reg.item("gear_" + material, () -> new Item(BuildCraft.itemProperties()));
    }
}
