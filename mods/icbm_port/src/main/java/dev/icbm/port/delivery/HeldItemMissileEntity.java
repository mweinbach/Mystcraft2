package dev.icbm.port.delivery;

import dev.icbm.port.IcbmPort;
import dev.icbm.port.core.GameFlightPath;
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
public final class HeldItemMissileEntity extends ThrownItemEntity {
  private ItemStack missile = ItemStack.EMPTY;
  private boolean recovered;
  private int flightTicks;
  private boolean empDisabled;
  private BlockPos target;
  private Vec3d start;
  private int duration;
  private double lift;

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
    double distance = getPos().distanceTo(Vec3d.ofCenter(target));
    if (!Double.isFinite(distance)
        || distance > 4096
        || world.isOutOfHeightLimit(target)
        || !world.getWorldBorder().contains(target))
      throw new IllegalArgumentException("Invalid target");
    this.target = target.toImmutable();
    start = getPos();
    duration = Math.max(2, Math.min(2400, (int) Math.ceil(distance / IcbmPort.MISSILE_SPEED)));
    lift =
        Math.max(
            0,
            Math.min(distance * .25, world.getTopY() - 2 - Math.max(start.y, target.getY() + .5)));
  }

  @Override
  public void tick() {
    if (!world.isClient && empDisabled) {
      recover();
      return;
    }
    if (!world.isClient
        && (++flightTicks > (target == null ? IcbmPort.MISSILE_LIFETIME : 2400)
            || !world.isChunkLoaded(getBlockPos())
            || world.isOutOfHeightLimit(getBlockPos())
            || !world.getWorldBorder().contains(getBlockPos()))) {
      recover();
      return;
    }
    Vec3d motion = getVelocity();
    if (!world.isClient && target != null) {
      var point =
          GameFlightPath.sample(
              new GameFlightPath.Point(start.x, start.y, start.z),
              new GameFlightPath.Point(target.getX() + .5, target.getY() + .5, target.getZ() + .5),
              lift,
              flightTicks / (double) duration);
      motion = new Vec3d(point.x(), point.y(), point.z()).subtract(getPos());
      setVelocity(motion);
    }
    Vec3d next = getPos().add(motion);
    if (!world.isClient
        && (!Double.isFinite(next.x)
            || !Double.isFinite(next.y)
            || !Double.isFinite(next.z)
            || !world.isChunkLoaded(new BlockPos(next))
            || world.isOutOfHeightLimit(new BlockPos(next))
            || !world.getWorldBorder().contains(new BlockPos(next)))) {
      recover();
      return;
    }
    super.tick();
    if (!isRemoved()) setVelocity(motion);
    if (!world.isClient && !isRemoved() && target != null && flightTicks >= duration) recover();
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
      if (secondaryFirst) secondary(held, hit);
      else primary(held, hit);
      if (mode == HeldItemMissileItem.Mode.PRIMARY_FIRST) secondary(held, hit);
      if (mode == HeldItemMissileItem.Mode.SECONDARY_FIRST) primary(held, hit);
    }
    HeldItemMissileItem.setCargo(missile, held);
    recover();
  }

  private boolean canModify(BlockPos pos) {
    return getOwner() instanceof PlayerEntity player
        && player.canModifyBlocks()
        && world.canPlayerModifyAt(player, pos);
  }

  private void primary(ItemStack held, HitResult hit) {
    if (held.isEmpty()) return;
    if (hit instanceof EntityHitResult entityHit
        && (held.getItem() instanceof ToolItem || held.getItem() instanceof SwordItem)) {
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
          || hardness > held.getMiningSpeedMultiplier(state) * Math.max(.1, getVelocity().length()))
        return;
      var blockEntity = world.getBlockEntity(pos);
      if (!world.removeBlock(pos, false)) return;
      Block.dropStacks(state, (ServerWorld) world, pos, blockEntity, getOwner(), held);
      held.postMine(world, state, pos, (PlayerEntity) getOwner());
    }
  }

  private void secondary(ItemStack held, HitResult hit) {
    if (held.isEmpty()
        || !(held.getItem() instanceof BlockItem item)
        || !(hit instanceof BlockHitResult blockHit)) return;
    BlockPos pos = blockHit.getBlockPos().offset(blockHit.getSide());
    if (!canModify(pos)) return;
    item.place(
        new AutomaticItemPlacementContext(
            world,
            pos,
            Direction.getFacing(getVelocity().x, getVelocity().y, getVelocity().z),
            held,
            blockHit.getSide()));
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
    nbt.putBoolean("HeldEmpDisabled", empDisabled);
    if (target != null) {
      nbt.putLong("HeldTarget", target.asLong());
      nbt.putDouble("HeldStartX", start.x);
      nbt.putDouble("HeldStartY", start.y);
      nbt.putDouble("HeldStartZ", start.z);
      nbt.putInt("HeldDuration", duration);
      nbt.putDouble("HeldLift", lift);
    }
  }

  @Override
  public void readCustomDataFromNbt(NbtCompound nbt) {
    super.readCustomDataFromNbt(nbt);
    setMissile(ItemStack.fromNbt(nbt.getCompound("HeldMissile")));
    flightTicks = Math.max(0, Math.min(2400, nbt.getInt("HeldFlight")));
    empDisabled = nbt.getBoolean("HeldEmpDisabled");
    target = null;
    if (nbt.contains("HeldTarget")) {
      var savedStart =
          new Vec3d(
              nbt.getDouble("HeldStartX"),
              nbt.getDouble("HeldStartY"),
              nbt.getDouble("HeldStartZ"));
      var savedTarget = BlockPos.fromLong(nbt.getLong("HeldTarget"));
      double savedLift = nbt.getDouble("HeldLift");
      if (Double.isFinite(savedStart.x)
          && Double.isFinite(savedStart.y)
          && Double.isFinite(savedStart.z)
          && Double.isFinite(savedLift)
          && savedStart.distanceTo(Vec3d.ofCenter(savedTarget)) <= 4096
          && !world.isOutOfHeightLimit(savedTarget)
          && world.getWorldBorder().contains(savedTarget)) {
        target = savedTarget;
        start = savedStart;
        lift = Math.max(0, Math.min(512, savedLift));
        duration = Math.max(2, Math.min(2400, nbt.getInt("HeldDuration")));
      }
    }
    setNoGravity(true);
  }
}
