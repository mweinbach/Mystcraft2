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

/** Standalone Fabric adaptation of upstream launcher inventory, target and energy buffer. */
public final class LauncherBlockEntity extends BlockEntity
    implements Inventory, NamedScreenHandlerFactory, dev.icbm.port.energy.EnergyMachine {
  private final DefaultedList<ItemStack> inventory = DefaultedList.ofSize(2, ItemStack.EMPTY);
  private BlockPos target = BlockPos.ORIGIN;
  private final dev.icbm.port.energy.EnergyBuffer energy;
  private int frequency;
  private boolean targeted;
  private net.minecraft.util.registry.RegistryKey<net.minecraft.world.World> payloadDimension;
  private BlockPos payloadDestination;

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
          return 11;
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

  /** Discover connectivity each operation: removing a connector immediately splits pools. */
  public void shareEnergy() {
    if (world == null) return;
    long required = launcherBlock().launchCost() - energy.getAmount();
    if (required <= 0) return;
    for (BlockPos candidate : LauncherNetwork.connected(world, pos)) {
      if (candidate.equals(pos)
          || !(world.getBlockEntity(candidate) instanceof LauncherBlockEntity donor)) continue;
      try (var transaction =
          net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()) {
        long taken = donor.energyBuffer().extract(required, transaction);
        if (energy.insert(taken, transaction) == taken) transaction.commit();
      }
      required = launcherBlock().launchCost() - energy.getAmount();
      if (required <= 0) return;
    }
  }

  public boolean pullNetworkAmmo() {
    if (world == null || !getStack(0).isEmpty()) return false;
    for (BlockPos candidate : LauncherNetwork.connected(world, pos)) {
      if (!candidate.equals(pos)
          && world.getBlockEntity(candidate) instanceof LauncherBlockEntity donor
          && IcbmPort.isMissile(donor.getStack(0))) {
        setStack(0, donor.removeStack(0, 1));
        return true;
      }
    }
    return false;
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
