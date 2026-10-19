package ports.jurassicraft.vehicle;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.phys.AABB;

/** Original vehicles clear foliage; leaves vanish while vines use their ordinary loot. */
public final class VehicleObstructions {
    private VehicleObstructions() {}
    public static void clear(VehicleEntity vehicle, boolean enabled) {
        Level level = vehicle.level;
        if (!enabled || level.isClientSide) return;
        AABB box = vehicle.getBoundingBox();
        for (BlockPos pos : BlockPos.betweenClosed(Mth.floor(box.minX), Mth.floor(box.minY), Mth.floor(box.minZ),
                Mth.ceil(box.maxX), Mth.ceil(box.maxY), Mth.ceil(box.maxZ))) {
            if (!level.hasChunkAt(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (state.is(BlockTags.LEAVES) || state.getMaterial() == Material.LEAVES) level.destroyBlock(pos, false, vehicle);
            else if (state.getBlock() instanceof VineBlock || state.is(BlockTags.CAVE_VINES)) level.destroyBlock(pos, true, vehicle);
        }
    }
}
