package ports.jurassicraft.plants;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Avoids loading neighboring chunks during plant growth and decay checks. */
final class PlantChunks {
    private PlantChunks() {}
    static boolean isAreaLoaded(ServerLevel level, BlockPos pos, int radius) {
        return level.hasChunksAt(pos.getX() - radius, pos.getZ() - radius,
                pos.getX() + radius, pos.getZ() + radius);
    }
}
