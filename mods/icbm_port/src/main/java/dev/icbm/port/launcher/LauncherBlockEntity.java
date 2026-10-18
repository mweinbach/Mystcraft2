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
    implements Inventory, NamedScreenHandlerFactory {
  private final DefaultedList<ItemStack> inventory = DefaultedList.ofSize(2, ItemStack.EMPTY);
  private BlockPos target = BlockPos.ORIGIN;
  private int energy = 1000, frequency;
  private boolean targeted;
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
            case 3 -> energy;
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
            case 3 -> energy = value;
            case 4 -> targeted = value != 0;
            case 5 -> frequency = value;
          }
        }
      };

  public LauncherBlockEntity(BlockPos pos, BlockState state) {
    super(LauncherContent.BLOCK_ENTITY, pos, state);
    target = pos.toImmutable();
  }

  public BlockPos target() {
    return target;
  }

  public boolean targeted() {
    return targeted;
  }

  public int energy() {
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
    if (energy > capacity() - 1000) return false;
    energy += 1000;
    markDirty();
    return true;
  }

  public boolean charge() {
    if (energy > capacity() - 1000 || !inventory.get(1).isOf(Items.REDSTONE)) return false;
    inventory.get(1).decrement(1);
    energy += 1000;
    markDirty();
    return true;
  }

  public void consumeLaunch() {
    energy -= launcherBlock().launchCost();
    removeStack(0, 1);
    markDirty();
  }

  @Override
  public void writeNbt(NbtCompound nbt) {
    super.writeNbt(nbt);
    Inventories.writeNbt(nbt, inventory);
    nbt.putLong("Target", target.asLong());
    nbt.putInt("Energy", energy);
    nbt.putInt("Frequency", frequency);
    nbt.putBoolean("Targeted", targeted);
  }

  @Override
  public void readNbt(NbtCompound nbt) {
    super.readNbt(nbt);
    inventory.clear();
    Inventories.readNbt(nbt, inventory);
    target = nbt.contains("Target") ? BlockPos.fromLong(nbt.getLong("Target")) : pos.toImmutable();
    energy =
        nbt.contains("Energy") ? Math.max(0, Math.min(capacity(), nbt.getInt("Energy"))) : 1000;
    frequency = Math.max(0, Math.min(9999, nbt.getInt("Frequency")));
    targeted = nbt.getBoolean("Targeted");
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
    return slot == 0 ? IcbmPort.isMissile(stack) : stack.isOf(Items.REDSTONE);
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
    if (world != null
        && !world.isClient
        && world.getBlockState(pos).getBlock() instanceof dev.icbm.port.block.LauncherBlock) {
      BlockState state = world.getBlockState(pos);
      boolean loaded = !getStack(0).isEmpty();
      if (state.get(LauncherBlock.LOADED) != loaded)
        world.setBlockState(pos, state.with(LauncherBlock.LOADED, loaded), 3);
    }
  }

  public Text getDisplayName() {
    return Text.literal("ICBM Launcher");
  }

  public ScreenHandler createMenu(int syncId, PlayerInventory inventory, PlayerEntity player) {
    return new LauncherScreenHandler(syncId, inventory, this, properties);
  }
}
