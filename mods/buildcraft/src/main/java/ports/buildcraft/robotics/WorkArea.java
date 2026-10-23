package ports.buildcraft.robotics;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ports.buildcraft.core.MapLocationItem;
import ports.buildcraft.robotics.zone.ZonePlan;

/**
 * The blocks a robot works in, written for this port: by default a cube around its home station, or what the
 * "Work in Area" gate action gives that station from a map location (an area's box, or a zone's painted columns
 * within {@link #ZONE_HALF_HEIGHT} blocks of the station's height).
 */
public record WorkArea(BlockPos min, BlockPos max, @Nullable ZonePlan zone) {
    /** How far from its home station a robot works when no gate gives it an area. */
    public static final int DEFAULT_RADIUS = 16;
    /** How far above and below its station a robot works in a painted zone, which has no height. */
    public static final int ZONE_HALF_HEIGHT = 16;
    /** The largest box a robot will scan along each axis. */
    public static final int MAX_SIZE = 64;

    public WorkArea {
        // Gate parameters come from clients, so clamp to the world's limits first: capping a box near
        // Integer.MAX_VALUE would otherwise overflow into one spanning the whole integer range.
        min = inWorld(min);
        max = inWorld(max);
        BlockPos lo = new BlockPos(Math.min(min.getX(), max.getX()), Math.min(min.getY(), max.getY()), Math.min(min.getZ(), max.getZ()));
        BlockPos hi = new BlockPos(Math.max(min.getX(), max.getX()), Math.max(min.getY(), max.getY()), Math.max(min.getZ(), max.getZ()));
        hi = new BlockPos(Math.min(hi.getX(), lo.getX() + MAX_SIZE - 1), Math.min(hi.getY(), lo.getY() + MAX_SIZE - 1),
                Math.min(hi.getZ(), lo.getZ() + MAX_SIZE - 1));
        min = lo;
        max = hi;
    }

    private static BlockPos inWorld(BlockPos pos) {
        return new BlockPos(Mth.clamp(pos.getX(), -Level.MAX_LEVEL_SIZE, Level.MAX_LEVEL_SIZE),
                Mth.clamp(pos.getY(), -Level.MAX_LEVEL_SIZE, Level.MAX_LEVEL_SIZE),
                Mth.clamp(pos.getZ(), -Level.MAX_LEVEL_SIZE, Level.MAX_LEVEL_SIZE));
    }

    public static WorkArea around(BlockPos centre, int radius) {
        return new WorkArea(centre.offset(-radius, -radius, -radius), centre.offset(radius, radius, radius), null);
    }

    /** The area a map location describes, or null for a blank map, a spot or a path. */
    @Nullable
    public static WorkArea fromMap(ItemStack stack, BlockPos station) {
        if (!(stack.getItem() instanceof MapLocationItem)) return null;
        CompoundTag tag = stack.getTag();
        if (tag == null) return null;
        return switch (MapLocationItem.Type.of(stack)) {
            case AREA -> new WorkArea(new BlockPos(tag.getInt("xMin"), tag.getInt("yMin"), tag.getInt("zMin")),
                    new BlockPos(tag.getInt("xMax"), tag.getInt("yMax"), tag.getInt("zMax")), null);
            case ZONE -> {
                ZonePlan plan = MapLocationItem.zone(stack);
                if (plan == null) yield null;
                int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
                for (ChunkPos chunk : plan.chunks()) {
                    for (int dz = 0; dz < 16; dz++) {
                        for (int dx = 0; dx < 16; dx++) {
                            int x = chunk.getMinBlockX() + dx, z = chunk.getMinBlockZ() + dz;
                            if (!plan.get(x, z)) continue;
                            minX = Math.min(minX, x);
                            minZ = Math.min(minZ, z);
                            maxX = Math.max(maxX, x);
                            maxZ = Math.max(maxZ, z);
                        }
                    }
                }
                if (minX > maxX) yield null;
                yield new WorkArea(new BlockPos(minX, station.getY() - ZONE_HALF_HEIGHT, minZ),
                        new BlockPos(maxX, station.getY() + ZONE_HALF_HEIGHT, maxZ), plan);
            }
            default -> null;
        };
    }

    public boolean contains(BlockPos pos) {
        if (pos.getX() < min.getX() || pos.getY() < min.getY() || pos.getZ() < min.getZ()) return false;
        if (pos.getX() > max.getX() || pos.getY() > max.getY() || pos.getZ() > max.getZ()) return false;
        return zone == null || zone.get(pos.getX(), pos.getZ());
    }

    public boolean contains(Vec3 point) {
        return contains(new BlockPos(point));
    }

    public AABB bounds() {
        return new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1, max.getY() + 1, max.getZ() + 1);
    }

    public Vec3 centre() {
        return bounds().getCenter();
    }

    /** Half the box's diagonal: every point of the area lies this close to its centre. */
    public double radius() {
        AABB box = bounds();
        return Math.sqrt(box.getXsize() * box.getXsize() + box.getYsize() * box.getYsize() + box.getZsize() * box.getZsize()) / 2;
    }
}
