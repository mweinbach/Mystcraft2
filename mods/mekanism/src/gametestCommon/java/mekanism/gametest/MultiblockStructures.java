/*
 * Mekanism 1.19.2 port - in-world tests shared by the Forge and Fabric builds.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import java.util.ArrayList;
import java.util.List;
import mekanism.common.registries.MekanismBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * Builds a hollow multiblock shell inside a GameTest. Edges are the frame block, the faces are the wall block, and one
 * face block that is not an edge is the valve. The inside is left as air. Later slices pass their own blocks; a
 * dynamic tank is the shell this slice fills in.
 */
public final class MultiblockStructures {

    private MultiblockStructures() {
    }

    public record Shell(BlockPos origin, int sizeX, int sizeY, int sizeZ, @Nullable BlockPos valve, @Nullable Direction valveSide, List<BlockPos> blocks) {
    }

    public static Shell dynamicTank(GameTestHelper helper, BlockPos origin, int size) {
        return cuboid(helper, origin, size, size, size, MekanismBlocks.DYNAMIC_TANK.getBlock(), MekanismBlocks.STRUCTURAL_GLASS.getBlock(),
              MekanismBlocks.DYNAMIC_VALVE.getBlock());
    }

    /**
     * @param valveBlock placed on the first non-edge face block, preferring the centre of the north face. Null skips it.
     */
    public static Shell cuboid(GameTestHelper helper, BlockPos origin, int sizeX, int sizeY, int sizeZ, Block casing, Block wall,
          @Nullable Block valveBlock) {
        BlockPos valve = valvePos(origin, sizeX, sizeY, sizeZ, valveBlock != null);
        List<BlockPos> blocks = new ArrayList<>();
        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                for (int z = 0; z < sizeZ; z++) {
                    int faces = faces(x, y, z, sizeX, sizeY, sizeZ);
                    if (faces == 0) {
                        continue;
                    }
                    BlockPos pos = origin.offset(x, y, z);
                    Block block = pos.equals(valve) ? valveBlock : faces >= 2 ? casing : wall;
                    helper.setBlock(pos, block);
                    blocks.add(pos);
                }
            }
        }
        return new Shell(origin, sizeX, sizeY, sizeZ, valve, valve == null ? null : outward(origin, sizeX, sizeY, sizeZ, valve), List.copyOf(blocks));
    }

    @Nullable
    private static BlockPos valvePos(BlockPos origin, int sizeX, int sizeY, int sizeZ, boolean placeValve) {
        if (!placeValve) {
            return null;
        }
        int x = sizeX / 2;
        int y = sizeY / 2;
        if (faces(x, y, 0, sizeX, sizeY, sizeZ) == 1) {
            return origin.offset(x, y, 0);
        }
        for (int dx = 0; dx < sizeX; dx++) {
            for (int dy = 0; dy < sizeY; dy++) {
                for (int dz = 0; dz < sizeZ; dz++) {
                    if (faces(dx, dy, dz, sizeX, sizeY, sizeZ) == 1) {
                        return origin.offset(dx, dy, dz);
                    }
                }
            }
        }
        return null;
    }

    private static int faces(int x, int y, int z, int sizeX, int sizeY, int sizeZ) {
        int faces = 0;
        if (x == 0 || x == sizeX - 1) {
            faces++;
        }
        if (y == 0 || y == sizeY - 1) {
            faces++;
        }
        if (z == 0 || z == sizeZ - 1) {
            faces++;
        }
        return faces;
    }

    private static Direction outward(BlockPos origin, int sizeX, int sizeY, int sizeZ, BlockPos valve) {
        if (valve.getX() == origin.getX()) {
            return Direction.WEST;
        } else if (valve.getX() == origin.getX() + sizeX - 1) {
            return Direction.EAST;
        } else if (valve.getY() == origin.getY()) {
            return Direction.DOWN;
        } else if (valve.getY() == origin.getY() + sizeY - 1) {
            return Direction.UP;
        } else if (valve.getZ() == origin.getZ() + sizeZ - 1) {
            return Direction.SOUTH;
        }
        return Direction.NORTH;
    }
}
