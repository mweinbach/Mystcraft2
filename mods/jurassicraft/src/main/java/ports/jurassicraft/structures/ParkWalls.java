package ports.jurassicraft.structures;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * 1.12 walls connected with a low arm and raised the centre post unless the run was straight
 * under open air. 1.19 raises an uncovered connection to {@link WallSide#TALL}, and a later chunk's
 * edge update recomputes a wall from whatever neighbour has generated so far. Both placements
 * therefore keep the 1.12 shape, including walls a neighbour chunk has just rewritten.
 */
final class ParkWalls {
    private static final int KEEP_SHAPE = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final ConcurrentHashMap<StructureTemplate, Map<BlockPos, BlockState>> BLOCKS = new ConcurrentHashMap<>();

    private ParkWalls() {}

    static Map<BlockPos, BlockState> blocks(StructureTemplate template) {
        return BLOCKS.computeIfAbsent(template, ParkWalls::read);
    }

    static void correct(StructureTemplate template, BlockPos origin, StructurePlaceSettings settings,
            BoundingBox bounds, WorldGenLevel level) {
        Map<BlockPos, BlockState> blocks = blocks(template);
        for (var entry : blocks.entrySet()) {
            if (!(entry.getValue().getBlock() instanceof WallBlock)) continue;
            BlockState classic = classic(entry.getValue(), entry.getKey(), blocks)
                    .mirror(settings.getMirror()).rotate(settings.getRotation());
            BlockPos world = StructureTemplate.calculateRelativePosition(settings, entry.getKey()).offset(origin);
            if (!affects(bounds, world)) continue;
            BlockState current = level.getBlockState(world);
            if (current.getBlock() instanceof WallBlock && !current.equals(classic)) level.setBlock(world, classic, KEEP_SHAPE);
        }
    }

    private static Map<BlockPos, BlockState> read(StructureTemplate template) {
        CompoundTag saved = template.save(new CompoundTag());
        ListTag palette = saved.getList("palette", Tag.TAG_COMPOUND);
        BlockState[] states = new BlockState[palette.size()];
        for (int i = 0; i < palette.size(); i++) states[i] = NbtUtils.readBlockState(palette.getCompound(i));
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        ListTag stored = saved.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < stored.size(); i++) {
            CompoundTag block = stored.getCompound(i);
            ListTag pos = block.getList("pos", Tag.TAG_INT);
            blocks.put(new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2)), states[block.getInt("state")]);
        }
        return blocks;
    }

    private static boolean affects(BoundingBox bounds, BlockPos world) {
        return world.getX() >= bounds.minX() - 1 && world.getX() <= bounds.maxX() + 1
                && world.getY() >= bounds.minY() && world.getY() <= bounds.maxY()
                && world.getZ() >= bounds.minZ() - 1 && world.getZ() <= bounds.maxZ() + 1;
    }

    /** Low arms, and a post unless this is a straight run with nothing above it. */
    private static BlockState classic(BlockState raw, BlockPos local, Map<BlockPos, BlockState> blocks) {
        boolean north = connects(blocks.get(local.north()), Direction.NORTH);
        boolean south = connects(blocks.get(local.south()), Direction.SOUTH);
        boolean west = connects(blocks.get(local.west()), Direction.WEST);
        boolean east = connects(blocks.get(local.east()), Direction.EAST);
        boolean straight = north && south && !west && !east || east && west && !north && !south;
        BlockState above = blocks.get(local.above());
        boolean post = !(straight && (above == null || above.isAir()));
        return raw.setValue(WallBlock.NORTH_WALL, side(north)).setValue(WallBlock.SOUTH_WALL, side(south))
                .setValue(WallBlock.WEST_WALL, side(west)).setValue(WallBlock.EAST_WALL, side(east)).setValue(WallBlock.UP, post);
    }

    private static WallSide side(boolean connected) { return connected ? WallSide.LOW : WallSide.NONE; }

    private static boolean connects(BlockState neighbor, Direction toward) {
        if (neighbor == null || neighbor.isAir()) return false;
        if (neighbor.getBlock() instanceof WallBlock) return true;
        if (neighbor.getBlock() instanceof FenceGateBlock && FenceGateBlock.connectsToDirection(neighbor, toward)) return true;
        return neighbor.isFaceSturdy(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, toward.getOpposite());
    }
}
