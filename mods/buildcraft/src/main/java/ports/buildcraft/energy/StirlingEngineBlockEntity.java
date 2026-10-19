package ports.buildcraft.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import ports.buildcraft.lib.InventoryUtil;
import ports.buildcraft.lib.Power;
import ports.buildcraft.platform.Platform;

/** Upstream {@code TileEngineStone_BC8}: burns furnace fuel with a PI-controlled output of 1/3 to 1 MJ/t. */
public class StirlingEngineBlockEntity extends EngineBlockEntity implements WorldlyContainer, MenuProvider {
    private static final long MAX_OUTPUT = Power.MJ;
    private static final long MIN_OUTPUT = MAX_OUTPUT / 3;
    private static final long E_LIMIT = (MAX_OUTPUT - MIN_OUTPUT) * 20;
    private static final int[] SLOTS = {0};

    private final NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);
    int burnTime;
    int totalBurnTime;
    long esum;
    private boolean isForceInserting;

    final ContainerData data = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> burnTime;
                case 1 -> totalBurnTime;
                case 2 -> (int) Math.round(heat * 100);
                default -> (int) (power / Power.MJ);
            };
        }
        @Override public void set(int index, int value) {}
        @Override public int getCount() { return 4; }
    };

    public StirlingEngineBlockEntity(BlockPos pos, BlockState state) {
        super(BCEnergy.STIRLING_ENGINE_TYPE.get(), pos, state);
    }

    @Override protected boolean isRedstoneOnly() { return false; }
    @Override public boolean isBurning() { return burnTime > 0; }
    @Override public long maxPowerExtracted() { return 100 * Power.MJ; }
    @Override public long getMaxPower() { return 1000 * Power.MJ; }
    @Override public boolean hasMenu() { return true; }

    @Override
    public InteractionResult onActivated(ServerPlayer player, InteractionHand hand, Direction side) {
        Platform.openMenu(player, this, worldPosition);
        return InteractionResult.CONSUME;
    }

    @Override
    protected void engineUpdate() {
        super.engineUpdate();
        if (burnTime > 0) {
            burnTime--;
            if (getPowerStage() != PowerStage.OVERHEAT) {
                long output = getCurrentOutput();
                currentOutput = output;
                addPower(output);
            }
        }
    }

    @Override
    protected void burn() {
        if (burnTime == 0 && isRedstonePowered) {
            burnTime = totalBurnTime = Platform.get().burnTime(items.get(0));
            if (burnTime > 0) {
                ItemStack fuel = items.get(0).split(1);
                ItemStack container = Platform.get().craftingRemainder(fuel);
                if (!container.isEmpty()) {
                    if (items.get(0).isEmpty()) {
                        isForceInserting = true;
                        items.set(0, container);
                    } else {
                        InventoryUtil.addToBestAcceptor(level, worldPosition, null, container);
                    }
                }
                setChanged();
            }
        }
    }

    @Override
    public long getCurrentOutput() {
        long e = 3 * getMaxPower() / 8 - power;
        esum = clamp(esum + e, -E_LIMIT, E_LIMIT);
        return clamp(e + esum / 20, MIN_OUTPUT, MAX_OUTPUT);
    }

    private static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public NonNullList<ItemStack> drops() {
        NonNullList<ItemStack> drops = NonNullList.create();
        drops.addAll(items);
        items.set(0, ItemStack.EMPTY);
        return drops;
    }

    // Container

    @Override public int getContainerSize() { return 1; }
    @Override public boolean isEmpty() { return items.get(0).isEmpty(); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack removed = ContainerHelper.removeItem(items, slot, amount);
        if (items.get(0).isEmpty()) isForceInserting = false;
        setChanged();
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        isForceInserting = false;
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.isEmpty()) isForceInserting = false;
        setChanged();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return Platform.get().burnTime(stack) > 0;
    }

    @Override
    public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 64;
    }

    @Override public void clearContent() { items.set(0, ItemStack.EMPTY); }
    @Override public int[] getSlotsForFace(Direction side) { return SLOTS; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) { return canPlaceItem(slot, stack); }

    /** Containers left behind by burnt fuel (lava buckets) may be pulled out; fuel may not. */
    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return isForceInserting || Platform.get().burnTime(stack) <= 0;
    }

    // Menu

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.buildcraft.stirling_engine");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new StirlingEngineMenu(id, inventory, this, data);
    }

    // Persistence

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("burnTime", burnTime);
        tag.putInt("totalBurnTime", totalBurnTime);
        tag.putLong("esum", esum);
        tag.putBoolean("forceInserting", isForceInserting);
        ContainerHelper.saveAllItems(tag, items);
    }

    @Override
    protected void loadServer(CompoundTag tag) {
        super.loadServer(tag);
        burnTime = tag.getInt("burnTime");
        totalBurnTime = tag.getInt("totalBurnTime");
        esum = tag.getLong("esum");
        isForceInserting = tag.getBoolean("forceInserting");
        items.set(0, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items);
    }
}
