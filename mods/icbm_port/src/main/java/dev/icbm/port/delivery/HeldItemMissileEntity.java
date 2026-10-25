package dev.icbm.port.delivery;

import net.minecraft.block.Block;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.*;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** Held-item impact subset: tool mining/striking, block placement, and intact recovery. */
public final class HeldItemMissileEntity extends ThrownItemEntity
    implements dev.icbm.port.flight.FlightTracked {
  private ItemStack missile = ItemStack.EMPTY;
  private boolean recovered;
  private int flightTicks;
  private int fuel = dev.icbm.port.flight.FlightProfile.CRUISE_FUEL;
  private float health = dev.icbm.port.flight.FlightProfile.health(2);
  private boolean empDisabled;
  private final dev.icbm.port.flight.StagedFlight flightPlan =
      new dev.icbm.port.flight.StagedFlight();
  private boolean flightChecked;

  @Override
  public dev.icbm.port.flight.StagedFlight flightPlan() {
    return flightPlan;
  }

  public HeldItemMissileEntity(EntityType<? extends HeldItemMissileEntity> type, World world) {
    super(type, world);
    setNoGravity(true);
  }

  @Override
  protected Item getDefaultItem() {
    return DeliveryContent.HELD_ITEM_MISSILE;
  }

  public void setMissile(ItemStack missile) {
    this.missile = missile.copy();
    this.missile.setCount(1);
    setItem(this.missile);
  }

  public ItemStack getMissile() {
    return missile.copy();
  }

  public boolean isEmpDisabled() {
    return empDisabled;
  }

  public void disableByEmp(int ticks) {
    if (ticks > 0) {
      empDisabled = true;
      recover();
    }
  }

  public void configureTarget(BlockPos target) {
    flightPlan.configure(world, getPos(), target);
  }

  @Override
  public void tick() {
    if (!world.isClient && !flightChecked) {
      flightChecked = true;
      if (dev.icbm.port.flight.FlightLedger.suppressDuplicate(this, flightPlan)) {
        discard();
        return;
      }
    }
    if (!world.isClient && empDisabled) {
      recover();
      return;
    }
    if (!world.isClient && ++flightTicks > 12000) {
      recover();
      return;
    }
    if (!world.isClient && !flightPlan.targeted() && --fuel <= 0) setNoGravity(false);
    Vec3d motion = !world.isClient ? flightPlan.nextVelocity(this) : getVelocity();
    Vec3d next = getPos().add(motion);
    // As MissileEntity: only player (or unsaveable) riders keep physical flight past the boundary.
    boolean physicalOnly = !world.isClient && !dev.icbm.port.flight.FlightLedger.capturable(this);
    if (!world.isClient
        && flightPlan.targeted()
        && !physicalOnly
        && ((next.y >= dev.icbm.port.flight.FlightProfile.exitHeight(world)
                && flightPlan.stage() != dev.icbm.port.flight.StagedFlight.Stage.DESCENT)
            || !world.isChunkLoaded(new BlockPos(next)))) {
      if (dev.icbm.port.flight.FlightLedger.tryCapture(this, flightPlan)) return;
      // Admission refused (ledger full): recover the casing and cargo instead of hovering at the
      // boundary until the 12,000-tick flight limit.
      recover();
      return;
    }
    if (!world.isClient
        && (!Double.isFinite(next.x)
            || !Double.isFinite(next.y)
            || !Double.isFinite(next.z)
            || !world.isChunkLoaded(new BlockPos(next))
            || (world.isOutOfHeightLimit(new BlockPos(next))
                && !(physicalOnly && next.y >= world.getTopY()))
            || !world.getWorldBorder().contains(new BlockPos(next)))) {
      recover();
      return;
    }
    setVelocity(motion);
    super.tick();
    if (!isRemoved() && hasNoGravity()) setVelocity(motion);
    if (!world.isClient && !isRemoved() && flightPlan.arrived()) recover();
  }

  @Override
  protected void onCollision(HitResult hit) {
    super.onCollision(hit);
    if (!world.isClient && hit.getType() != HitResult.Type.MISS) impact(hit);
  }

  public void impact(HitResult hit) {
    if (world.isClient || recovered) return;
    if (empDisabled) {
      recover();
      return;
    }
    ItemStack held = HeldItemMissileItem.cargo(missile);
    var mode = HeldItemMissileItem.mode(missile);
    boolean secondaryFirst =
        mode == HeldItemMissileItem.Mode.SECONDARY
            || mode == HeldItemMissileItem.Mode.SECONDARY_FIRST;
    if (HeldItemMissileItem.allowed(held)) {
      if (secondaryFirst) held = secondary(held, hit);
      else held = primary(held, hit);
      if (mode == HeldItemMissileItem.Mode.PRIMARY_FIRST) held = secondary(held, hit);
      if (mode == HeldItemMissileItem.Mode.SECONDARY_FIRST) held = primary(held, hit);
    }
    HeldItemMissileItem.setCargo(missile, held);
    recover();
  }

  private boolean canModify(BlockPos pos) {
    return getOwner() instanceof PlayerEntity player
        && player.canModifyBlocks()
        && world.canPlayerModifyAt(player, pos);
  }

  private ItemStack primary(ItemStack held, HitResult hit) {
    if (held.isEmpty()) return held;
    if (hit instanceof EntityHitResult entityHit) {
      if (DeliveryConfig.genericInteractions() && getOwner() instanceof PlayerEntity player) {
        return PlayerOwnedItemActions.attack(player, held, hit);
      }
      var attack = new EntityAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE, a -> {});
      attack.setBaseValue(1);
      for (var modifier :
          held.getAttributeModifiers(EquipmentSlot.MAINHAND)
              .get(EntityAttributes.GENERIC_ATTACK_DAMAGE)) attack.addTemporaryModifier(modifier);
      if (entityHit
              .getEntity()
              .damage(DamageSource.thrownProjectile(this, getOwner()), (float) attack.getValue())
          && held.damage(1, random, null)) held.decrement(1);
    } else if (hit instanceof BlockHitResult blockHit
        && (held.getItem() instanceof ToolItem || held.getItem() instanceof ShearsItem)
        && canModify(blockHit.getBlockPos())) {
      BlockPos pos = blockHit.getBlockPos();
      var state = world.getBlockState(pos);
      float hardness = state.getHardness(world, pos);
      if (hardness < 0
          || (state.isToolRequired() && !held.isSuitableFor(state))
          || hardness
              > held.getMiningSpeedMultiplier(state)
                  * Math.max(.1, getVelocity().length())
                  * DeliveryConfig.blockDamageMultiplier()) return held;
      var blockEntity = world.getBlockEntity(pos);
      if (!world.removeBlock(pos, false)) return held;
      Block.dropStacks(state, (ServerWorld) world, pos, blockEntity, getOwner(), held);
      held.postMine(world, state, pos, (PlayerEntity) getOwner());
    }
    return held;
  }

  private ItemStack secondary(ItemStack held, HitResult hit) {
    if (held.isEmpty()) return held;
    if (getOwner() instanceof PlayerEntity player && DeliveryConfig.genericInteractions()) {
      if (hit instanceof BlockHitResult blockHit
          && (!canModify(blockHit.getBlockPos())
              || !canModify(blockHit.getBlockPos().offset(blockHit.getSide())))) return held;
      return PlayerOwnedItemActions.secondary(player, held, hit);
    }
    if (!(held.getItem() instanceof BlockItem item) || !(hit instanceof BlockHitResult blockHit))
      return held;
    BlockPos pos = blockHit.getBlockPos().offset(blockHit.getSide());
    if (canModify(pos))
      item.place(
          new AutomaticItemPlacementContext(
              world,
              pos,
              Direction.getFacing(getVelocity().x, getVelocity().y, getVelocity().z),
              held,
              blockHit.getSide()));
    return held;
  }

  @Override
  public boolean canHit() {
    return !isRemoved();
  }

  /** Upstream EntityProjectile#shouldCollideWith: ignore launcher seats and our own riders. */
  @Override
  protected boolean canHit(Entity entity) {
    return !(entity instanceof dev.icbm.port.entity.PlayerSeatEntity)
        && !entity.isConnectedThroughVehicle(this)
        && super.canHit(entity);
  }

  @Override
  public boolean damage(DamageSource source, float amount) {
    if (world.isClient || isRemoved() || !Float.isFinite(amount) || amount <= 0) return false;
    health -= amount;
    if (health <= 0) recover();
    return true;
  }

  public void recover() {
    if (world.isClient || recovered) return;
    recovered = true;
    if (!missile.isEmpty()) dropStack(missile);
    missile = ItemStack.EMPTY;
    discard();
  }

  @Override
  public void writeCustomDataToNbt(NbtCompound nbt) {
    super.writeCustomDataToNbt(nbt);
    nbt.put("HeldMissile", missile.writeNbt(new NbtCompound()));
    nbt.putInt("HeldFlight", flightTicks);
    nbt.putInt("HeldFuel", fuel);
    nbt.putFloat("HeldHealth", health);
    nbt.putBoolean("HeldEmpDisabled", empDisabled);
    nbt.put("HeldFlightPlan", flightPlan.writeNbt());
  }

  @Override
  public void readCustomDataFromNbt(NbtCompound nbt) {
    super.readCustomDataFromNbt(nbt);
    setMissile(ItemStack.fromNbt(nbt.getCompound("HeldMissile")));
    flightTicks = Math.max(0, Math.min(12000, nbt.getInt("HeldFlight")));
    fuel = nbt.contains("HeldFuel") ? Math.max(0, Math.min(200, nbt.getInt("HeldFuel"))) : 200;
    health =
        nbt.contains("HeldHealth") && Float.isFinite(nbt.getFloat("HeldHealth"))
            ? Math.max(0, Math.min(80, nbt.getFloat("HeldHealth")))
            : 80;
    empDisabled = nbt.getBoolean("HeldEmpDisabled");
    flightPlan.readNbt(world, nbt.getCompound("HeldFlightPlan"));
    if (!flightPlan.targeted() && nbt.contains("HeldTarget")) {
      try {
        configureTarget(BlockPos.fromLong(nbt.getLong("HeldTarget")));
      } catch (IllegalArgumentException ignored) {
      }
    }
    setNoGravity(flightPlan.targeted() || fuel > 0);
  }
}
