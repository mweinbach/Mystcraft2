package dev.icbm.port.entity;

import dev.icbm.port.IcbmPort;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;

/** Direct-flight prototype. Vanilla projectile ray tracing handles collisions. */
public final class MissileEntity extends ThrownItemEntity {
    private int flightTicks;

    public MissileEntity(EntityType<? extends MissileEntity> type, World world) {
        super(type, world);
        setNoGravity(true);
    }

    @Override protected Item getDefaultItem() { return IcbmPort.CONDENSED_MISSILE; }

    @Override
    public void tick() {
        // Do not force-load chunks or leave missiles flying forever after reload.
        Vec3d motion = getVelocity();
        Vec3d next = getPos().add(motion);
        if (!world.isClient && (++flightTicks > IcbmPort.MISSILE_LIFETIME
                || next.y < world.getBottomY() || next.y >= world.getTopY()
                || !world.getWorldBorder().contains(new BlockPos(next))
                || !world.isChunkLoaded(new BlockPos(next)))) {
            discard();
            return;
        }
        super.tick();
        // ThrownEntity applies drag; powered flight preserves its launch velocity.
        if (!isRemoved()) setVelocity(motion);
        if (world.isClient) world.addParticle(ParticleTypes.SMOKE, getX(), getY(), getZ(), 0, 0, 0);
    }

    @Override
    protected void onCollision(HitResult hit) {
        super.onCollision(hit);
        if (!world.isClient && !isRemoved() && hit.getType() != HitResult.Type.MISS) {
            Vec3d pos = hit.getPos();
            discard();
            world.createExplosion(this, pos.x, pos.y, pos.z, IcbmPort.BLAST_POWER, Explosion.DestructionType.BREAK);
        }
    }

    @Override public void writeCustomDataToNbt(NbtCompound nbt) { super.writeCustomDataToNbt(nbt); nbt.putInt("FlightTicks", flightTicks); }
    @Override public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        flightTicks = Math.max(0, Math.min(IcbmPort.MISSILE_LIFETIME, nbt.getInt("FlightTicks")));
        setNoGravity(true);
    }
}
