package net.mystcraft.minecraft.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.mystcraft.integration.platform.GameplayPlatform;

/** Source EntityMeteor: constant velocity, collision penetration and eight shaped no-drop bursts. */
public final class MeteorEntity extends Entity {
    private static java.util.function.Consumer<MeteorEntity> clientSound = meteor -> {};
    private static final EntityDataAccessor<Float> SCALE = SynchedEntityData.defineId(MeteorEntity.class, EntityDataSerializers.FLOAT);
    private int penetration;
    private int inGroundTime;
    public MeteorEntity(EntityType<? extends MeteorEntity> type, Level level) { super(type, level); }
    public static void installClientSound(java.util.function.Consumer<MeteorEntity> sound) { clientSound = java.util.Objects.requireNonNull(sound); }
    @Override protected void defineSynchedData() { entityData.define(SCALE, 1F); }
    public float getScale() { return entityData.get(SCALE); }
    public void setScale(float scale, int penetration) {
        if (!Float.isFinite(scale) || scale <= 0 || scale > 32 || penetration < 0 || penetration > 127)
            throw new IllegalArgumentException("Invalid meteor scale/penetration");
        entityData.set(SCALE, scale); this.penetration = penetration; refreshDimensions();
    }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key); if (SCALE.equals(key)) refreshDimensions();
    }
    @Override public EntityDimensions getDimensions(Pose pose) { return EntityDimensions.scalable(getScale(), getScale()); }
    @Override public boolean isPickable() { return true; }
    @Override public float getPickRadius() { return 1; }
    @Override public boolean shouldBeSaved() { return false; } // Original discards meteors on NBT load.
    @Override protected void readAdditionalSaveData(CompoundTag tag) { discard(); }
    @Override protected void addAdditionalSaveData(CompoundTag tag) { tag.putByte("InGround", (byte) inGroundTime); }
    @Override public Packet<?> getAddEntityPacket() { return GameplayPlatform.spawnPacket(this); }
    @Override public void tick() {
        super.tick(); setSecondsOnFire(1);
        if (tickCount == 1 && level.isClientSide) clientSound.accept(this);
        if (tickCount == 1 && !level.isClientSide)
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER,
                    10000, 0.8F + random.nextFloat() * 0.2F);
        Vec3 motion = getDeltaMovement();
        HitResult hit = level.clip(new ClipContext(position(), position().add(motion), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (hit.getType() != HitResult.Type.MISS) { ++inGroundTime; if (!level.isClientSide) impact(); }
        else inGroundTime = Math.max(0, inGroundTime - 1);
        motion = getDeltaMovement();
        setPos(getX() + motion.x, getY() + motion.y, getZ() + motion.z);
        float yaw = (float) (Mth.atan2(motion.x, motion.z) * 180 / Math.PI);
        float pitch = (float) (Mth.atan2(motion.y, Math.sqrt(motion.x * motion.x + motion.z * motion.z)) * 180 / Math.PI);
        setYRot(yRotO + Mth.wrapDegrees(yaw - yRotO) * 0.2F);
        setXRot(xRotO + Mth.wrapDegrees(pitch - xRotO) * 0.2F);
        if (level.isClientSide) {
            if (isInWater()) for (int i = 0; i < 4; i++) level.addParticle(ParticleTypes.BUBBLE,
                    getX() - motion.x * 0.25, getY() - motion.y * 0.25, getZ() - motion.z * 0.25, motion.x, motion.y, motion.z);
            level.addParticle(ParticleTypes.SMOKE, getX(), getY() + 0.5, getZ(), 0, 0, 0);
        }
    }
    private void impact() {
        setDeltaMovement(getDeltaMovement().multiply(1, 0.9, 1));
        Vec3 movement = getDeltaMovement();
        var bounds = getBoundingBox().move(movement);
        for (BlockPos pos : BlockPos.betweenClosed(Mth.floor(bounds.minX), Mth.floor(bounds.minY), Mth.floor(bounds.minZ),
                Mth.floor(bounds.maxX), Mth.floor(bounds.maxY + 5), Mth.floor(bounds.maxZ)))
            if (!level.isOutsideBuildHeight(pos) && level.hasChunkAt(pos)) level.removeBlock(pos, false);
        if (inGroundTime < penetration) return;
        float scale = getScale();
        burst(0, 0, 0, 5, false, true);
        burst(0, -scale / 10, 0, scale, false, true);
        burst(0, -scale / 5, 0, scale * 2, false, true);
        burst(0, -scale * 2 / 5, 0, scale, false, true);
        burst(scale * 4 / 5, 0, 0, scale, true, false);
        burst(-scale * 4 / 5, 0, 0, scale, true, false);
        burst(0, 0, scale * 4 / 5, scale, true, false);
        burst(0, 0, -scale * 4 / 5, scale, true, false);
        discard();
    }
    private void burst(double x, double y, double z, float power, boolean fire, boolean ores) {
        MeteorExplosion.explode((ServerLevel) level, this, position().add(x, y, z), power, fire, ores);
    }
}
