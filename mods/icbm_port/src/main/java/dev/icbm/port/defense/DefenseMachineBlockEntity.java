package dev.icbm.port.defense;

import dev.icbm.port.energy.*;
import dev.icbm.port.radio.RadioMessage;
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
 * Persistent configuration, battery slot, energy and radio for defense machines.
 *
 * <p>Radar station: adapted from MIT ICBM-Classic {@code content/blocks/radarstation/
 * TileRadarStation.java} and {@code RadioRadar.java} with {@code config/machines/ConfigRadar.java}
 * defaults. EMP tower: adapted from {@code content/blocks/emptower/TileEMPTower.java}, {@code
 * RadioEmpTower.java} and {@code config/machines/ConfigEmpTower.java}. The SAM station is a port
 * machine (upstream fires SAMs from launchers) that reuses the same radio receiver contract.
 *
 * <p>The battery slot is a separate {@link Inventory} rather than this block entity: hoppers only
 * use {@code ItemStorage.SIDED} for targets that are not inventories, and that lookup is how the
 * SAM station's ammunition counter accepts {@code sam_missile} items from hoppers and pipes.
 */
public final class DefenseMachineBlockEntity extends BlockEntity
    implements EnergyMachine,
        NamedScreenHandlerFactory,
        RadioNetwork.Receiver,
        RadioNetwork.Sender {
  // ConfigRadar defaults.
  public static final int RADAR_MAX_RANGE = 500,
      RADAR_CAPACITY = 20_000,
      RADAR_COST = 1_000,
      RADAR_SCAN_TICKS = 1,
      RADAR_SAM_TICKS = 40;
  // ConfigEmpTower defaults.
  public static final int EMP_BONUS_RADIUS = 20,
      EMP_COST_AREA = 100,
      EMP_COST_TICKING = 10,
      EMP_COST_TICKING_CAP = 100,
      EMP_COOLDOWN = 200,
      EMP_INPUT = 10_000,
      EMP_MAX_BASE_RANGE = 150,
      EMP_MAX_EXTENSIONS = 16;
  /** Upper bound on scanned column height (port bound; upstream walks the column unbounded). */
  public static final int EMP_MAX_COLUMN = 64;

  /** Upstream {@code EnumRadarState}. */
  public enum RadarState {
    ON,
    OFF,
    WARNING,
    DANGER
  }

  private final DefaultedList<ItemStack> items = DefaultedList.ofSize(1, ItemStack.EMPTY);
  private final BatterySlot batterySlot = new BatterySlot();
  private final SamAmmoStorage samAmmo = new SamAmmoStorage();
  private final EnergyBuffer energy;
  private int range, triggerRange = 50, channel, cooldown;
  private boolean explosiveFilter = true,
      heldFilter = true,
      radioEnabled = true,
      outputRedstone = true,
      radarOnly,
      enabled = true;
  private int radioCooldown;
  private final List<Entity> detectedThreats = new ArrayList<>();
  private final List<Entity> incomingThreats = new ArrayList<>();
  private RadarState radarState = RadarState.OFF;
  private int visualFlags, visualYaw, lastSentVisualFlags = -1, lastSentVisualYaw;
  private long lastVisualSync = Long.MIN_VALUE, visualTargetUntil;

  /**
   * Read-only synchronized rendering state: ready=1, cooling=2, incoming=4, disabled=8,
   * tracking=16, warning=32.
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
              case RADAR -> energy.getAmount() >= RADAR_COST;
              case EMP -> cooldown == 0 && energy.getAmount() >= empFiringCost();
              case SAM -> getCachedState().get(DefenseMachineBlock.POWER) > 0;
            };
    boolean radar = kind() == DefenseMachineBlock.Kind.RADAR;
    return (ready ? 1 : 0)
        | (cooldown > 0 ? 2 : 0)
        | (radar && radarState == RadarState.DANGER ? 4 : 0)
        | (disabled ? 8 : 0)
        | (world.getTime() < visualTargetUntil ? 16 : 0)
        | (radar && radarState == RadarState.WARNING ? 32 : 0);
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
    if (kind() == DefenseMachineBlock.Kind.EMP) {
      // Upstream TileEMPTower: capacity = firing cost + ticking cost * 100 ticks, receive limit
      // 10,000 per action and no receiving while cooling down.
      energy =
          new EnergyBuffer(32_000_000, EMP_INPUT, 32_000_000, this::markDirty)
              .withDynamicCapacity(() -> empFiringCost() + empTickingCost() * EMP_COST_TICKING_CAP)
              .withInsertCondition(() -> cooldown <= 0);
    } else
      energy =
          new EnergyBuffer(
              kind() == DefenseMachineBlock.Kind.RADAR ? RADAR_CAPACITY : 32_000_000,
              10_000,
              32_000_000,
              this::markDirty);
  }

  public DefenseMachineBlock.Kind kind() {
    return ((DefenseMachineBlock) getCachedState().getBlock()).kind();
  }

  public int maximumRange() {
    return kind() == DefenseMachineBlock.Kind.EMP
        ? Math.min(512, EMP_MAX_BASE_RANGE + extensions() * EMP_BONUS_RADIUS)
        : kind() == DefenseMachineBlock.Kind.SAM ? 128 : RADAR_MAX_RANGE;
  }

  /** Upstream {@code TileEMPTower#getFiringCost}: range squared times the area cost. */
  public long empFiringCost() {
    return (long) range * range * EMP_COST_AREA;
  }

  /** Upstream {@code TileEMPTower#getTickingCost}: range times the ticking cost, per tick. */
  public long empTickingCost() {
    return (long) range * EMP_COST_TICKING;
  }

  /**
   * Upstream {@code TileEMPTower#updateStructure}: walk the contiguous column of EMP tower blocks
   * above the base and count the boosters ("fake" tower cells) in it. Upstream's base and fake cells
   * are one block with two metadata values; here they are {@code emp_tower} and {@code
   * emp_tower_extension}, so both continue the column and only extensions count.
   */
  public int extensions() {
    if (world == null || kind() != DefenseMachineBlock.Kind.EMP) return 0;
    int count = 0;
    for (int height = 1; height <= EMP_MAX_COLUMN && count < EMP_MAX_EXTENSIONS; height++) {
      BlockPos above = pos.up(height);
      if (!world.isChunkLoaded(above)) break;
      BlockState state = world.getBlockState(above);
      if (state.isOf(DefenseContent.EMP_TOWER_EXTENSION)) count++;
      else if (!state.isOf(DefenseContent.EMP_TOWER)) break;
    }
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
      if (!RadioNetwork.isRegistered(server, machine)) RadioNetwork.register(server, machine);
      if (machine.kind() == DefenseMachineBlock.Kind.EMP) {
        // Upstream drains the ticking upkeep every tick before anything else.
        machine.energy.drain(machine.empTickingCost());
      }
      EnergyContent.chargeFromBattery(machine.batterySlot, 0, machine.energy, 10_000);
      if (machine.cooldown > 0) {
        machine.cooldown--;
        machine.markDirty();
      }
      machine.range = MathHelper.clamp(machine.range, 1, machine.maximumRange());
      if (!machine.enabled || DefenseContent.isEmpDisabled(world, pos)) {
        machine.clearTracks();
        machine.output(0);
        return;
      }
      if (machine.kind() == DefenseMachineBlock.Kind.RADAR) machine.radarTick(server);
      else if (machine.kind() == DefenseMachineBlock.Kind.EMP) {
        if (machine.cooldown == 0 && world.getReceivedStrongRedstonePower(pos) > 0)
          machine.fireEmp();
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

  /** Upstream {@code TileRadarStation#update}. */
  private void radarTick(ServerWorld server) {
    if (!energy.consume(RADAR_COST)) {
      clearTracks();
      output(0);
      return;
    }
    if (server.getTime() % RADAR_SCAN_TICKS == 0) scan();
    if (radioEnabled && RadioNetwork.validChannel(channel) && !incomingThreats.isEmpty()) {
      if (radioCooldown > 0) radioCooldown--;
      if (radioCooldown <= 0) {
        radioCooldown = RADAR_SAM_TICKS;
        Entity first = incomingThreats.get(0);
        RadioNetwork.broadcast(
            server,
            this,
            new RadioMessage.IncomingMissile(channel, first.getUuid(), first.getPos(), true));
        markDirty();
      }
    }
    radarState = incomingThreats.isEmpty()
        ? detectedThreats.isEmpty() ? RadarState.ON : RadarState.WARNING
        : RadarState.DANGER;
    output(outputRedstone ? Math.min(15, incomingThreats.size()) : 0);
  }

  private void clearTracks() {
    incomingThreats.clear();
    detectedThreats.clear();
    radarState = RadarState.OFF;
  }

  /**
   * Upstream {@code TileRadarStation#doScan}: every loaded explosive missile (and, as a port
   * extension, held-item cargo missile) inside a square column of half-width {@code min(range,
   * 500)} that has been airborne for more than one tick is either incoming (moving closer) or merely
   * detected; both lists are sorted nearest first by block-distance sum.
   */
  void scan() {
    incomingThreats.clear();
    detectedThreats.clear();
    if (world == null) return;
    double half = Math.min(range, RADAR_MAX_RANGE);
    double cx = pos.getX() + 0.5, cz = pos.getZ() + 0.5;
    Box column =
        new Box(cx - half, world.getBottomY(), cz - half, cx + half, world.getTopY(), cz + half);
    for (Entity entity :
        world.getEntitiesByClass(
            Entity.class,
            column,
            e -> DefenseContent.isTrackedMissile(e) && !e.isRemoved() && accepts(e))) {
      if (entity.age <= 1) continue;
      if (isMissileGoingToHit(entity)) incomingThreats.add(entity);
      else detectedThreats.add(entity);
    }
    Comparator<Entity> nearest = Comparator.comparingInt(this::blockDistance);
    incomingThreats.sort(nearest);
    detectedThreats.sort(nearest);
  }

  private int blockDistance(Entity entity) {
    return (int) Math.floor(Math.abs(pos.getX() + 0.5 - entity.getX()))
        + (int) Math.floor(Math.abs(pos.getY() + 0.5 - entity.getY()))
        + (int) Math.floor(Math.abs(pos.getZ() + 0.5 - entity.getZ()));
  }

  /**
   * Upstream {@code TileRadarStation#isMissileGoingToHit}: the missile is incoming when its next
   * position is closer to the radar than its current one. EMP-disabled flights are never incoming
   * (port rule: they have no guidance left).
   */
  public boolean isMissileGoingToHit(Entity entity) {
    if (entity == null || entity.isRemoved() || DefenseContent.isFlightDisabled(entity))
      return false;
    Vec3d radar = new Vec3d(pos.getX(), pos.getY(), pos.getZ());
    Vec3d now = entity.getPos();
    return now.add(entity.getVelocity()).distanceTo(radar) < now.distanceTo(radar);
  }

  public RadarState radarState() {
    return radarState;
  }

  public List<Entity> incomingThreats() {
    return List.copyOf(incomingThreats);
  }

  public List<Entity> detectedThreats() {
    return List.copyOf(detectedThreats);
  }

  public boolean outputsRedstone() {
    return outputRedstone;
  }

  /** Upstream right-click-with-redstone toggle on the radar block. */
  public boolean toggleRedstoneOutput() {
    if (kind() != DefenseMachineBlock.Kind.RADAR) return false;
    outputRedstone = !outputRedstone;
    if (!outputRedstone) output(0);
    markDirty();
    return outputRedstone;
  }

  private void output(int power) {
    if (kind() == DefenseMachineBlock.Kind.RADAR
        && world != null
        && world.getBlockState(pos).get(DefenseMachineBlock.POWER) != power)
      world.setBlockState(
          pos, getCachedState().with(DefenseMachineBlock.POWER, power), Block.NOTIFY_ALL);
  }

  /** Upstream {@code TileEMPTower#fire}: requires no cooldown and the full area cost. */
  public boolean fireEmp() {
    if (!(world instanceof ServerWorld)
        || kind() != DefenseMachineBlock.Kind.EMP
        || !enabled
        || cooldown > 0) return false;
    if (!energy.consume(empFiringCost())) return false;
    DefenseContent.applyEmp(world, Vec3d.ofCenter(pos), range);
    EmpEnergyDrain.drain(
        (ServerWorld) world,
        Vec3d.ofCenter(pos),
        range,
        dev.icbm.port.payload.PayloadConfig.empEnergyDrain());
    cooldown = EMP_COOLDOWN;
    markDirty();
    return true;
  }

  public int cooldown() {
    return cooldown;
  }

  public int range() {
    return range;
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

  /** Highest server button id accepted by {@link #configure(int)}. */
  public static final int MAX_BUTTON = 14;

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
      case 9 -> {
        if (kind() != DefenseMachineBlock.Kind.RADAR) return false;
        toggleRedstoneOutput();
      }
      case 10 -> {
        return fireEmp();
      }
      case 11 -> enabled = !enabled;
      case 12 -> radarOnly = !radarOnly;
      case 13 -> channel -= 100;
      case 14 -> channel += 100;
      default -> {
        return false;
      }
    }
    range = MathHelper.clamp(range, 1, maximumRange());
    triggerRange = MathHelper.clamp(triggerRange, 1, range);
    channel = MathHelper.clamp(channel, 0, RadioNetwork.MAX_CHANNEL);
    markDirty();
    return true;
  }

  public PropertyDelegate properties() {
    return new PropertyDelegate() {
      @Override
      public int size() {
        return PROPERTY_COUNT;
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
          case 6 -> outputRedstone ? 1 : 0;
          case 7 -> cooldown;
          case 8 -> detectedThreats.size() + incomingThreats.size();
          case 9 -> (int) (energy.getAmount() / 1000);
          case 10 -> kind().ordinal();
          case 11 -> maximumRange();
          case 12 -> enabled ? 1 : 0;
          case 13 -> extensions();
          case 14 -> radarOnly ? 1 : 0;
          case 15 -> radarState.ordinal();
          case 16 -> incomingThreats.size();
          case 17 -> (int) (energy.getCapacity() / 1000);
          case 18 -> kind() == DefenseMachineBlock.Kind.EMP ? (int) empTickingCost() : 0;
          case 19 -> kind() == DefenseMachineBlock.Kind.EMP ? (int) (empFiringCost() / 1000) : 0;
          default -> 0;
        };
      }

      @Override
      public void set(int index, int value) {}
    };
  }

  public static final int PROPERTY_COUNT = 20;

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
        && RadioNetwork.validChannel(channel)
        && world != null
        && !DefenseContent.isEmpDisabled(world, pos);
  }

  /**
   * Upstream {@code RadioEmpTower#onMessage}: an incoming-missile message with its trigger set
   * fires when the missile is inside the configured range; any other trigger message fires
   * outright. Radars only send ({@code RadioRadar}). SAM stations (port machine) launch at the
   * referenced missile.
   */
  @Override
  public void onMessage(RadioNetwork.Sender sender, RadioMessage message) {
    if (!(world instanceof ServerWorld server) || !radioEnabled()) return;
    switch (kind()) {
      case RADAR -> {}
      case EMP -> {
        if (message instanceof RadioMessage.IncomingMissile incoming) {
          if (!incoming.shouldTrigger()) return;
          Entity missile = server.getEntity(incoming.missile());
          if (missile != null && DefenseContent.isTrackedMissile(missile) && !accepts(missile))
            return;
          Vec3d at = missile != null ? missile.getPos() : incoming.position();
          if (at.distanceTo(Vec3d.ofCenter(pos)) < range && fireEmp())
            RadioNetwork.reply(sender, this, "message.icbm_port.radio.emp_fired");
        } else if (message.shouldTrigger() && fireEmp())
          RadioNetwork.reply(sender, this, "message.icbm_port.radio.emp_fired");
      }
      case SAM -> {
        if (message instanceof RadioMessage.IncomingMissile incoming) {
          Entity target = server.getEntity(incoming.missile());
          if (target != null && DefenseContent.isTrackedMissile(target) && accepts(target))
            fireSam(target);
        }
      }
    }
  }

  @Override
  public void markRemoved() {
    if (world instanceof ServerWorld server) RadioNetwork.remove(server, this);
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
    nbt.putInt("Range", range);
    energy.writeNbt(nbt);
    nbt.putInt("Trigger", triggerRange);
    nbt.putInt("Channel", channel);
    nbt.putInt("Cooldown", cooldown);
    nbt.putBoolean("ExplosiveFilter", explosiveFilter);
    nbt.putBoolean("HeldFilter", heldFilter);
    nbt.putBoolean("RadioEnabled", radioEnabled);
    nbt.putBoolean("OutputRedstone", outputRedstone);
    nbt.putBoolean("Enabled", enabled);
    nbt.putBoolean("RadarOnly", radarOnly);
    nbt.putInt("SamDelay", radioCooldown);
  }

  @Override
  public void readNbt(NbtCompound nbt) {
    if (nbt.getBoolean("DefenseVisual")) {
      visualFlags = nbt.getInt("VisualFlags") & 63;
      visualYaw = MathHelper.clamp(nbt.getInt("VisualYaw"), -180, 180);
      return;
    }
    super.readNbt(nbt);
    Inventories.readNbt(nbt, items);
    // Range first: the EMP tower's energy capacity depends on it.
    range = MathHelper.clamp(nbt.getInt("Range"), 1, 512);
    cooldown = MathHelper.clamp(nbt.getInt("Cooldown"), 0, EMP_COOLDOWN);
    energy.readNbt(nbt);
    triggerRange = MathHelper.clamp(nbt.getInt("Trigger"), 1, RADAR_MAX_RANGE);
    channel = MathHelper.clamp(nbt.getInt("Channel"), 0, RadioNetwork.MAX_CHANNEL);
    explosiveFilter = !nbt.contains("ExplosiveFilter") || nbt.getBoolean("ExplosiveFilter");
    heldFilter = !nbt.contains("HeldFilter") || nbt.getBoolean("HeldFilter");
    radioEnabled = !nbt.contains("RadioEnabled") || nbt.getBoolean("RadioEnabled");
    outputRedstone = !nbt.contains("OutputRedstone") || nbt.getBoolean("OutputRedstone");
    radarOnly = nbt.getBoolean("RadarOnly");
    enabled = !nbt.contains("Enabled") || nbt.getBoolean("Enabled");
    radioCooldown = MathHelper.clamp(nbt.getInt("SamDelay"), 0, RADAR_SAM_TICKS);
  }
}
