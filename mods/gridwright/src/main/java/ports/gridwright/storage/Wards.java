package ports.gridwright.storage;

import ports.gridwright.block.WardKind;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.level.Level;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Where the loaded wards are, per level, and what they forbid. Each loader asks here from its own
 * hook: Forge from {@code LivingSpawnEvent.CheckSpawn} and {@code EntityTeleportEvent.EnderEntity},
 * Fabric from small mixins on the same two vanilla checks.
 */
public final class Wards {

    private static final Map<Level, Map<BlockPos, WardBlockEntity>> WARDS = Collections.synchronizedMap(new WeakHashMap<>());

    private Wards() { }

    static void add(Level level, BlockPos pos, WardBlockEntity ward) {
        WARDS.computeIfAbsent(level, key -> Collections.synchronizedMap(new HashMap<>())).put(pos.immutable(), ward);
    }

    static void remove(Level level, BlockPos pos, WardBlockEntity ward) {
        Map<BlockPos, WardBlockEntity> wards = WARDS.get(level);
        if (wards != null) wards.remove(pos, ward);
    }

    /** Whether a working ward with {@code effect} covers {@code (x, y, z)}. */
    public static boolean covered(Level level, WardKind.Effect effect, double x, double y, double z) {
        Map<BlockPos, WardBlockEntity> wards = WARDS.get(level);
        if (wards == null || wards.isEmpty()) return false;
        synchronized (wards) {
            for (Map.Entry<BlockPos, WardBlockEntity> entry : wards.entrySet()) {
                WardBlockEntity ward = entry.getValue();
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

    /** Whether a ward stops {@code entity} teleporting from where it stands to {@code (x, y, z)}. */
    public static boolean deniesTeleport(Level level, Entity entity, double x, double y, double z) {
        return entity instanceof EnderMan
                && (covered(level, WardKind.Effect.TELEPORTS, entity.getX(), entity.getY(), entity.getZ())
                || covered(level, WardKind.Effect.TELEPORTS, x, y, z));
    }
}
