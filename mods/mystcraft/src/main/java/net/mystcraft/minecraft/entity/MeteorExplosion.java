package net.mystcraft.minecraft.entity;

import java.util.HashSet;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.EntityDamageSource;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Source ExplosionAdvanced ray shell plus Basic/noDrop/Fire/PlaceOres effects. No vanilla TNT substitution. */
public final class MeteorExplosion {
    private MeteorExplosion() {}
    public static void explode(ServerLevel level, MeteorEntity meteor, Vec3 origin, float power, boolean fire, boolean ores) {
        var affected = new HashSet<BlockPos>();
        for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) {
            if (x != 0 && x != 15 && y != 0 && y != 15 && z != 0 && z != 15) continue;
            double dx = x / 15F * 2F - 1F, dy = y / 15F * 2F - 1F, dz = z / 15F * 2F - 1F;
            double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
            dx /= length; dy /= length; dz /= length;
            double px = origin.x, py = origin.y, pz = origin.z;
            float remaining = power * (0.7F + level.random.nextFloat() * 0.6F);
            for (; remaining > 0; remaining -= 0.3F * 0.75F) {
                BlockPos pos = new BlockPos(px, py, pz);
                if (level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos)) break;
                var state = level.getBlockState(pos);
                float resistance = Math.max(state.getBlock().getExplosionResistance(), state.getFluidState().getExplosionResistance());
                remaining -= (resistance + 0.3F) * 0.3F;
                if (remaining > 0) affected.add(pos);
                px += dx * 0.3F; py += dy * 0.3F; pz += dz * 0.3F;
            }
        }
        float radius = power * 2;
        var bounds = new AABB(Mth.floor(origin.x - radius - 1), Mth.floor(origin.y - radius - 1), Mth.floor(origin.z - radius - 1),
                Mth.floor(origin.x + radius + 1), Mth.floor(origin.y + radius + 1), Mth.floor(origin.z + radius + 1));
        var damage = new EntityDamageSource("explosion.player", meteor).setScalesWithDifficulty().setExplosion();
        for (var entity : level.getEntities(meteor, bounds)) {
            double distance = entity.position().distanceTo(origin) / radius;
            if (distance >= 1) continue;
            Vec3 direction = new Vec3(entity.getX() - origin.x, entity.getEyeY() - origin.y, entity.getZ() - origin.z);
            double length = direction.length();
            if (length == 0) continue;
            double force = (1 - distance) * Explosion.getSeenPercent(origin, entity);
            entity.hurt(damage, (int) ((force * force + force) / 2 * 8 * radius + 1));
            entity.setDeltaMovement(entity.getDeltaMovement().add(direction.scale(force / length)));
            entity.hurtMarked = true;
        }
        level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 4,
                (1 + (level.random.nextFloat() - level.random.nextFloat()) * 0.2F) * 0.7F);
        level.sendParticles(ParticleTypes.EXPLOSION, origin.x, origin.y, origin.z, 1, 0, 0, 0, 0);
        var random = new Random();
        for (BlockPos pos : affected) {
            level.removeBlock(pos, false);
            BlockPos below = pos.below();
            boolean supported = level.getBlockState(below).isSolidRender(level, below);
            if (fire && supported && random.nextInt(3) == 0) level.setBlock(pos, Blocks.FIRE.defaultBlockState(), 3);
            if (ores && random.nextInt(20) == 0 && level.isEmptyBlock(pos) && supported) {
                float selection = random.nextFloat();
                level.setBlock(pos, (selection < 0.5F ? Blocks.COAL_ORE : selection < 0.8F ? Blocks.IRON_ORE : Blocks.GOLD_ORE).defaultBlockState(), 3);
            }
        }
    }
}
