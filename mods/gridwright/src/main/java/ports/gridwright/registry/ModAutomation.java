package ports.gridwright.registry;

import ports.gridwright.Gridwright;
import ports.gridwright.automation.ChunkWardBlock;
import ports.gridwright.automation.ChunkWardBlockEntity;
import ports.gridwright.automation.ChunkWards;
import ports.gridwright.item.GoldenLassoItem;
import ports.gridwright.platform.Platform;
import net.minecraft.core.Registry;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Material;

import java.util.function.Supplier;

/**
 * Phase 3: the chunk-loading ward and the cursed lasso. The mechanical crafter is a worker and the new
 * generators are {@link ports.gridwright.machine.GeneratorKind}s, so they register with their families.
 */
public final class ModAutomation {

    public static final Supplier<ChunkWardBlock> CHUNK_WARD = Platform.register(Registry.BLOCK_REGISTRY, "chunk_loading_ward",
            () -> new ChunkWardBlock(BlockBehaviour.Properties.of(Material.STONE).strength(3.0F, 9.0F).sound(SoundType.STONE)
                    .lightLevel(state -> 7).requiresCorrectToolForDrops()));
    public static final Supplier<BlockItem> CHUNK_WARD_ITEM = Platform.register(Registry.ITEM_REGISTRY, "chunk_loading_ward",
            () -> new BlockItem(CHUNK_WARD.get(), new Item.Properties().tab(Gridwright.TAB).rarity(Rarity.UNCOMMON)));
    public static final Supplier<BlockEntityType<ChunkWardBlockEntity>> CHUNK_WARD_ENTITY = Platform.register(
            Registry.BLOCK_ENTITY_TYPE_REGISTRY, "chunk_loading_ward", () -> BlockEntityType.Builder.of(
                    ChunkWardBlockEntity::new, CHUNK_WARD.get()).build(null));

    public static final Supplier<GoldenLassoItem> CURSED_LASSO = Platform.register(Registry.ITEM_REGISTRY, "cursed_lasso",
            () -> new GoldenLassoItem(new Item.Properties().tab(Gridwright.TAB).stacksTo(1).rarity(Rarity.UNCOMMON), true));

    private ModAutomation() { }

    public static void init() {
        // The game rule must exist before any world loads.
        ChunkWards.LIMIT.getId();
    }
}
