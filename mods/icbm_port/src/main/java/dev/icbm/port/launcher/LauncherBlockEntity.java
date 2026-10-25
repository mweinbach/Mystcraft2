package dev.icbm.port.launcher;

import dev.icbm.port.IcbmPort;
import dev.icbm.port.block.LauncherBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;

/**
 * Standalone Fabric adaptation of upstream launcher inventory, target and energy buffer.
 *
 * <p>Firing delay, lock height and the persisted firing package are adapted from MIT ICBM-Classic
 * {@code content/blocks/launcher/base/TileLauncherBase.java} and {@code
 * content/blocks/launcher/FiringPackage.java}. The port has no separate launcher-screen block
 * entity, so each launcher is also its own radio receiver on its channel, standing in for upstream
 * {@code RadioScreen} (bases) and {@code RadioCruise} (cruise launchers).
 */
public final class LauncherBlockEntity extends BlockEntity
    implements Inventory,
        NamedScreenHandlerFactory,
        dev.icbm.port.energy.EnergyMachine,
        dev.icbm.port.radio.RadioNetwork.Receiver {
  private final DefaultedList<ItemStack> inventory = DefaultedList.ofSize(2, ItemStack.EMPTY);
  private BlockPos target = BlockPos.ORIGIN;
  private final dev.icbm.port.energy.EnergyBuffer energy;
  private int frequency;
  private boolean targeted;
  private net.minecraft.util.registry.RegistryKey<net.minecraft.world.World> payloadDimension;
  private BlockPos payloadDestination;
  private int firingDelay, lockHeight = LauncherRules.DEFAULT_LOCK_HEIGHT;
  // Upstream FiringPackage: countdown, target (null = direct fire along the facing) and the
  // launcher count used for inaccuracy when it fires.
  private boolean pending;
  private BlockPos pendingTarget;
  private int pendingCountdown, pendingLaunchers = 1;
  private LauncherStatus lastStatus = LauncherStatus.READY;

  public net.minecraft.util.registry.RegistryKey<net.minecraft.world.World> payloadDimension() {
    return payloadDimension;
  }

  public BlockPos payloadDestination() {
    return payloadDestination;
  }

  public boolean configurePayloadDestination(
      net.minecraft.util.registry.RegistryKey<net.minecraft.world.World> dimension,
      BlockPos destination) {
    if (!(world instanceof net.minecraft.server.world.ServerWorld source)
        || IcbmPort.payloadForMissile(getStack(0)) != dev.icbm.port.payload.PayloadType.ENDER)
      return false;
    var targetWorld = source.getServer().getWorld(dimension);
    if (targetWorld == null
        || targetWorld.isOutOfHeightLimit(destination)
        || !targetWorld.getWorldBorder().contains(destination)) return false;
    payloadDimension = dimension;
    payloadDestination = destination.toImmutable();
    markDirty();
    return true;
  }

  public final PropertyDelegate properties =
      new PropertyDelegate() {
        public int size() {
          return PROPERTY_COUNT;
        }

        public int get(int i) {
          return switch (i) {
            case 0 -> target.getX();
            case 1 -> target.getY();
            case 2 -> target.getZ();
            case 3 -> energy();
            case 4 -> targeted ? 1 : 0;
            case 5 -> frequency;
            case 6 -> target.getX() >> 16;
            case 7 -> target.getY() >> 16;
            case 8 -> target.getZ() >> 16;
            case 10 -> capacity();
            case 11 -> firingDelay;
            case 12 -> lockHeight;
            case 13 -> lastStatus.ordinal();
            case 14 -> pending ? pendingCountdown : 0;
            default -> 1;
          };
        }

        public void set(int i, int value) {
          switch (i) {
            case 0 -> target = new BlockPos(value, target.getY(), target.getZ());
            case 1 -> target = new BlockPos(target.getX(), value, target.getZ());
            case 2 -> target = new BlockPos(target.getX(), target.getY(), value);
            case 3 -> energy.setAmount(value);
            case 4 -> targeted = value != 0;
            case 5 -> frequency = value;
          }
        }
      };

  public static final int PROPERTY_COUNT = 15;

  public LauncherBlockEntity(BlockPos pos, BlockState state) {
    super(LauncherContent.BLOCK_ENTITY, pos, state);
    target = pos.toImmutable();
    energy = new dev.icbm.port.energy.EnergyBuffer(capacity(), 1200, 1200, this::markDirty);
    energy.setAmount(launcherBlock().tier == 0 ? 1000 : 0);
  }

  public BlockPos target() {
    return target;
  }

  public boolean targeted() {
    return targeted;
  }

  public int energy() {
    return (int) energy.getAmount();
  }

  @Override
  public dev.icbm.port.energy.EnergyBuffer energyBuffer() {
    return energy;
  }

  public dev.icbm.port.block.LauncherBlock launcherBlock() {
    return (dev.icbm.port.block.LauncherBlock) getCachedState().getBlock();
  }

  public int capacity() {
    return launcherBlock().capacity();
  }

  public int frequency() {
    return frequency;
  }

  public boolean configure(BlockPos target, boolean targeted, int frequency) {
    if (world == null
        || !LauncherContent.validTarget(world, pos, target)
        || frequency < 0
        || frequency > 9999) return false;
    this.target = target.toImmutable();
    this.targeted = targeted;
    this.frequency = frequency;
    markDirty();
    return true;
  }

  public int firingDelay() {
    return firingDelay;
  }

  public int lockHeight() {
    return lockHeight;
  }

  /** Atomically set the firing delay (0..1200 ticks) and lock height (3..64 blocks). */
  public boolean configureLaunch(int firingDelay, int lockHeight) {
    if (firingDelay != LauncherRules.clampFiringDelay(firingDelay)
        || lockHeight != LauncherRules.clampLockHeight(lockHeight)) return false;
    this.firingDelay = firingDelay;
    this.lockHeight = lockHeight;
    markDirty();
    return true;
  }

  public LauncherStatus lastStatus() {
    return lastStatus;
  }

  public void recordStatus(LauncherStatus status) {
    lastStatus = status;
  }

  public boolean hasPendingLaunch() {
    return pending;
  }

  public int pendingCountdown() {
    return pending ? pendingCountdown : 0;
  }

  /** Upstream {@code TileLauncherBase#setFiringPackage}. */
  public void queueLaunch(BlockPos target, int countdown, int launchers) {
    pending = true;
    pendingTarget = target == null ? null : target.toImmutable();
    pendingCountdown = Math.max(1, countdown);
    pendingLaunchers = Math.max(1, launchers);
    markDirty();
  }

  /**
   * Server ticker: count the firing package down and fire it (upstream {@code
   * TileLauncherBase#handleFirePackage}), and keep the radio registration current.
   */
  public static void tick(
      net.minecraft.world.World world,
      BlockPos pos,
      BlockState state,
      LauncherBlockEntity launcher) {
    if (!(world instanceof net.minecraft.server.world.ServerWorld server)) return;
    if (!dev.icbm.port.radio.RadioNetwork.isRegistered(server, launcher))
      dev.icbm.port.radio.RadioNetwork.register(server, launcher);
    if (!launcher.pending) return;
    if (--launcher.pendingCountdown > 0) {
      launcher.markDirty();
      return;
    }
    BlockPos target = launcher.pendingTarget;
    int launchers = launcher.pendingLaunchers;
    launcher.pending = false;
    launcher.pendingTarget = null;
    launcher.markDirty();
    launcher.launcherBlock().fireNow(server, pos, target, launchers);
  }

  @Override
  public BlockPos radioPosition() {
    return pos;
  }

  @Override
  public int radioChannel() {
    return frequency;
  }

  @Override
  public boolean radioEnabled() {
    return world != null
        && dev.icbm.port.radio.RadioNetwork.validChannel(frequency)
        && !dev.icbm.port.defense.DefenseContent.isEmpDisabled(world, pos);
  }

  /**
   * Upstream {@code RadioScreen#onMessage} (bases) and {@code RadioCruise#onMessage} (cruise): a
   * message carrying a target retargets the launcher (with a "target set" reply unless it also
   * triggers); a trigger fires the base, or queues one firing on a cruise launcher.
   */
  @Override
  public void onMessage(
      dev.icbm.port.radio.RadioNetwork.Sender sender, dev.icbm.port.radio.RadioMessage message) {
    if (!(world instanceof net.minecraft.server.world.ServerWorld server) || !radioEnabled())
      return;
    net.minecraft.util.math.Vec3d aim = message.target();
    if (aim != null) {
      BlockPos requested = new BlockPos(aim);
      if (!LauncherContent.validTarget(world, pos, requested)) {
        dev.icbm.port.radio.RadioNetwork.reply(
            sender, this, "message.icbm_port.radio.target_invalid");
        return;
      }
      target = requested.toImmutable();
      targeted = true;
      markDirty();
      if (!message.shouldTrigger())
        dev.icbm.port.radio.RadioNetwork.reply(
            sender,
            this,
            "message.icbm_port.radio.target_set",
            target.getX(),
            target.getY(),
            target.getZ());
    }
    if (!message.shouldTrigger()) return;
    LauncherBlock block = launcherBlock();
    if (block.cruise) {
      if (pending) {
        dev.icbm.port.radio.RadioNetwork.reply(
            sender, this, LauncherStatus.ERROR_QUEUED.translationKey());
        return;
      }
      queueLaunch(targeted ? target : null, 1, 1);
      dev.icbm.port.radio.RadioNetwork.reply(
          sender, this, "message.icbm_port.radio.launch_triggered");
      return;
    }
    LauncherStatus status =
        block.fire(server, pos, LauncherNetworkStorage.launchers(world, pos).size());
    if (status.accepted())
      dev.icbm.port.radio.RadioNetwork.reply(
          sender,
          this,
          "message.icbm_port.radio.launch_success",
          target.getX(),
          target.getY(),
          target.getZ(),
          (int) Math.sqrt(target.getSquaredDistance(pos)));
    else
      dev.icbm.port.radio.RadioNetwork.reply(
          sender,
          this,
          "message.icbm_port.radio.launch_failed",
          net.minecraft.text.Text.translatable(status.translationKey()));
  }

  @Override
  public void markRemoved() {
    if (world instanceof net.minecraft.server.world.ServerWorld server)
      dev.icbm.port.radio.RadioNetwork.remove(server, this);
    super.markRemoved();
  }

  public boolean acceptCharge() {
    return energy.insert(1000, false) > 0;
  }

  public boolean charge() {
    if (dev.icbm.port.energy.EnergyContent.chargeFromBattery(this, 1, energy, 1200) > 0)
      return true;
    if (!inventory.get(1).isOf(Items.REDSTONE) || !acceptCharge()) return false;
    inventory.get(1).decrement(1);
    markDirty();
    return true;
  }

  public void consumeLaunch() {
    if (!energy.consume(launcherBlock().launchCost()))
      throw new IllegalStateException("Launcher energy changed during launch transaction");
    removeStack(0, 1);
    markDirty();
  }

  @Override
  public void writeNbt(NbtCompound nbt) {
    super.writeNbt(nbt);
    Inventories.writeNbt(nbt, inventory);
    nbt.putLong("Target", target.asLong());
    energy.writeNbt(nbt);
    nbt.putInt("Frequency", frequency);
    nbt.putBoolean("Targeted", targeted);
    nbt.putInt("FiringDelay", firingDelay);
    nbt.putInt("LockHeight", lockHeight);
    if (pending) {
      NbtCompound firing = new NbtCompound();
      firing.putInt("Countdown", pendingCountdown);
      firing.putInt("Launchers", pendingLaunchers);
      if (pendingTarget != null) firing.putLong("Target", pendingTarget.asLong());
      nbt.put("FiringPackage", firing);
    }
    if (payloadDimension != null && payloadDestination != null) {
      nbt.putString("PayloadDimension", payloadDimension.getValue().toString());
      nbt.putLong("PayloadDestination", payloadDestination.asLong());
    }
  }

  @Override
  public void readNbt(NbtCompound nbt) {
    super.readNbt(nbt);
    inventory.clear();
    Inventories.readNbt(nbt, inventory);
    target = nbt.contains("Target") ? BlockPos.fromLong(nbt.getLong("Target")) : pos.toImmutable();
    if (nbt.contains("Energy")) energy.readNbt(nbt);
    else energy.setAmount(launcherBlock().tier == 0 ? 1000 : 0);
    frequency = Math.max(0, Math.min(9999, nbt.getInt("Frequency")));
    targeted = nbt.getBoolean("Targeted");
    firingDelay = LauncherRules.clampFiringDelay(nbt.getInt("FiringDelay"));
    lockHeight =
        nbt.contains("LockHeight")
            ? LauncherRules.clampLockHeight(nbt.getInt("LockHeight"))
            : LauncherRules.DEFAULT_LOCK_HEIGHT;
    pending = nbt.contains("FiringPackage");
    NbtCompound firing = nbt.getCompound("FiringPackage");
    pendingCountdown = pending ? Math.max(1, Math.min(LauncherRules.MAX_FIRING_DELAY, firing.getInt("Countdown"))) : 0;
    pendingLaunchers = Math.max(1, Math.min(128, firing.getInt("Launchers")));
    pendingTarget = pending && firing.contains("Target") ? BlockPos.fromLong(firing.getLong("Target")) : null;
    payloadDimension = null;
    payloadDestination = null;
    var dimension = net.minecraft.util.Identifier.tryParse(nbt.getString("PayloadDimension"));
    if (dimension != null && nbt.contains("PayloadDestination")) {
      payloadDimension =
          net.minecraft.util.registry.RegistryKey.of(
              net.minecraft.util.registry.Registry.WORLD_KEY, dimension);
      payloadDestination = BlockPos.fromLong(nbt.getLong("PayloadDestination"));
    }
  }

  public int size() {
    return 2;
  }

  public boolean isEmpty() {
    return inventory.stream().allMatch(ItemStack::isEmpty);
  }

  public ItemStack getStack(int slot) {
    return inventory.get(slot);
  }

  public ItemStack removeStack(int slot, int count) {
    ItemStack result = Inventories.splitStack(inventory, slot, count);
    markDirty();
    return result;
  }

  public ItemStack removeStack(int slot) {
    ItemStack result = Inventories.removeStack(inventory, slot);
    markDirty();
    return result;
  }

  public void setStack(int slot, ItemStack stack) {
    inventory.set(slot, stack);
    if (stack.getCount() > getMaxCountPerStack()) stack.setCount(getMaxCountPerStack());
    markDirty();
  }

  public boolean isValid(int slot, ItemStack stack) {
    return slot == 0
        ? IcbmPort.isMissile(stack)
        : (stack.isOf(Items.REDSTONE) || dev.icbm.port.energy.EnergyContent.isBattery(stack));
  }

  public boolean canPlayerUse(PlayerEntity player) {
    return world != null
        && world.getBlockEntity(pos) == this
        && LauncherNetwork.canUse(world, pos, player);
  }

  public void clear() {
    inventory.clear();
    markDirty();
  }

  @Override
  public void markDirty() {
    super.markDirty();
    if (energy != null
        && world != null
        && !world.isClient
        && world.getBlockState(pos).getBlock() instanceof dev.icbm.port.block.LauncherBlock) {
      BlockState state = world.getBlockState(pos);
      boolean loaded = !getStack(0).isEmpty();
      if (state.get(LauncherBlock.LOADED) != loaded)
        world.setBlockState(pos, state.with(LauncherBlock.LOADED, loaded), 3);
    }
  }

  public Text getDisplayName() {
    return Text.translatable("container.icbm_port.launcher");
  }

  public ScreenHandler createMenu(int syncId, PlayerInventory inventory, PlayerEntity player) {
    return new LauncherScreenHandler(syncId, inventory, this, properties);
  }
}
