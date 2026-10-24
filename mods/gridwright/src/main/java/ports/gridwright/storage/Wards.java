package ports.gridwright.storage;

import ports.gridwright.block.WardKind;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.level.Level;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Where the loaded wards are, per server level, and what they forbid. Each loader asks here from its
 * own hook: Forge from {@code LivingSpawnEvent.CheckSpawn} and {@code EntityTeleportEvent.EnderEntity},
 * Fabric from small mixins on the same two vanilla checks.
 *
 * <p>Both the levels and the wards are held weakly: a ward references its level, so a strong value
 * would keep a discarded level (and every ward in it) alive through the weak key.
 */
public final class Wards {

    private static final Map<Level, Map<BlockPos, WeakReference<WardBlockEntity>>> WARDS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private Wards() { }

    static void add(Level level, BlockPos pos, WardBlockEntity ward) {
        if (level.isClientSide) return;
        WARDS.computeIfAbsent(level, key -> Collections.synchronizedMap(new HashMap<>()))
                .put(pos.immutable(), new WeakReference<>(ward));
    }

    static void remove(Level level, BlockPos pos, WardBlockEntity ward) {
        Map<BlockPos, WeakReference<WardBlockEntity>> wards = WARDS.get(level);
        if (wards == null) return;
        synchronized (wards) {
            WeakReference<WardBlockEntity> held = wards.get(pos);
            if (held != null && (held.get() == ward || held.get() == null)) wards.remove(pos);
        }
    }

    /** Whether a working ward with {@code effect} covers {@code (x, y, z)}. */
    public static boolean covered(Level level, WardKind.Effect effect, double x, double y, double z) {
        Map<BlockPos, WeakReference<WardBlockEntity>> wards = WARDS.get(level);
        if (wards == null || wards.isEmpty()) return false;
        synchronized (wards) {
            var entries = wards.entrySet().iterator();
            while (entries.hasNext()) {
                var entry = entries.next();
                WardBlockEntity ward = entry.getValue().get();
                if (ward == null) {
                    entries.remove();
                    continue;
                }
                WardKind kind = ward.kind();
                if (kind.effect() != effect || !ward.working()) continue;
                BlockPos pos = entry.getKey();
                double reach = kind.radius() + 0.5;
                if (Math.abs(x - (pos.getX() + 0.5)) <= reach && Math.abs(y - (pos.getY() + 0.5)) <= reach
                        && Math.abs(z - (pos.getZ() + 0.5)) <= reach) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Whether a ward stops {@code mob} spawning naturally where it stands. Only hostile creatures. */
    public static boolean deniesNaturalSpawn(Level level, Mob mob) {
        return mob.getType().getCategory() == MobCategory.MONSTER
                && covered(level, WardKind.Effect.SPAWNS, mob.getX(), mob.getY(), mob.getZ());
    }

    /**
     * Whether a ward stops {@code entity} teleporting from where it stands to {@code (x, y, z)}. The
     * destination is checked where the entity would actually land: a teleport drops to the first block
     * that stops movement below the requested spot, so a target above a ward can still land inside it.
     */
    public static boolean deniesTeleport(Level level, Entity entity, double x, double y, double z) {
        if (!(entity instanceof EnderMan)) return false;
        return covered(level, WardKind.Effect.TELEPORTS, entity.getX(), entity.getY(), entity.getZ())
                || covered(level, WardKind.Effect.TELEPORTS, x, y, z)
                || covered(level, WardKind.Effect.TELEPORTS, x, landingY(level, x, y, z), z);
    }

    /** The height a teleport to {@code (x, y, z)} comes to rest at, as vanilla's random teleport finds it. */
    static double landingY(Level level, double x, double y, double z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, y, z);
        if (!level.hasChunkAt(pos)) return y;
        double landing = y;
        while (pos.getY() > level.getMinBuildHeight()) {
            pos.move(0, -1, 0);
            if (level.getBlockState(pos).getMaterial().blocksMotion()) break;
            landing--;
        }
        return landing;
    }
}
