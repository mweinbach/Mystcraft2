package dev.icbm.port.entity;

import dev.icbm.port.IcbmPort;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.FlyingItemEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;

public final class PrimedExplosiveEntity extends Entity implements FlyingItemEntity {
    private static final TrackedData<Integer> FUSE = DataTracker.registerData(PrimedExplosiveEntity.class, TrackedDataHandlerRegistry.INTEGER);

    public PrimedExplosiveEntity(EntityType<? extends PrimedExplosiveEntity> type, World world) {
        super(type, world);
        intersectionChecked = true;
    }

    @Override protected void initDataTracker() { dataTracker.startTracking(FUSE, IcbmPort.FUSE_TICKS); }
    public int getFuse() { return dataTracker.get(FUSE); }
    public void setFuse(int fuse) { dataTracker.set(FUSE, Math.max(0, Math.min(fuse, IcbmPort.FUSE_TICKS))); }

    @Override
    public void tick() {
        super.tick();
        if (!hasNoGravity()) setVelocity(getVelocity().add(0, -0.04, 0));
        move(MovementType.SELF, getVelocity());
        setVelocity(getVelocity().multiply(0.98));
        if (onGround) setVelocity(getVelocity().multiply(0.7, -0.5, 0.7));
        if (!world.isClient) {
            setFuse(getFuse() - 1);
            if (getFuse() <= 0) {
                discard();
                world.createExplosion(this, getX(), getBodyY(0.0625), getZ(), IcbmPort.BLAST_POWER, Explosion.DestructionType.BREAK);
            }
        } else {
            world.addParticle(ParticleTypes.SMOKE, getX(), getY() + 0.5, getZ(), 0, 0, 0);
        }
    }

    @Override protected void readCustomDataFromNbt(NbtCompound nbt) { if (nbt.contains("Fuse")) setFuse(nbt.getInt("Fuse")); }
    @Override protected void writeCustomDataToNbt(NbtCompound nbt) { nbt.putInt("Fuse", getFuse()); }
    @Override public Packet<?> createSpawnPacket() { return new EntitySpawnS2CPacket(this); }
    @Override public ItemStack getStack() { return new ItemStack(IcbmPort.CONDENSED_EXPLOSIVE); }
}
