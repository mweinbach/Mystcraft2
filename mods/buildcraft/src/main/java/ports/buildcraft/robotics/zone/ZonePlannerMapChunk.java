package ports.buildcraft.robotics.zone;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
/**
 * Upstream {@code ZonePlannerMapChunk}: the map colour and height of each column, scanned down from the top of
 * the slice until a block with a map colour is found.
 */
public final class ZonePlannerMapChunk {
    private final Column[][] columns = new Column[16][16];

    public ZonePlannerMapChunk(Level level, ZonePlannerMapChunkKey key) {
        LevelChunk chunk = level.getChunk(key.chunkPos.x, key.chunkPos.z);
        int originX = key.chunkPos.getMinBlockX();
        int originZ = key.chunkPos.getMinBlockZ();
        int top = key.level * ZonePlannerMapChunkKey.LEVEL_HEIGHT;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = top; y > 0; y--) {
                    BlockPos pos = new BlockPos(originX + x, y, originZ + z);
                    // Upstream passed the column's local coordinates to getMapColor. 1.19 needs the world position,
                    // which also makes grass and foliage colours follow the column instead of the world's origin.
                    int colour = chunk.getBlockState(pos).getMapColor(level, pos).col;
                    if (colour != 0) {
                        columns[x][z] = new Column(y, colour);
                        break;
                    }
                }
            }
        }
    }

    public ZonePlannerMapChunk(FriendlyByteBuf buffer) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int y = buffer.readInt();
                if (y > 0) columns[x][z] = new Column(y, buffer.readInt());
            }
        }
    }

    public void write(FriendlyByteBuf buffer) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                Column column = columns[x][z];
                if (column == null) {
                    buffer.writeInt(-1);
                } else {
                    buffer.writeInt(column.y);
                    buffer.writeInt(column.colour);
                }
            }
        }
    }

    public int colour(int x, int z) {
        Column column = column(x, z);
        return column == null ? -1 : column.colour;
    }

    @Nullable
    public Column column(int x, int z) {
        return columns[x & 15][z & 15];
    }

    /** Upstream {@code MapColourData}: the column's surface height and its map colour, without an alpha. */
    public record Column(int y, int colour) {}
}
