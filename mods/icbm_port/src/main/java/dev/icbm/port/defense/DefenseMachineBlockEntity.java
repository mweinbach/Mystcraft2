package dev.icbm.port.defense;

import dev.icbm.port.energy.*;
import dev.icbm.port.radio.RadioNetwork;
import java.util.*;
import net.fabricmc.fabric.api.transfer.v1.item.InventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.base.CombinedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.InsertionOnlyStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.*;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.*;

/**
 * Persistent configuration, battery slot, energy and radio receiver for defense machines.
 *
 * <p>The battery slot is a separate {@link Inventory} rather than this block entity: hoppers only
 * use {@code ItemStorage.SIDED} for targets that are not inventories, and that lookup is how the
 * SAM station's ammunition counter accepts {@code sam_missile} items from hoppers and pipes.
 */
public final class DefenseMachineBlockEntity extends BlockEntity
    implements EnergyMachine, NamedScreenHandlerFactory, RadioNetwork.Receiver {
  private final DefaultedList<ItemStack> items = DefaultedList.ofSize(1, ItemStack.EMPTY);
  private final BatterySlot batterySlot = new BatterySlot();
  private final SamAmmoStorage samAmmo = new SamAmmoStorage();
  private final EnergyBuffer energy;
  private int range, triggerRange = 50, channel, cooldown, tracks;
  private boolean explosiveFilter = true,
      heldFilter = true,
      radioEnabled = true,
      relay,
      radarOnly,
      enabled = true;
  private int radioCooldown;
  private int visualFlags, visualYaw, lastSentVisualFlags = -1, lastSentVisualYaw;
  private long lastVisualSync = Long.MIN_VALUE, visualTargetUntil;

  /**
   * Read-only synchronized rendering state: ready=1, cooling=2, incoming=4, disabled=8,
   * tracking=16.
   */
  public int visualFlags() {
    return visualFlags;
  }

  public float visualYaw() {
    return visualYaw;
  }

  private int computeVisualFlags() {
    if (world == null) return 0;
    boolean disabled = !enabled || DefenseContent.isEmpDisabled(world, pos);
    boolean ready =
        !disabled
            && switch (kind()) {
              case RADAR -> energy.getAmount() >= 1000;
              case EMP -> cooldown == 0 && energy.getAmount() >= (long) range * range * 100;
              case SAM -> getCachedState().get(DefenseMachineBlock.POWER) > 0;
            };
    return (ready ? 1 : 0)
        | (cooldown > 0 ? 2 : 0)
        | (kind() == DefenseMachineBlock.Kind.RADAR && tracks > 0 ? 4 : 0)
        | (disabled ? 8 : 0)
        | (world.getTime() < visualTargetUntil ? 16 : 0);
  }

  private void syncVisual() {
    if (!(world instanceof ServerWorld)) return;
    visualFlags = computeVisualFlags();
    long now = world.getTime();
    if ((visualFlags != lastSentVisualFlags || visualYaw != lastSentVisualYaw)
        && (lastVisualSync == Long.MIN_VALUE || now - lastVisualSync >= 5)) {
      lastSentVisualFlags = visualFlags;
      lastSentVisualYaw = visualYaw;
      lastVisualSync = now;
      world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
    }
  }

  @Override
  public NbtCompound toInitialChunkDataNbt() {
    NbtCompound nbt = new NbtCompound();
    nbt.putBoolean("DefenseVisual", true);
    nbt.putInt(
        "VisualFlags", world != null && !world.isClient ? computeVisualFlags() : visualFlags);
    nbt.putInt("VisualYaw", visualYaw);
    return nbt;
  }

  @Override
  public net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket toUpdatePacket() {
    return net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket.create(this);
  }

  public DefenseMachineBlockEntity(BlockPos pos, BlockState state) {
    super(DefenseContent.MACHINE_BLOCK_ENTITY, pos, state);
    range =
        kind() == DefenseMachineBlock.Kind.EMP
            ? 60
            : kind() == DefenseMachineBlock.Kind.SAM ? 30 : 100;
    energy =
        new EnergyBuffer(
            kind() == DefenseMachineBlock.Kind.RADAR ? 20_000 : 32_000_000,
            10_000,
            32_000_000,
            this::markDirty);
  }

  public DefenseMachineBlock.Kind kind() {
    return ((DefenseMachineBlock) getCachedState().getBlock()).kind();
  }

  public int maximumRange() {
    return kind() == DefenseMachineBlock.Kind.EMP
        ? Math.min(512, 150 + extensions() * 20)
        : kind() == DefenseMachineBlock.Kind.SAM ? 128 : 500;
  }

  public int extensions() {
    if (world == null || kind() != DefenseMachineBlock.Kind.EMP) return 0;
    int count = 0;
    while (count < 16
        && world.isChunkLoaded(pos.up(count + 1))
        && world.getBlockState(pos.up(count + 1)).isOf(DefenseContent.EMP_TOWER_EXTENSION)) count++;
    return count;
  }

  public boolean accepts(Entity entity) {
    return entity instanceof dev.icbm.port.entity.MissileEntity
        ? explosiveFilter
        : entity instanceof dev.icbm.port.delivery.HeldItemMissileEntity && heldFilter;
  }

  public static void tick(
      net.minecraft.world.World world,
      BlockPos pos,
      BlockState state,
      DefenseMachineBlockEntity machine) {
    if (!(world instanceof ServerWorld server)) return;
    try {
      RadioNetwork.register(server, machine);
      EnergyContent.chargeFromBattery(machine.batterySlot, 0, machine.energy, 10_000);
      if (machine.cooldown > 0) {
        machine.cooldown--;
        machine.markDirty();
      }
      if (machine.radioCooldown > 0) machine.radioCooldown--;
      machine.range = MathHelper.clamp(machine.range, 1, machine.maximumRange());
      if (!machine.enabled || DefenseContent.isEmpDisabled(world, pos)) {
        machine.output(0);
        return;
      }
      if (machine.kind() == DefenseMachineBlock.Kind.RADAR) {
        if (!machine.energy.consume(1000)) {
          machine.tracks = 0;
          machine.output(0);
          return;
        }
        var detected =
            DefenseContent.trackedMissiles(world, Vec3d.ofCenter(pos), machine.range).stream()
                .filter(machine::accepts)
                .toList();
        machine.tracks = detected.size();
        var incoming = detected.stream().filter(entity -> machine.incoming(entity)).toList();
        machine.output(Math.min(15, incoming.size()));
        if (!incoming.isEmpty() && machine.radioCooldown == 0) {
          RadioNetwork.broadcast(server, machine, incoming.get(0).getUuid());
          machine.radioCooldown = 40;
        }
      } else if (machine.kind() == DefenseMachineBlock.Kind.EMP) {
        if (machine.cooldown == 0 && world.isReceivingRedstonePower(pos)) machine.fireEmp();
      } else if (!machine.radarOnly
          && world.isReceivingRedstonePower(pos)
          && machine.cooldown == 0) {
        DefenseContent.trackedMissiles(world, Vec3d.ofCenter(pos), machine.range).stream()
            .filter(machine::accepts)
            .filter(entity -> DefenseContent.isThreat(world, Vec3d.ofCenter(pos), entity))
            .min(
                Comparator.comparingDouble(entity -> entity.squaredDistanceTo(Vec3d.ofCenter(pos))))
            .ifPresent(machine::fireSam);
      }
    } finally {
      machine.syncVisual();
    }
  }

  private boolean incoming(Entity entity) {
    if (DefenseContent.isFlightDisabled(entity)) return false;
    Vec3d station = Vec3d.ofCenter(pos),
        motion = entity.getVelocity(),
        delta = station.subtract(entity.getPos());
    if (motion.lengthSquared() < 1e-8) return delta.lengthSquared() <= triggerRange * triggerRange;
    double time = delta.dotProduct(motion) / motion.lengthSquared();
    return time >= 0
        && time <= 2400
        && entity.getPos().add(motion.multiply(time)).squaredDistanceTo(station)
            <= triggerRange * triggerRange;
  }

  private void output(int power) {
    if (kind() == DefenseMachineBlock.Kind.RADAR
        && world != null
        && world.getBlockState(pos).get(DefenseMachineBlock.POWER) != power)
      world.setBlockState(
          pos, getCachedState().with(DefenseMachineBlock.POWER, power), Block.NOTIFY_ALL);
  }

  public boolean fireEmp() {
    if (!(world instanceof ServerWorld)
        || kind() != DefenseMachineBlock.Kind.EMP
        || !enabled
        || cooldown > 0) return false;
    long cost = (long) range * range * 100;
    if (!energy.consume(cost)) return false;
    DefenseContent.applyEmp(world, Vec3d.ofCenter(pos), range);
    EmpEnergyDrain.drain(
        (ServerWorld) world,
        Vec3d.ofCenter(pos),
        range,
        dev.icbm.port.payload.PayloadConfig.empEnergyDrain());
    cooldown = 200;
    markDirty();
    return true;
  }

  public boolean fireSam(Entity target) {
    if (!(world instanceof ServerWorld)
        || kind() != DefenseMachineBlock.Kind.SAM
        || cooldown > 0
        || !enabled
        || DefenseContent.isEmpDisabled(world, pos)
        || !accepts(target)
        || target.squaredDistanceTo(Vec3d.ofCenter(pos)) > (double) range * range
        || !DefenseContent.isThreat(world, Vec3d.ofCenter(pos), target)) return false;
    BlockState state = world.getBlockState(pos);
    if (state.get(DefenseMachineBlock.POWER) <= 0) return false;
    var interceptor = new SamInterceptorEntity(DefenseContent.SAM_INTERCEPTOR, world);
    interceptor.setPosition(Vec3d.ofCenter(pos.up()));
    interceptor.setTarget(target);
    interceptor.setVelocity(target.getPos().subtract(interceptor.getPos()).normalize().multiply(4));
    if (!world.spawnEntity(interceptor)) return false;
    world.setBlockState(
        pos,
        state.with(DefenseMachineBlock.POWER, state.get(DefenseMachineBlock.POWER) - 1),
        Block.NOTIFY_ALL);
    Vec3d visualDelta = target.getPos().subtract(Vec3d.ofCenter(pos));
    visualYaw = (int) Math.toDegrees(Math.atan2(visualDelta.x, visualDelta.z));
    visualTargetUntil = world.getTime() + 40;
    cooldown = 40;
    markDirty();
    return true;
  }

  public boolean configure(int button) {
    switch (button) {
      case 0 -> range -= 10;
      case 1 -> range += 10;
      case 2 -> triggerRange -= 10;
      case 3 -> triggerRange += 10;
      case 4 -> channel--;
      case 5 -> channel++;
      case 6 -> explosiveFilter = !explosiveFilter;
      case 7 -> heldFilter = !heldFilter;
      case 8 -> radioEnabled = !radioEnabled;
      case 9 -> relay = !relay;
      case 10 -> {
        return fireEmp();
      }
      case 11 -> enabled = !enabled;
      case 12 -> radarOnly = !radarOnly;
      default -> {
        return false;
      }
    }
    range = MathHelper.clamp(range, 1, maximumRange());
    triggerRange = MathHelper.clamp(triggerRange, 1, range);
    channel = MathHelper.clamp(channel, 0, 4095);
    markDirty();
    return true;
  }

  public PropertyDelegate properties() {
    return new PropertyDelegate() {
      @Override
      public int size() {
        return 15;
      }

      @Override
      public int get(int index) {
        return switch (index) {
          case 0 -> range;
          case 1 -> triggerRange;
          case 2 -> channel;
          case 3 -> explosiveFilter ? 1 : 0;
          case 4 -> heldFilter ? 1 : 0;
          case 5 -> radioEnabled ? 1 : 0;
          case 6 -> relay ? 1 : 0;
          case 7 -> cooldown;
          case 8 -> tracks;
          case 9 -> (int) (energy.getAmount() / 1000);
          case 10 -> kind().ordinal();
          case 11 -> maximumRange();
          case 12 -> enabled ? 1 : 0;
          case 13 -> extensions();
          case 14 -> radarOnly ? 1 : 0;
          default -> 0;
        };
      }

      @Override
      public void set(int index, int value) {}
    };
  }

  @Override
  public BlockPos radioPosition() {
    return pos;
  }

  @Override
  public int radioChannel() {
    return channel;
  }

  @Override
  public boolean radioEnabled() {
    return enabled
        && radioEnabled
        && channel > 0
        && world != null
        && !DefenseContent.isEmpDisabled(world, pos);
  }

  @Override
  public boolean receive(RadioNetwork.Incoming message) {
    if (!radioEnabled() || channel != message.channel() || !(world instanceof ServerWorld server))
      return false;
    Entity target = server.getEntity(message.target());
    if (target != null && DefenseContent.isTrackedMissile(target) && accepts(target)) {
      if (kind() == DefenseMachineBlock.Kind.SAM) fireSam(target);
      else if (kind() == DefenseMachineBlock.Kind.EMP
          && target.squaredDistanceTo(Vec3d.ofCenter(pos)) <= (double) range * range) fireEmp();
    }
    return relay;
  }

  @Override
  public void markRemoved() {
    if (world instanceof ServerWorld server) RadioNetwork.remove(server, pos);
    super.markRemoved();
  }

  @Override
  public EnergyBuffer energyBuffer() {
    return energy;
  }

  @Override
  public Text getDisplayName() {
    return switch (kind()) {
      case RADAR -> Text.translatable("container.icbm_port.radar_station");
      case EMP -> Text.translatable("container.icbm_port.emp_tower");
      case SAM -> Text.translatable("container.icbm_port.sam_launcher");
    };
  }

  @Override
  public ScreenHandler createMenu(int syncId, PlayerInventory inventory, PlayerEntity player) {
    return new DefenseScreenHandler(syncId, inventory, this);
  }

  /** The battery slot shown in the machine screen and dropped when the machine breaks. */
  public Inventory batterySlot() {
    return batterySlot;
  }

  /**
   * The {@code ItemStorage.SIDED} view: the battery slot on every machine, plus an insert-only
   * {@code sam_missile} magazine on SAM stations.
   */
  public Storage<ItemVariant> itemStorage(Direction side) {
    Storage<ItemVariant> battery = InventoryStorage.of(batterySlot, side);
    return kind() == DefenseMachineBlock.Kind.SAM
        ? new CombinedStorage<>(List.of(battery, samAmmo))
        : battery;
  }

  public boolean canPlayerUse(PlayerEntity player) {
    return world != null
        && !player.isSpectator()
        && player.canModifyBlocks()
        && world.getBlockEntity(pos) == this
        && player.squaredDistanceTo(Vec3d.ofCenter(pos)) <= 64
        && world.canPlayerModifyAt(player, pos);
  }

  private final class BatterySlot implements Inventory {
    @Override
    public int size() {
      return 1;
    }

    @Override
    public boolean isEmpty() {
      return items.get(0).isEmpty();
    }

    @Override
    public ItemStack getStack(int slot) {
      return items.get(slot);
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
      ItemStack result = Inventories.splitStack(items, slot, amount);
      markDirty();
      return result;
    }

    @Override
    public ItemStack removeStack(int slot) {
      ItemStack result = Inventories.removeStack(items, slot);
      markDirty();
      return result;
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
      items.set(slot, stack);
      markDirty();
    }

    @Override
    public void markDirty() {
      DefenseMachineBlockEntity.this.markDirty();
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
      return DefenseMachineBlockEntity.this.canPlayerUse(player);
    }

    @Override
    public boolean isValid(int slot, ItemStack stack) {
      return EnergyContent.isBattery(stack);
    }

    @Override
    public void clear() {
      items.clear();
      markDirty();
    }
  }

  /**
   * SAM ammunition is the {@link DefenseMachineBlock#POWER} counter. Insertions are held per
   * transaction and written to the block state only on the outermost commit, so simulated or
   * aborted transfers never change the world.
   */
  private final class SamAmmoStorage extends SnapshotParticipant<Integer>
      implements InsertionOnlyStorage<ItemVariant> {
    private int pending;

    @Override
    public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
      StoragePreconditions.notBlankNotNegative(resource, maxAmount);
      if (!resource.isOf(DefenseContent.SAM_MISSILE) || world == null || isRemoved()) return 0;
      BlockState state = world.getBlockState(pos);
      if (!(state.getBlock() instanceof DefenseMachineBlock block)
          || block.kind() != DefenseMachineBlock.Kind.SAM) return 0;
      int space = DefenseMachineBlock.MAX_SAM_AMMO - state.get(DefenseMachineBlock.POWER) - pending;
      int inserted = (int) Math.min(maxAmount, Math.max(0, space));
      if (inserted > 0) {
        updateSnapshots(transaction);
        pending += inserted;
      }
      return inserted;
    }

    @Override
    protected Integer createSnapshot() {
      return pending;
    }

    @Override
    protected void readSnapshot(Integer snapshot) {
      pending = snapshot;
    }

    @Override
    protected void onFinalCommit() {
      int added = pending;
      pending = 0;
      if (added <= 0 || world == null) return;
      BlockState state = world.getBlockState(pos);
      if (!(state.getBlock() instanceof DefenseMachineBlock)) return;
      int ammo =
          Math.min(DefenseMachineBlock.MAX_SAM_AMMO, state.get(DefenseMachineBlock.POWER) + added);
      world.setBlockState(pos, state.with(DefenseMachineBlock.POWER, ammo), Block.NOTIFY_ALL);
      markDirty();
    }
  }

  @Override
  public void writeNbt(NbtCompound nbt) {
    super.writeNbt(nbt);
    Inventories.writeNbt(nbt, items);
    energy.writeNbt(nbt);
    nbt.putInt("Range", range);
    nbt.putInt("Trigger", triggerRange);
    nbt.putInt("Channel", channel);
    nbt.putInt("Cooldown", cooldown);
    nbt.putBoolean("ExplosiveFilter", explosiveFilter);
    nbt.putBoolean("HeldFilter", heldFilter);
    nbt.putBoolean("RadioEnabled", radioEnabled);
    nbt.putBoolean("Relay", relay);
    nbt.putBoolean("Enabled", enabled);
    nbt.putBoolean("RadarOnly", radarOnly);
  }

  @Override
  public void readNbt(NbtCompound nbt) {
    if (nbt.getBoolean("DefenseVisual")) {
      visualFlags = nbt.getInt("VisualFlags") & 31;
      visualYaw = MathHelper.clamp(nbt.getInt("VisualYaw"), -180, 180);
      return;
    }
    super.readNbt(nbt);
    Inventories.readNbt(nbt, items);
    energy.readNbt(nbt);
    range = MathHelper.clamp(nbt.getInt("Range"), 1, 512);
    triggerRange = MathHelper.clamp(nbt.getInt("Trigger"), 1, 500);
    channel = MathHelper.clamp(nbt.getInt("Channel"), 0, 4095);
    cooldown = MathHelper.clamp(nbt.getInt("Cooldown"), 0, 200);
    explosiveFilter = !nbt.contains("ExplosiveFilter") || nbt.getBoolean("ExplosiveFilter");
    heldFilter = !nbt.contains("HeldFilter") || nbt.getBoolean("HeldFilter");
    radioEnabled = !nbt.contains("RadioEnabled") || nbt.getBoolean("RadioEnabled");
    relay = nbt.getBoolean("Relay");
    radarOnly = nbt.getBoolean("RadarOnly");
    enabled = !nbt.contains("Enabled") || nbt.getBoolean("Enabled");
  }
}
