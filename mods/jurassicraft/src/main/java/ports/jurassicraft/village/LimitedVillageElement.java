package ports.jurassicraft.village;

import com.mojang.datafixers.util.Either;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * Vanilla probes a candidate's jigsaws at the origin, then tries another element when that list is empty.
 * An already placed building is expanded at its real position, so streets still attach.
 * This is a {@link SinglePoolElement} so a generated village saves as {@code minecraft:single_pool_element}.
 * The cap only applies during assembly; loading the chunk reconstructs a plain single element.
 */
final class LimitedVillageElement extends SinglePoolElement {
    private final VillageLimits.Category category;

    LimitedVillageElement(ResourceLocation template, Holder<StructureProcessorList> processors, VillageLimits.Category category) {
        super(Either.left(template), processors, StructureTemplatePool.Projection.RIGID);
        this.category = category;
    }

    VillageLimits.Category category() { return category; }

    @Override
    public List<StructureTemplate.StructureBlockInfo> getShuffledJigsawBlocks(StructureTemplateManager manager, BlockPos pos,
            Rotation rotation, RandomSource random) {
        if (pos.equals(BlockPos.ZERO) && !VillageLimits.permit(category)) return List.of();
        return super.getShuffledJigsawBlocks(manager, pos, rotation, random);
    }
}
