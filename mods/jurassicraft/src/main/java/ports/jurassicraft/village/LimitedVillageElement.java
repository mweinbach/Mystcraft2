package ports.jurassicraft.village;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElementType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * Vanilla probes a candidate's jigsaws at the origin, then tries another element when that list is empty.
 * An already placed building is expanded at its real position, so streets still attach.
 */
final class LimitedVillageElement extends StructurePoolElement {
    private final StructurePoolElement delegate;
    private final VillageLimits.Category category;

    LimitedVillageElement(StructurePoolElement delegate, VillageLimits.Category category) {
        super(delegate.getProjection());
        this.delegate = delegate;
        this.category = category;
    }

    VillageLimits.Category category() { return category; }

    @Override
    public List<StructureTemplate.StructureBlockInfo> getShuffledJigsawBlocks(StructureTemplateManager manager, BlockPos pos,
            Rotation rotation, RandomSource random) {
        if (pos.equals(BlockPos.ZERO) && !VillageLimits.permit(category)) return List.of();
        return delegate.getShuffledJigsawBlocks(manager, pos, rotation, random);
    }

    @Override
    public Vec3i getSize(StructureTemplateManager manager, Rotation rotation) {
        return delegate.getSize(manager, rotation);
    }

    @Override
    public BoundingBox getBoundingBox(StructureTemplateManager manager, BlockPos pos, Rotation rotation) {
        return delegate.getBoundingBox(manager, pos, rotation);
    }

    @Override
    public boolean place(StructureTemplateManager manager, WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
            BlockPos offset, BlockPos origin, Rotation rotation, BoundingBox box, RandomSource random, boolean keepJigsaws) {
        return delegate.place(manager, level, structures, generator, offset, origin, rotation, box, random, keepJigsaws);
    }

    @Override
    public StructurePoolElementType<?> getType() { return delegate.getType(); }

    @Override
    public int getGroundLevelDelta() { return delegate.getGroundLevelDelta(); }

    @Override
    public String toString() { return delegate.toString(); }
}
