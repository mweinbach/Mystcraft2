package ports.buildcraft.transport.flow;

import java.util.EnumSet;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Upstream {@code TravellingItem}. While {@link #toCenter} is true the item moves from {@link #side}
 * to the pipe centre; otherwise it moves from the centre out through {@link #side}. Times are
 * absolute game times.
 */
public final class TravellingItem {
    ItemStack stack;
    boolean toCenter;
    double speed = 0.05;
    long tickStarted;
    long tickFinished;
    int timeToDest;
    Direction side;
    EnumSet<Direction> tried = EnumSet.noneOf(Direction.class);

    TravellingItem(ItemStack stack) {
        this.stack = stack;
    }

    TravellingItem(CompoundTag tag, long tickNow) {
        stack = ItemStack.of(tag.getCompound("stack"));
        toCenter = tag.getBoolean("toCenter");
        speed = Math.max(0.001, tag.getDouble("speed"));
        tickStarted = tag.getInt("tickStarted") + tickNow;
        tickFinished = tag.getInt("tickFinished") + tickNow;
        timeToDest = tag.getInt("timeToDest");
        side = tag.contains("side") ? Direction.from3DDataValue(tag.getByte("side")) : null;
        if (side == null || timeToDest == 0) toCenter = true;
        if (side == null) side = Direction.UP;
        byte triedBits = tag.getByte("tried");
        for (Direction face : Direction.values()) {
            if ((triedBits & (1 << face.ordinal())) != 0) tried.add(face);
        }
    }

    CompoundTag save(long tickNow) {
        CompoundTag tag = new CompoundTag();
        tag.put("stack", stack.save(new CompoundTag()));
        tag.putBoolean("toCenter", toCenter);
        tag.putDouble("speed", speed);
        tag.putInt("tickStarted", (int) (tickStarted - tickNow));
        tag.putInt("tickFinished", (int) (tickFinished - tickNow));
        tag.putInt("timeToDest", timeToDest);
        tag.putByte("side", (byte) side.get3DDataValue());
        int triedBits = 0;
        for (Direction face : tried) triedBits |= 1 << face.ordinal();
        tag.putByte("tried", (byte) triedBits);
        return tag;
    }

    int currentDelay(long tickNow) {
        return (int) Math.max(0, tickFinished - tickNow);
    }

    void genTimings(long now, double distance) {
        tickStarted = now;
        timeToDest = (int) Math.ceil(distance / speed);
        tickFinished = now + timeToDest;
    }

    boolean canMerge(TravellingItem with) {
        return toCenter == with.toCenter
                && side == with.side
                && Math.abs(tickFinished - with.tickFinished) < 4
                && stack.getMaxStackSize() >= stack.getCount() + with.stack.getCount()
                && ItemStack.isSameItemSameTags(stack, with.stack);
    }

    boolean mergeWith(TravellingItem with) {
        if (!canMerge(with)) return false;
        stack.grow(with.stack.getCount());
        return true;
    }

    public ItemStack stack() {
        return stack;
    }

    /** Upstream {@code getRenderPosition}, relative to the block origin. */
    public Vec3 renderPosition(long tick, float partialTicks, double pipeLength) {
        long diff = Math.max(1, tickFinished - tickStarted);
        double interp = Math.max(0, Math.min(1, (tick - tickStarted + partialTicks) / diff));
        Vec3 center = new Vec3(0.5, 0.5, 0.5);
        Vec3 sidePos = center.add(side.getStepX() * pipeLength, side.getStepY() * pipeLength, side.getStepZ() * pipeLength);
        Vec3 from = toCenter ? sidePos : center;
        Vec3 to = toCenter ? center : sidePos;
        return from.scale(1 - interp).add(to.scale(interp));
    }

    public boolean isFinished(long tick) {
        return tick > tickFinished;
    }

    public Direction side() {
        return side;
    }
}
