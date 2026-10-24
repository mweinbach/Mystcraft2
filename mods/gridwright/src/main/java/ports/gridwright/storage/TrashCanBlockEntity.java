package ports.gridwright.storage;

import ports.gridwright.block.TrashCanBlock;
import ports.gridwright.block.TrashKind;
import ports.gridwright.registry.ModUtilities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * A trash can. Item cans are a container that is always empty: whatever a hopper, pipe or player puts
 * in is destroyed. Fluid and energy cans accept any amount through each loader's fluid and energy
 * APIs and destroy it. Nothing is ever stored, so nothing can be pulled back out.
 */
public class TrashCanBlockEntity extends BlockEntity implements WorldlyContainer {

    /** Nine slots so the player's screen is one chest row; every one destroys what it is given. */
    public static final int SLOTS = 9;
    private static final int[] FACE_SLOTS = {0};
    private static final int[] NO_SLOTS = {};

    /** What this can has destroyed since it loaded; not saved, for its owner's curiosity and tests. */
    private long destroyed;

    public TrashCanBlockEntity(BlockPos pos, BlockState state) {
        super(ModUtilities.TRASH_CAN.get(), pos, state);
    }

    /**
     * Read from the block state rather than a field: Forge gathers capabilities inside BlockEntity's
     * constructor, before subclass fields are assigned, and asks for the kind there.
     */
    public TrashKind kind() {
        return getBlockState().getBlock() instanceof TrashCanBlock can ? can.kind() : TrashKind.ITEM;
    }

    /** Items, fluid units or energy units destroyed since load. */
    public long destroyed() {
        return destroyed;
    }

    /** Records {@code amount} destroyed; loader adapters call this when they accept a resource. */
    public void destroy(long amount) {
        if (amount > 0) destroyed += amount;
    }

    @Override
    public int getContainerSize() {
        return kind() == TrashKind.ITEM ? SLOTS : 0;
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (kind() == TrashKind.ITEM) destroy(stack.getCount());
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return kind() == TrashKind.ITEM;
    }

    @Override
    public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 64.0;
    }

    @Override
    public void clearContent() {
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return kind() == TrashKind.ITEM ? FACE_SLOTS : NO_SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return kind() == TrashKind.ITEM;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return false;
    }
}
