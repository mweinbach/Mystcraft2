package ports.gridwright.registry;

import ports.gridwright.Gridwright;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/** Block tags Gridwright reads; packs can change what the machines do through them. */
public final class ModTags {

    /** Blocks no machine ever mines (bedrock and other unbreakables are refused regardless). */
    public static final TagKey<Block> UNMINABLE = gridwright("unminable");
    /** Blocks that can form an ender quarry's boundary loop. */
    public static final TagKey<Block> QUARRY_BOUNDARY = gridwright("quarry_boundary");
    /** Blocks the quantum quarry never produces, even if they are tagged as ores. */
    public static final TagKey<Block> QUANTUM_QUARRY_EXCLUDED = gridwright("quantum_quarry_excluded");
    /** The common ore tags of Fabric (c) and Forge (forge); modded ores listed there join the quantum quarry's table. */
    public static final TagKey<Block> C_ORES = TagKey.create(Registry.BLOCK_REGISTRY, new ResourceLocation("c", "ores"));
    public static final TagKey<Block> FORGE_ORES = TagKey.create(Registry.BLOCK_REGISTRY, new ResourceLocation("forge", "ores"));

    private ModTags() { }

    private static TagKey<Block> gridwright(String path) {
        return TagKey.create(Registry.BLOCK_REGISTRY, new ResourceLocation(Gridwright.MOD_ID, path));
    }
}
