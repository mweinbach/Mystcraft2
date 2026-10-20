package ports.buildcraft.builders;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.builders.snapshot.SchematicBlockAir;
import ports.buildcraft.builders.snapshot.SchematicBlockDefault;
import ports.buildcraft.builders.snapshot.SchematicBlockFluid;
import ports.buildcraft.builders.snapshot.SchematicBlockManager;
import ports.buildcraft.builders.snapshot.SchematicEntityDefault;
import ports.buildcraft.builders.snapshot.SchematicEntityManager;

/** Upstream {@code BCBuildersSchematics}: the block and entity schematics, by priority. */
public final class BuildersSchematics {
    private BuildersSchematics() {}

    public static void init() {
        SchematicBlockManager.registerFactory(BuildCraft.id("air"), 0, SchematicBlockAir::predicate, SchematicBlockAir::new);
        SchematicBlockManager.registerFactory(BuildCraft.id("default"), 100, SchematicBlockDefault::predicate, SchematicBlockDefault::new);
        SchematicBlockManager.registerFactory(BuildCraft.id("fluid"), 200, SchematicBlockFluid::predicate, SchematicBlockFluid::new);
        SchematicBlockManager.registerFactory(BuildCraft.id("banner"), 300, context -> context.block() instanceof AbstractBannerBlock, Banner::new);
        SchematicBlockManager.registerFactory(BuildCraft.id("vine"), 300, context -> context.block() instanceof VineBlock, Vine::new);
        SchematicEntityManager.registerFactory(BuildCraft.id("default"), 100, SchematicEntityDefault::predicate, SchematicEntityDefault::new);
    }

    /** A banner costs a banner of its colour carrying its patterns. Colours are separate blocks since the flattening. */
    public static final class Banner extends SchematicBlockDefault {
        @Override
        public List<ItemStack> computeRequiredItems() {
            ItemStack banner = new ItemStack(blockState.getBlock().asItem());
            if (tileNbt != null && !tileNbt.getList("Patterns", Tag.TAG_COMPOUND).isEmpty()) {
                CompoundTag blockEntityTag = new CompoundTag();
                blockEntityTag.put("Patterns", tileNbt.getList("Patterns", Tag.TAG_COMPOUND).copy());
                BlockItem.setBlockEntityData(banner, BlockEntityType.BANNER, blockEntityTag);
            }
            return List.of(banner);
        }
    }

    /** Vines wait for a vine above them or a solid full block beside them. */
    public static final class Vine extends SchematicBlockDefault {
        @Override
        public boolean isReadyToBuild(Level level, BlockPos pos) {
            return super.isReadyToBuild(level, pos)
                    && (level.getBlockState(pos.above()).getBlock() instanceof VineBlock
                    || Direction.Plane.HORIZONTAL.stream().map(pos::relative).anyMatch(side -> {
                        BlockState state = level.getBlockState(side);
                        return state.isCollisionShapeFullBlock(level, side) && state.getMaterial().blocksMotion();
                    }));
        }
    }
}
