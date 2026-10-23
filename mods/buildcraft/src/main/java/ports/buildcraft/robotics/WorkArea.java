package ports.buildcraft.robotics;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
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
        BlockPos lo = new BlockPos(Math.min(min.getX(), max.getX()), Math.min(min.getY(), max.getY()), Math.min(min.getZ(), max.getZ()));
        BlockPos hi = new BlockPos(Math.max(min.getX(), max.getX()), Math.max(min.getY(), max.getY()), Math.max(min.getZ(), max.getZ()));
        hi = new BlockPos(Math.min(hi.getX(), lo.getX() + MAX_SIZE - 1), Math.min(hi.getY(), lo.getY() + MAX_SIZE - 1),
                Math.min(hi.getZ(), lo.getZ() + MAX_SIZE - 1));
        min = lo;
        max = hi;
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
                if (plan == null || plan.columns().isEmpty()) yield null;
                BlockPos lo = null, hi = null;
                for (BlockPos column : plan.columns()) {
                    lo = lo == null ? column : new BlockPos(Math.min(lo.getX(), column.getX()), 0, Math.min(lo.getZ(), column.getZ()));
                    hi = hi == null ? column : new BlockPos(Math.max(hi.getX(), column.getX()), 0, Math.max(hi.getZ(), column.getZ()));
                }
                yield new WorkArea(new BlockPos(lo.getX(), station.getY() - ZONE_HALF_HEIGHT, lo.getZ()),
                        new BlockPos(hi.getX(), station.getY() + ZONE_HALF_HEIGHT, hi.getZ()), plan);
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
