package ports.jurassicraft.vehicle;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.RecordItem;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

/** Shared server-authoritative controls and passenger behavior for JurassiCraft vehicles. */
public abstract class VehicleEntity extends Entity {
    public static final int FORWARD = VehiclePhysics.FORWARD, BACK = VehiclePhysics.BACK, LEFT = VehiclePhysics.LEFT,
            RIGHT = VehiclePhysics.RIGHT, UP = VehiclePhysics.UP, DOWN = VehiclePhysics.DOWN,
            ROTATE_LEFT = VehiclePhysics.ROTATE_LEFT, ROTATE_RIGHT = VehiclePhysics.ROTATE_RIGHT;
    private static final int CONTROL_MASK = FORWARD | BACK | LEFT | RIGHT | UP | DOWN | ROTATE_LEFT | ROTATE_RIGHT;
    private static final float MAX_DAMAGE = 40.0F;
    private static final DamageSource[] CAR_DAMAGE = {
            new DamageSource("jurassic.car0"), new DamageSource("jurassic.car1"), new DamageSource("jurassic.car2")
    };
    private static final EntityDataAccessor<Float> DAMAGE = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<ItemStack> RECORD = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<CompoundTag> SEATS = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.COMPOUND_TAG);
    private int controls;
    private int controlAge;
    private UUID controlOwner;
    private int lerpSteps;
    private double lerpX, lerpY, lerpZ;
    private float lerpYaw, lerpPitch;
    private float helicopterYawRate;
    private double previousSpeed;
    private float repairPending;
    private int repairCooldown = 40;

    protected VehicleEntity(EntityType<? extends VehicleEntity> type, Level level) {
        super(type, level);
        blocksBuilding = true;
        maxUpStep = 1.0F;
    }
    protected abstract Item vehicleItem();
    protected abstract double[][] seatOffsets();
    protected boolean flies() { return false; }
    /** Return true when a subclass handled movement, for example on a tour rail. */
    protected boolean tickSpecialMovement(int controls) { return false; }
    @Override protected void defineSynchedData() {
        entityData.define(DAMAGE, 0F);
        entityData.define(RECORD, ItemStack.EMPTY);
        entityData.define(SEATS, new CompoundTag());
    }
    public float damageFraction() { return entityData.get(DAMAGE) / MAX_DAMAGE; }
    public ItemStack record() { return entityData.get(RECORD).copy(); }
    /** A seated passenger can swap a main-hand record, or retrieve it with an empty hand. */
    public boolean swapRecord(Player player) {
        if (level.isClientSide || player.getVehicle() != this || !hasPassenger(player)) return false;
        ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (!held.isEmpty() && !(held.getItem() instanceof RecordItem)) return false;
        ItemStack previous = record();
        if (held.isEmpty() && previous.isEmpty()) return false;
        ItemStack inserted = ItemStack.EMPTY;
        if (!held.isEmpty()) {
            inserted = held.copy();
            inserted.setCount(1);
        }
        if (held.getCount() <= 1) {
            player.setItemInHand(InteractionHand.MAIN_HAND, previous);
        } else {
            held.shrink(1);
            if (!previous.isEmpty() && !player.getInventory().add(previous)) player.drop(previous, false);
        }
        entityData.set(RECORD, inserted);
        VehicleNetwork.broadcastRecord(this, inserted);
        return true;
    }
    @Override public boolean isPickable() { return true; }
    @Override public boolean isPushable() { return true; }
    @Override public boolean canBeCollidedWith() { return isAlive(); }
    @Override public boolean canCollideWith(Entity entity) { return Boat.canVehicleCollide(this, entity); }
    // Only the server simulates motion; the client sends keys instead of position packets.
    @Override public boolean isControlledByLocalInstance() { return false; }
    @Nullable @Override public Entity getControllingPassenger() {
        return passengerInSeat(0);
    }
    @Override protected boolean canAddPassenger(Entity passenger) {
        return passenger instanceof Player && getPassengers().size() < seatOffsets().length;
    }
    /** Seat identities are synchronized separately from vanilla's ordered passenger list. */
    @Nullable public Entity passengerInSeat(int seat) {
        if (seat < 0 || seat >= seatOffsets().length) return null;
        CompoundTag seats = entityData.get(SEATS);
        String key = Integer.toString(seat);
        if (!seats.hasUUID(key)) return null;
        UUID occupant = seats.getUUID(key);
        for (Entity passenger : getPassengers()) {
            if (passenger.getUUID().equals(occupant)) return passenger;
        }
        return null;
    }
    public int seatOf(Entity passenger) {
        for (int seat = 0; seat < seatOffsets().length; seat++) {
            if (passengerInSeat(seat) == passenger) return seat;
        }
        return -1;
    }
    @Override protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        if (!level.isClientSide) {
            passenger.resetFallDistance();
            for (int seat = 0; seat < seatOffsets().length; seat++) {
                if (passengerInSeat(seat) == null) {
                    assignSeat(passenger, seat);
                    break;
                }
            }
        }
    }
    /** Reject unsolicited requests, invalid indices, and occupied seats on the server. */
    public boolean changeSeat(Player passenger, int seat) {
        if (level.isClientSide || passenger.getVehicle() != this || !hasPassenger(passenger)
                || seat < 0 || seat >= seatOffsets().length || passengerInSeat(seat) != null) return false;
        return assignSeat(passenger, seat);
    }
    private boolean assignSeat(Entity passenger, int seat) {
        int oldSeat = seatOf(passenger);
        CompoundTag seats = entityData.get(SEATS).copy();
        if (oldSeat >= 0) seats.remove(Integer.toString(oldSeat));
        seats.putUUID(Integer.toString(seat), passenger.getUUID());
        entityData.set(SEATS, seats);
        if (oldSeat == 0 || seat == 0) { controls = 0; controlOwner = null; controlAge = 0; }
        return true;
    }
    @Override public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.isSecondaryUseActive()) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        return player.startRiding(this) ? InteractionResult.CONSUME : InteractionResult.PASS;
    }
    @Override public void positionRider(Entity passenger) {
        int index = seatOf(passenger);
        if (index < 0 || index >= seatOffsets().length) return;
        Vec3 seat = seatPosition(index);
        passenger.setPos(seat.x, seat.y, seat.z);
    }
    public Vec3 seatPosition(int index) {
        double[] seat = seatOffsets()[index];
        double theta = Math.toRadians(getYRot());
        return new Vec3(getX() + Math.cos(theta) * seat[0] - Math.sin(theta) * seat[2],
                getY() + seat[1], getZ() + Math.sin(theta) * seat[0] + Math.cos(theta) * seat[2]);
    }
    /** Reject input from non-drivers and clear it if packets stop arriving. */
    public boolean acceptControls(Player player, int state) {
        if (level.isClientSide || getControllingPassenger() != player) return false;
        controls = state & CONTROL_MASK;
        controlOwner = player.getUUID();
        controlAge = 0;
        return true;
    }
    @Override protected void removePassenger(Entity passenger) {
        if (!level.isClientSide) {
            int seat = seatOf(passenger);
            if (seat >= 0) {
                CompoundTag seats = entityData.get(SEATS).copy();
                seats.remove(Integer.toString(seat));
                entityData.set(SEATS, seats);
                if (seat == 0) { controls = 0; controlOwner = null; controlAge = 0; }
            }
        }
        super.removePassenger(passenger);
    }
    @Override public void tick() {
        super.tick();
        if (level.isClientSide) {
            if (lerpSteps > 0) {
                setPos(getX() + (lerpX - getX()) / lerpSteps, getY() + (lerpY - getY()) / lerpSteps, getZ() + (lerpZ - getZ()) / lerpSteps);
                setRot(Mth.rotLerp(1.0F / lerpSteps, getYRot(), lerpYaw), getXRot() + (lerpPitch - getXRot()) / lerpSteps);
                lerpSteps--;
            }
            return;
        }
        Entity driver = getControllingPassenger();
        if (driver == null || !driver.getUUID().equals(controlOwner) || ++controlAge > 30) controls = 0;
        // Riders should not carry fall distance from before mounting through a trip.
        for (Entity passenger : getPassengers()) passenger.resetFallDistance();
        if (!tickSpecialMovement(controls)) {
            // Use the prior tick's actual movement, so an animal that stops the
            // vehicle at the point of contact is still struck at approach speed.
            runOverEntities(previousSpeed);
            if (isInWater()) {
                controls = 0;
                // Copy the list because a drowned rider can dismount while taking damage.
                for (Entity passenger : java.util.List.copyOf(getPassengers())) passenger.hurt(DamageSource.DROWN, .5F);
                hurt(DamageSource.DROWN, .25F);
                if (isRemoved()) return;
            }
            updateRepair();
            VehicleObstructions.clear(this, VehicleConfig.DESTROY_BLOCKS.get());
            Vec3 start = position();
            drive();
            previousSpeed = position().distanceTo(start);
        }
    }
    protected void updateRepair() {
        if (repairCooldown > 0) repairCooldown--;
        else if (repairPending > 0) {
            float damage = Math.max(0, entityData.get(DAMAGE) - 1);
            entityData.set(DAMAGE, damage);
            repairPending = damage == 0 ? 0 : repairPending - 1;
        }
    }
    private void runOverEntities(double speed) {
        if (speed <= .1) return;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(.1),
                entity -> entity.isAlive() && !entity.isSpectator() && !hasPassenger(entity))) {
            target.hurt(CAR_DAMAGE[random.nextInt(CAR_DAMAGE.length)], (float)speed);
        }
    }
    protected void drive() {
        Vec3 old = getDeltaMovement();
        double theta = Math.toRadians(getYRot());
        double speed = old.x * -Math.sin(theta) + old.z * Math.cos(theta);
        double lateral = 0;
        if (flies()) {
            boolean inFlight = driverPresent() && !isInWater() && (!isOnGround() || (controls & UP) != 0);
            int flightControls = inFlight ? controls : 0;
            speed = VehiclePhysics.speed(speed, flightControls, isOnGround(), true);
            lateral = VehiclePhysics.helicopterLateral(old.x * Math.cos(theta) + old.z * Math.sin(theta), flightControls);
            helicopterYawRate = inFlight && !isOnGround() ? VehiclePhysics.helicopterYaw(helicopterYawRate, flightControls) : 0;
            setYRot(getYRot() + helicopterYawRate);
        } else {
            speed = VehiclePhysics.speed(speed, controls, isOnGround(), false);
            setYRot(getYRot() + VehiclePhysics.steering(speed, controls, false));
        }
        theta = Math.toRadians(getYRot());
        double vertical = VehiclePhysics.vertical(old.y, controls, flies() && driverPresent() && !isInWater(), isNoGravity());
        setDeltaMovement(-Math.sin(theta) * speed + Math.cos(theta) * lateral, vertical,
                Math.cos(theta) * speed + Math.sin(theta) * lateral);
        move(MoverType.SELF, getDeltaMovement());
        if (horizontalCollision) setDeltaMovement(0, getDeltaMovement().y, 0);
        if (verticalCollision) setDeltaMovement(getDeltaMovement().x, 0, getDeltaMovement().z);
    }
    protected final boolean driverPresent() { return getControllingPassenger() != null; }
    protected final int controlState() { return controls; }
    protected boolean preventsFallDamage() { return false; }
    @Override public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        if (level.isClientSide || preventsFallDamage()) return false;
        float damage = Mth.ceil((distance - 3F) * multiplier) * 1.25F;
        return damage > 0 && hurt(source, damage);
    }
    @Override public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps, boolean teleport) {
        lerpX = x; lerpY = y; lerpZ = z; lerpYaw = yaw; lerpPitch = pitch;
        lerpSteps = teleport ? 1 : Math.max(1, Math.min(steps, 3));
    }
    @Override public boolean hurt(DamageSource source, float amount) {
        if (isInvulnerableTo(source) || amount <= 0) return false;
        if (level.isClientSide) return true;
        boolean creative = source.getEntity() instanceof Player player && player.getAbilities().instabuild;
        if (source.getEntity() instanceof Player && !creative) {
            amount *= 10;
            repairPending += amount;
            repairCooldown = 40;
        }
        float damage = entityData.get(DAMAGE) + amount;
        entityData.set(DAMAGE, damage);
        if (creative || damage >= MAX_DAMAGE) {
            ejectPassengers();
            if (level.getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
                if (!creative) spawnAtLocation(vehicleItem());
                ItemStack record = record();
                if (!record.isEmpty()) spawnAtLocation(record);
            }
            onDestroyed();
            discard();
        }
        return true;
    }
    protected void onDestroyed() {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("Damage", entityData.get(DAMAGE));
        tag.putFloat("RepairPending", Math.max(0, repairPending));
        tag.putInt("RepairCooldown", repairCooldown);
        if (!record().isEmpty()) tag.put("RecordItem", record().save(new CompoundTag()));
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(DAMAGE, Mth.clamp(tag.getFloat("Damage"), 0, MAX_DAMAGE));
        float pending = tag.getFloat("RepairPending");
        repairPending = Float.isFinite(pending) ? Math.max(0, pending) : 0;
        repairCooldown = tag.contains("RepairCooldown") ? Mth.clamp(tag.getInt("RepairCooldown"), 0, 40) : 40;
        ItemStack loaded = ItemStack.of(tag.getCompound("RecordItem"));
        if (!(loaded.getItem() instanceof RecordItem)) loaded = ItemStack.EMPTY;
        else loaded.setCount(1);
        entityData.set(RECORD, loaded);
    }
    @Override public Packet<?> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
}
