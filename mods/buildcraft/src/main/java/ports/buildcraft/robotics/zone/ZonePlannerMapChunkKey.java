package ports.buildcraft.robotics.zone;

import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Upstream {@code ZonePlannerMapChunkKey}. One map slice is {@link #LEVEL_HEIGHT} blocks tall. */
public final class ZonePlannerMapChunkKey {
    public static final int LEVEL_HEIGHT = 32;
    /** Upstream stopped its ground search at the 1.12 height limit. */
    public static final int SEARCH_TOP = 255;

    /** The lowest slice that still meets this world's build height. */
    public static int minSlice(Level level) {
        return Math.floorDiv(level.getMinBuildHeight(), LEVEL_HEIGHT);
    }

    /** The highest slice that still meets this world's build height. */
    public static int maxSlice(Level level) {
        return Math.floorDiv(Math.max(level.getMinBuildHeight(), level.getMaxBuildHeight() - 1), LEVEL_HEIGHT);
    }

    public final ChunkPos chunkPos;
    public final int dimensionId;
    public final int level;
    private final int hash;

    public ZonePlannerMapChunkKey(ChunkPos chunkPos, int dimensionId, int level) {
        this.chunkPos = chunkPos;
        this.dimensionId = dimensionId;
        this.level = level;
        hash = Arrays.hashCode(new int[] {chunkPos.x, chunkPos.z, dimensionId, level});
    }

    public static ZonePlannerMapChunkKey read(FriendlyByteBuf buffer) {
        return new ZonePlannerMapChunkKey(new ChunkPos(buffer.readInt(), buffer.readInt()), buffer.readInt(), buffer.readInt());
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeInt(chunkPos.x);
        buffer.writeInt(chunkPos.z);
        buffer.writeInt(dimensionId);
        buffer.writeInt(level);
    }

    /** Overworld, nether and end keep upstream's dimension numbers; any other dimension uses its id's hash. */
    public static int dimensionId(Level level) {
        ResourceKey<Level> dimension = level.dimension();
        if (dimension == Level.OVERWORLD) return 0;
        if (dimension == Level.NETHER) return -1;
        if (dimension == Level.END) return 1;
        return dimension.location().hashCode();
    }

    /**
     * Upstream {@code TileZonePlanner.getLevel}: walk up from {@code start} to the first block with a solid
     * bottom, or y 255, and take that height's slice.
     */
    public static int levelOf(Level level, BlockPos start) {
        BlockPos pos = start;
        while (pos.getY() < SEARCH_TOP) {
            BlockState state = level.getBlockState(pos);
            if (state.isFaceSturdy(level, pos, Direction.DOWN)) break;
            pos = pos.above();
        }
        return Math.floorDiv(pos.getY(), LEVEL_HEIGHT);
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) return true;
        if (!(other instanceof ZonePlannerMapChunkKey key)) return false;
        return dimensionId == key.dimensionId && level == key.level && chunkPos.x == key.chunkPos.x && chunkPos.z == key.chunkPos.z;
    }

    @Override
    public int hashCode() {
        return hash;
    }
}
