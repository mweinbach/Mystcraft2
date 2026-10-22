package ports.jurassicraft.structures;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.HugeMushroomBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;

/**
 * A one-shot {@code placeInWorld} updates neighbours as each block is placed. A chunk slice cannot:
 * the next slice is not there yet, and telling neighbours early pops vines. Vanilla
 * {@code markPosForPostprocessing} runs one {@code updateFromNeighbourShapes} when the chunk is about
 * to tick. That single update still sees the raw template shape, and repeating it until it stops
 * changing raises both arms of a wall and drops the post. The one-shot result is the placement
 * cascade, so once every chunk of the piece is loaded that cascade is replayed.
 *
 * <p>The stored result writes fences, walls, vines, mushrooms and levers only. Other blocks are put
 * back to the state they had, and a chest's saved tag is loaded again. The loot table is forgotten
 * before that chest is cleared, so the table is not rolled and its items are not dropped. Entities
 * are not placed. A block the player has broken or replaced is left as it is, and a chunk whose
 * inhabited time is already above zero is not written. The piece records that this has run, so a
 * later load does not run it again.
 */
public final class ParkEdges {
    /**
     * Place the template state without telling neighbours, so a missing slice cannot pop a vine.
     * Light is left alone too: mushroom survival reads skylight, and an early light queue would
     * darken the interior before the shapes are replayed.
     */
    static final int PLACED = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_LIGHT;
    /** Same neighbour pass as a one-shot, without feeding the light engine until the shapes are done. */
    static final int SHAPED = Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_LIGHT;
    /** Final shape write inside {@code placeInWorld}: known shape, so the write itself does not cascade. */
    private static final int SHAPE_FLAGS = (SHAPED & ~Block.UPDATE_NEIGHBORS) | Block.UPDATE_KNOWN_SHAPE;
    private static final int QUIET = PLACED | Block.UPDATE_SUPPRESS_DROPS;
    private static final ConcurrentHashMap<StructureTemplate, List<StructureTemplate.StructureBlockInfo>> BLOCKS =
            new ConcurrentHashMap<>();

    private ParkEdges() {}

    static boolean sensitive(BlockState state) {
        Block block = state.getBlock();
        return block instanceof CrossCollisionBlock || block instanceof FenceGateBlock || block instanceof WallBlock
                || block instanceof VineBlock || block instanceof MushroomBlock || block instanceof HugeMushroomBlock
                || block instanceof LeverBlock;
    }

    /**
     * Replay the one-shot neighbour cascade for shape blocks inside {@code slice}. A fence that is
     * now air, and every chest, stays as it is.
     */
    static void settleSlice(ServerLevel level, StructureTemplate template, BlockPos origin, StructurePlaceSettings settings,
            BoundingBox slice) {
        settle(level, template, origin, settings, slice, false);
    }

    /** Same cascade as {@link #settleSlice}, for a live world. Inhabited chunks are not written. */
    static void settleFootprint(ServerLevel level, StructureTemplate template, BlockPos origin, StructurePlaceSettings settings,
            BoundingBox footprint) {
        settle(level, template, origin, settings, footprint, true);
    }

    /**
     * Called when a chunk is about to tick for the first time, or has just loaded and no player has
     * spent time in it. Pieces whose whole footprint is now loaded, and which have not settled
     * before, replay the cascade. A chunk a player has already inhabited does not start it, and the
     * cascade does not write those chunks.
     */
    public static void settleLoaded(LevelChunk chunk) {
        if (!(chunk.getLevel() instanceof ServerLevel level)) return;
        if (chunk.getInhabitedTime() > 0) return;
        for (StructureStart start : chunk.getAllStarts().values()) {
            if (start == null || !start.isValid()) continue;
            for (StructurePiece piece : start.getPieces()) {
                if (piece instanceof ParkStructurePiece park) park.settleEdges(level);
            }
        }
    }

    static boolean covers(BoundingBox bounds, BoundingBox footprint) {
        return bounds.isInside(new BlockPos(footprint.minX(), footprint.minY(), footprint.minZ()))
                && bounds.isInside(new BlockPos(footprint.maxX(), footprint.maxY(), footprint.maxZ()));
    }

    /**
     * {@code placeInWorld} only shape-updates a block when {@code setBlock} reports a change. The
     * same ordered cascade has to see stairs, chests and furnaces appear, or a wall settles on the
     * wrong side and a vine that should have popped stays up. Those blocks are put back afterwards.
     * A broken or replaced block is not the template block, and a chunk the player has inhabited is
     * not replayed. Entities are not part of this pass.
     */
    private static void settle(ServerLevel level, StructureTemplate template, BlockPos origin, StructurePlaceSettings settings,
            BoundingBox bounds, boolean skipInhabited) {
        StructurePlaceSettings placing = new StructurePlaceSettings()
                .setRotation(settings.getRotation()).setMirror(settings.getMirror())
                .setRotationPivot(settings.getRotationPivot())
                .setIgnoreEntities(true).setKnownShape(false).setBoundingBox(bounds)
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
        List<StructureTemplate.StructureBlockInfo> infos = StructureTemplate.processBlockInfos(
                level, origin, origin, placing, BLOCKS.computeIfAbsent(template, ParkEdges::readBlocks));
        if (infos.isEmpty()) return;
        Map<BlockPos, BlockState> restore = new LinkedHashMap<>();
        Map<BlockPos, CompoundTag> blockEntities = new LinkedHashMap<>();
        List<StructureTemplate.StructureBlockInfo> replay = new ArrayList<>();
        for (StructureTemplate.StructureBlockInfo info : infos) {
            if (!bounds.isInside(info.pos)) continue;
            BlockState current = level.getBlockState(info.pos);
            BlockState shaped = info.state.mirror(placing.getMirror()).rotate(placing.getRotation());
            BlockEntity blockEntity = level.getBlockEntity(info.pos);
            boolean inhabited = skipInhabited && inhabited(level, info.pos);
            // A broken fence is air, so it is not the template block and is not replayed.
            boolean sameBlock = current.is(shaped.getBlock());
            if (blockEntity != null || inhabited || !sameBlock || !sensitive(current)) restore.put(info.pos, current);
            if (blockEntity != null) blockEntities.put(info.pos, blockEntity.saveWithId());
            if (!inhabited && sameBlock) replay.add(info);
        }
        for (StructureTemplate.StructureBlockInfo info : replay) {
            if (level.getBlockState(info.pos).isAir()) continue;
            clearQuietly(level, info.pos);
        }
        List<BlockPos> placed = new ArrayList<>();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (StructureTemplate.StructureBlockInfo info : replay) {
            BlockState shaped = info.state.mirror(placing.getMirror()).rotate(placing.getRotation());
            if (!level.setBlock(info.pos, shaped, SHAPED)) continue;
            placed.add(info.pos);
            minX = Math.min(minX, info.pos.getX()); minY = Math.min(minY, info.pos.getY()); minZ = Math.min(minZ, info.pos.getZ());
            maxX = Math.max(maxX, info.pos.getX()); maxY = Math.max(maxY, info.pos.getY()); maxZ = Math.max(maxZ, info.pos.getZ());
        }
        if (!placed.isEmpty()) {
            BitSetDiscreteVoxelShape shape = new BitSetDiscreteVoxelShape(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);
            for (BlockPos pos : placed) shape.fill(pos.getX() - minX, pos.getY() - minY, pos.getZ() - minZ);
            StructureTemplate.updateShapeAtEdge(level, SHAPED, shape, minX, minY, minZ);
            for (BlockPos pos : placed) {
                BlockState current = level.getBlockState(pos);
                BlockState updated = Block.updateFromNeighbourShapes(current, level, pos);
                if (current != updated) level.setBlock(pos, updated, SHAPE_FLAGS);
                level.blockUpdated(pos, updated.getBlock());
            }
        }
        for (Map.Entry<BlockPos, BlockState> entry : restore.entrySet())
            level.setBlock(entry.getKey(), entry.getValue(), PLACED);
        for (Map.Entry<BlockPos, CompoundTag> entry : blockEntities.entrySet()) {
            BlockEntity blockEntity = level.getBlockEntity(entry.getKey());
            if (blockEntity == null) continue;
            if (!blockEntity.saveWithId().equals(entry.getValue())) blockEntity.load(entry.getValue());
        }
    }

    /**
     * {@code setBlock} to air runs {@code onRemove}, and a loot chest drops whatever {@code getItem}
     * unpacks. The section is cleared directly so that removal hook never runs. The block entity is
     * detached and its pending tag is dropped. The saved tag is loaded back after the cascade.
     */
    private static void clearQuietly(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkAt(pos);
        discardPending(chunk, pos);
        BlockEntity blockEntity = chunk.getBlockEntity(pos);
        disarm(blockEntity);
        chunk.getBlockEntities().remove(pos);
        if (blockEntity != null) blockEntity.setRemoved();
        chunk.removeBlockEntity(pos);
        LevelChunkSection section = chunk.getSection(level.getSectionIndex(pos.getY()));
        section.setBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15, Blocks.AIR.defaultBlockState());
    }

    /** A promoted pending tag would rebuild the loot table inside {@code onRemove} and drop it. */
    @SuppressWarnings("unchecked")
    private static void discardPending(LevelChunk chunk, BlockPos pos) {
        try {
            var field = ChunkAccess.class.getDeclaredField("pendingBlockEntities");
            field.setAccessible(true);
            ((Map<BlockPos, ?>) field.get(chunk)).remove(pos);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not keep chest loot out of the shape cascade", exception);
        }
    }

    private static void disarm(BlockEntity blockEntity) {
        if (blockEntity == null) return;
        if (blockEntity instanceof RandomizableContainerBlockEntity) {
            try {
                var table = RandomizableContainerBlockEntity.class.getDeclaredField("lootTable");
                table.setAccessible(true);
                table.set(blockEntity, null);
                var seed = RandomizableContainerBlockEntity.class.getDeclaredField("lootTableSeed");
                seed.setAccessible(true);
                seed.setLong(blockEntity, 0L);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Could not keep chest loot out of the shape cascade", exception);
            }
        }
        if (blockEntity instanceof Container container) container.clearContent();
    }

    private static boolean inhabited(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk != null && chunk.getInhabitedTime() > 0;
    }

    /** Template order, which is the order {@code placeInWorld} places blocks. Loading does not sort it. */
    private static List<StructureTemplate.StructureBlockInfo> readBlocks(StructureTemplate template) {
        CompoundTag saved = template.save(new CompoundTag());
        ListTag palette = saved.getList("palette", Tag.TAG_COMPOUND);
        BlockState[] states = new BlockState[palette.size()];
        for (int i = 0; i < palette.size(); i++) states[i] = NbtUtils.readBlockState(palette.getCompound(i));
        ListTag stored = saved.getList("blocks", Tag.TAG_COMPOUND);
        List<StructureTemplate.StructureBlockInfo> blocks = new ArrayList<>(stored.size());
        for (int i = 0; i < stored.size(); i++) {
            CompoundTag block = stored.getCompound(i);
            ListTag pos = block.getList("pos", Tag.TAG_INT);
            int state = block.getInt("state");
            if (state < 0 || state >= states.length) continue;
            CompoundTag nbt = block.contains("nbt", Tag.TAG_COMPOUND) ? block.getCompound("nbt") : null;
            blocks.add(new StructureTemplate.StructureBlockInfo(
                    new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2)), states[state], nbt));
        }
        return blocks;
    }
}
