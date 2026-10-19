package ports.jurassicraft.vehicle;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public final class HelicopterEntity extends VehicleEntity {
    public static final int TOGGLE_AUTOPILOT = 0, TOGGLE_ROTATION_LOCK = 1;
    private static final EntityDataAccessor<Boolean> AUTOPILOT = SynchedEntityData.defineId(HelicopterEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> ROTATION_LOCK = SynchedEntityData.defineId(HelicopterEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> ENGINE_RPM = SynchedEntityData.defineId(HelicopterEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> FLIGHT_PITCH = SynchedEntityData.defineId(HelicopterEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> FLIGHT_ROLL = SynchedEntityData.defineId(HelicopterEntity.class, EntityDataSerializers.FLOAT);
    private static final double[][] SEATS = {{0, -.23, 1.2}, {-.55, -.34, .1}, {.55, -.34, .1}, {.4, .25, -1}, {-.4, .25, -1}};
    private float yawRate;
    private boolean flightActive;
    public HelicopterEntity(EntityType<? extends HelicopterEntity> type, Level level) { super(type, level); }
    @Override protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(AUTOPILOT, true);
        entityData.define(ROTATION_LOCK, true);
        entityData.define(ENGINE_RPM, 0F);
        entityData.define(FLIGHT_PITCH, 0F);
        entityData.define(FLIGHT_ROLL, 0F);
    }
    @Override protected Item vehicleItem() { return VehicleRegistry.HELICOPTER_ITEM.get(); }
    @Override protected double[][] seatOffsets() { return SEATS; }
    @Override protected boolean flies() { return true; }
    @Override protected boolean preventsFallDamage() { return flightActive; }
    @Override protected void updateRepair() { /* upstream helicopters do not self-repair */ }
    public boolean autopilot() { return entityData.get(AUTOPILOT); }
    public boolean rotationLock() { return entityData.get(ROTATION_LOCK); }
    public float engineRpm() { return entityData.get(ENGINE_RPM); }
    public float flightPitch() { return entityData.get(FLIGHT_PITCH); }
    public float flightRoll() { return entityData.get(FLIGHT_ROLL); }
    public float hoverRpm() { return HelicopterFlight.hoverRpm(flightPitch(), flightRoll()); }
    /** Only the current pilot can change modes, and the controls shut off in water. */
    public boolean toggleFlightMode(Player player, int mode) {
        if (level.isClientSide || player != getControllingPassenger() || isInWater()) return false;
        if (mode == TOGGLE_AUTOPILOT) entityData.set(AUTOPILOT, !autopilot());
        else if (mode == TOGGLE_ROTATION_LOCK) entityData.set(ROTATION_LOCK, !rotationLock());
        else return false;
        return true;
    }
    /** Distance to the first collision surface below the aircraft, in blocks. */
    public double groundDistance() {
        Vec3 start = position().add(0, .01, 0);
        Vec3 end = new Vec3(getX(), level.getMinBuildHeight() - 1, getZ());
        var hit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
        return Math.max(0, getY() - (hit.getType() == HitResult.Type.MISS ? level.getMinBuildHeight() : hit.getLocation().y));
    }
    @Override protected void drive() {
        int controls = controlState();
        boolean pilot = driverPresent(), water = isInWater(), airborne = !isOnGround();
        float rpm = engineRpm(), pitch = flightPitch(), roll = flightRoll();
        double ground = groundDistance();
        // Upstream enters its flying state when the pilot requests lift, and
        // keeps that state until landing even if the pilot leaves the seat.
        if (!water && pilot && damageFraction() < .95F && (controls & UP) != 0) flightActive = true;
        if (water) rpm /= 8;
        else if (pilot && damageFraction() < .95F) {
            if ((controls & UP) != 0) rpm += HelicopterFlight.THROTTLE;
            else if ((controls & DOWN) != 0 && airborne) rpm -= HelicopterFlight.THROTTLE;
            else if (autopilot() && airborne) rpm = HelicopterFlight.approach(rpm,
                    Math.min(HelicopterFlight.MAX_RPM, HelicopterFlight.hoverRpm(pitch, roll)), 1.5F);
            else if (autopilot() && !airborne) rpm -= 1;
        } else rpm -= 1;
        rpm = Mth.clamp(rpm, 0, HelicopterFlight.MAX_RPM);

        boolean crashing = !water && airborne && damageFraction() >= .95F && ground > 1.5;
        if (!water && airborne && rpm > 10 && pilot && damageFraction() < .95F) {
            int forward = ((controls & FORWARD) != 0 ? 1 : 0) - ((controls & BACK) != 0 ? 1 : 0);
            int strafe = ((controls & LEFT) != 0 ? 1 : 0) - ((controls & RIGHT) != 0 ? 1 : 0);
            int turn = ((controls & ROTATE_RIGHT) != 0 ? 1 : 0) - ((controls & ROTATE_LEFT) != 0 ? 1 : 0);
            if (forward != 0) pitch += forward * HelicopterFlight.THROTTLE / 2;
            else if (autopilot() && rotationLock()) pitch = HelicopterFlight.approach(pitch, 0, HelicopterFlight.THROTTLE / 2);
            if (strafe != 0) roll += strafe * HelicopterFlight.THROTTLE;
            else if (autopilot() && rotationLock()) roll = HelicopterFlight.approach(roll, 0, HelicopterFlight.THROTTLE);
            if (turn != 0 && strafe == 0 && ground > .1) yawRate += turn * .1F;
            else if (autopilot()) yawRate = HelicopterFlight.approach(yawRate, 0, .05F);
            yawRate = Mth.clamp(yawRate, -2 * HelicopterFlight.THROTTLE, 2 * HelicopterFlight.THROTTLE);
            float limit = HelicopterFlight.maximumTilt(ground, rotationLock());
            pitch = Mth.clamp(Mth.wrapDegrees(pitch), -limit, limit);
            roll = Mth.clamp(roll, -limit, limit);
            setYRot(getYRot() + yawRate);
        } else if (crashing) {
            yawRate = Math.min(7, yawRate + random.nextFloat() * rpm / HelicopterFlight.MAX_RPM * .9F);
            rpm = Math.max(0, rpm - 1);
            if (pitch >= 0 && pitch < random.nextFloat() * 10 + 20) pitch += .2F;
            else if (pitch < 0 && pitch > -random.nextFloat() * 10 - 20) pitch -= .2F;
            if ((roll >= 0 && roll < random.nextFloat() * 10 + 50)
                    || (roll < 0 && roll > -random.nextFloat() * 10 - 50)) roll -= 1;
            setYRot(getYRot() + yawRate);
        }
        entityData.set(ENGINE_RPM, rpm);
        entityData.set(FLIGHT_PITCH, pitch);
        entityData.set(FLIGHT_ROLL, roll);
        Vec3 motion = HelicopterFlight.motion(getDeltaMovement(), getYRot(), rpm, pitch, roll, water, pilot);
        if (crashing) motion = motion.add(Math.sin(Math.toRadians(getYRot())) * .05, 0, Math.cos(Math.toRadians(getYRot())) * .05);
        setDeltaMovement(motion);
        move(MoverType.SELF, getDeltaMovement());
        if (horizontalCollision) setDeltaMovement(0, getDeltaMovement().y, 0);
        if (verticalCollision) setDeltaMovement(getDeltaMovement().x, 0, getDeltaMovement().z);
        if (isOnGround()) {
            flightActive = false;
            if (Math.abs(pitch) > 30 || Math.abs(roll) > 30 || Math.abs(yawRate) > .2F) {
                hurt(DamageSource.GENERIC, 40);
                return;
            }
            entityData.set(ROTATION_LOCK, true);
            entityData.set(FLIGHT_PITCH, 0F);
            yawRate = 0;
        }
        if (!water && rpm >= 1 && rotorObstructed()) {
            hurt(DamageSource.GENERIC, rpm / HelicopterFlight.MAX_RPM * 2);
            if (isRemoved()) return;
        }
        applyDownwash(rpm);
    }
    private boolean rotorObstructed() {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = -5; x < 5; x++) for (int z = -5; z < 5; z++) {
            pos.set(Mth.floor(getX() + x), Mth.floor(getY() + getBbHeight()), Mth.floor(getZ() + z));
            // The rotor never forces an unloaded chunk to load.
            if (level.hasChunkAt(pos) && !level.getBlockState(pos).isAir()) return true;
        }
        return false;
    }
    private void applyDownwash(float rpm) {
        if (rpm <= 0) return;
        double distanceToGround = groundDistance();
        if (distanceToGround >= 6) return;
        double groundPower = (1 - distanceToGround / 6) * Math.pow(rpm / HelicopterFlight.MAX_RPM, 3);
        AABB area = new AABB(getX() - 10, getY() - distanceToGround - 1, getZ() - 10,
                getX() + 10, getY() + 1, getZ() + 10);
        for (Entity other : level.getEntities(this, area, entity -> !hasPassenger(entity) && entity.getBoundingBox().getSize() <= .55)) {
            double dx = other.getX() - getX(), dz = other.getZ() - getZ();
            double distance = other.position().distanceTo(position());
            if (distance <= 1.0E-6 || distance >= 10) continue;
            double power = (1 - distance / 10) * groundPower / distance;
            other.push(dx * power, 0, dz * power);
        }
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Autopilot", autopilot()); tag.putBoolean("RotationLock", rotationLock());
        tag.putFloat("EngineRpm", engineRpm()); tag.putFloat("FlightPitch", flightPitch()); tag.putFloat("FlightRoll", flightRoll());
        tag.putFloat("YawRate", yawRate);
        tag.putBoolean("FlightActive", flightActive);
    }
    private static float savedFloat(CompoundTag tag, String key, float low, float high) {
        float value = tag.getFloat(key);
        return Float.isFinite(value) ? Mth.clamp(value, low, high) : 0;
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(AUTOPILOT, !tag.contains("Autopilot") || tag.getBoolean("Autopilot"));
        entityData.set(ROTATION_LOCK, !tag.contains("RotationLock") || tag.getBoolean("RotationLock"));
        entityData.set(ENGINE_RPM, savedFloat(tag, "EngineRpm", 0, HelicopterFlight.MAX_RPM));
        entityData.set(FLIGHT_PITCH, savedFloat(tag, "FlightPitch", -180, 180));
        entityData.set(FLIGHT_ROLL, savedFloat(tag, "FlightRoll", -180, 180));
        yawRate = savedFloat(tag, "YawRate", -7, 7);
        flightActive = tag.getBoolean("FlightActive");
    }
    @Override protected void onDestroyed() {
        if (level instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY(), getZ(), 1, 0, 0, 0, 0);
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.NEUTRAL, 4F,
                    (1F + (random.nextFloat() - random.nextFloat()) * .2F) * .7F);
        }
    }
}
