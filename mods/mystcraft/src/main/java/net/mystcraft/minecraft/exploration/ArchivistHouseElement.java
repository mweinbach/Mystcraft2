package net.mystcraft.minecraft.exploration;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElementType;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.mystcraft.integration.registry.ExplorationRegistry;
import net.mystcraft.integration.registry.VillageArchivistRegistry;
import net.mystcraft.integration.registry.WritingDeskRegistry;
import net.mystcraft.minecraft.writing.WritingDeskBlock;

/** Persistent native jigsaw piece with the exact classic house's two treasure lecterns and empty desk. */
public final class ArchivistHouseElement extends SinglePoolElement {
    public static final ResourceLocation TEMPLATE = new ResourceLocation("mystcraft", "village/archivist_house");
    public static final Codec<ArchivistHouseElement> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ArchivistHouseElement.<ArchivistHouseElement>templateCodec(),
            ArchivistHouseElement.<ArchivistHouseElement>processorsCodec(),
            ArchivistHouseElement.<ArchivistHouseElement>projectionCodec()).apply(instance, ArchivistHouseElement::new));
    private ArchivistHouseElement(Either<ResourceLocation, StructureTemplate> template, Holder<StructureProcessorList> processors,
                                  StructureTemplatePool.Projection projection) { super(template, processors, projection); }
    public static ArchivistHouseElement create() {
        return new ArchivistHouseElement(Either.left(TEMPLATE), Holder.direct(new StructureProcessorList(List.of())), StructureTemplatePool.Projection.RIGID);
    }
    @Override public StructurePoolElementType<?> getType() { return VillageArchivistRegistry.HOUSE.get(); }
    @Override public List<StructureTemplate.StructureBlockInfo> getDataMarkers(
            net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager templates,
            BlockPos origin, Rotation rotation, boolean transformed) {
        // SinglePoolElement runs markers through the placement processors too. Its
        // STRUCTURE_BLOCK ignore processor would discard every DATA marker unless
        // the marker's transport state is air; keep position/NBT for native rotation.
        return super.getDataMarkers(templates, origin, rotation, transformed).stream()
                .map(marker -> new StructureTemplate.StructureBlockInfo(marker.pos, Blocks.AIR.defaultBlockState(), marker.nbt)).toList();
    }
    @Override public boolean place(net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager templates,
                                    net.minecraft.world.level.WorldGenLevel level, net.minecraft.world.level.StructureManager structures,
                                    net.minecraft.world.level.chunk.ChunkGenerator generator, BlockPos origin, BlockPos reference,
                                    Rotation rotation, BoundingBox clip, RandomSource random, boolean keepJigsaws) {
        boolean placed = super.place(templates, level, structures, generator, origin, reference, rotation, clip, random, keepJigsaws);
        if (!placed) return false;
        for (int x = 0; x < 9; x++) for (int z = 1; z <= 7; z++) {
            BlockPos floor = origin.offset(new BlockPos(x, 0, z).rotate(rotation));
            for (BlockPos pos = floor.below(); pos.getY() >= level.getMinBuildHeight() && clip.isInside(pos)
                    && level.ensureCanWrite(pos) && (level.isEmptyBlock(pos) || !level.getFluidState(pos).isEmpty()); pos = pos.below())
                level.setBlock(pos, Blocks.COBBLESTONE.defaultBlockState(), 18);
        }
        return true;
    }
    @Override public void handleDataMarker(LevelAccessor level, StructureTemplate.StructureBlockInfo marker, BlockPos origin,
                                           Rotation rotation, RandomSource random, BoundingBox clip) {
        handleDataMarker(level, marker, origin, rotation, random, clip, VillageArchivistPools.settings());
    }
    public void handleDataMarker(LevelAccessor level, StructureTemplate.StructureBlockInfo marker, BlockPos origin,
                                 Rotation rotation, RandomSource random, BoundingBox clip, VillageArchivistPools.Settings settings) {
        if (!clip.isInside(marker.pos) || marker.nbt == null) return;
        String action = marker.nbt.getString("metadata");
        if (action.equals("lectern")) {
            if (level.getBlockEntity(marker.pos) instanceof MystLecternBlockEntity existing && !existing.getBook().isEmpty()) return;
            level.setBlock(marker.pos, ExplorationRegistry.LECTERN.get().defaultBlockState()
                    .setValue(MystLecternBlock.FACING, rotation.rotate(Direction.EAST)), 18);
            if (level.getBlockEntity(marker.pos) instanceof MystLecternBlockEntity lectern && level instanceof ServerLevelAccessor server)
                lectern.setBook(ExplorationPages.sampleLectern(server.getLevel().registryAccess(), random));
        } else if (action.equals("desk_head") || action.equals("desk_foot")) {
            if (settings.villageDeskGen()) level.setBlock(marker.pos, WritingDeskRegistry.WRITING_DESK.get().defaultBlockState()
                    .setValue(WritingDeskBlock.FACING, rotation.rotate(Direction.SOUTH))
                    .setValue(WritingDeskBlock.FOOT, action.equals("desk_foot")), 18);
            else level.setBlock(marker.pos, Blocks.AIR.defaultBlockState(), 18);
        } else if (action.equals("archivist")) {
            level.setBlock(marker.pos, Blocks.AIR.defaultBlockState(), 18);
            if (!settings.archivistEnabled() || !(level instanceof ServerLevelAccessor server)) return;
            Villager villager = EntityType.VILLAGER.create(server.getLevel());
            if (villager == null) return;
            villager.moveTo(marker.pos.getX() + .5, marker.pos.getY(), marker.pos.getZ() + .5, 0, 0);
            villager.finalizeSpawn(server, level.getCurrentDifficultyAt(marker.pos), MobSpawnType.STRUCTURE, null, null);
            villager.setVillagerData(new VillagerData(VillagerType.byBiome(level.getBiome(marker.pos)), ExplorationRegistry.ARCHIVIST.get(), 1));
            // Native ResetProfession erases XP-zero novices without a job-site memory.
            // Keep the classic career even when the optional desk is disabled.
            villager.setVillagerXp(1);
            // MCA preserves tags but not XP while replacing native villagers asynchronously.
            villager.addTag(ArchivistCareer.INITIALIZE);
            villager.setPersistenceRequired();
            server.addFreshEntityWithPassengers(villager);
        }
    }
}
