package ports.buildcraft.transport.behaviour;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ports.buildcraft.lib.Power;
import ports.buildcraft.platform.EnergyPort;
import ports.buildcraft.transport.PipeBlockEntity;
import ports.buildcraft.transport.flow.ItemFlow;

/**
 * Upstream {@code PipeBehaviourObsidian}: with exactly one connection, the pipe's open face is the one opposite it.
 * Items inside the pipe's block are sucked in for free; engine power sucks items in front of the open face, up to
 * four blocks out, nearest slice first, at 1/2 MJ per item plus 1/4 MJ per metre (at least one) from the pipe's
 * centre. Each power delivery that finds nothing still uses 1 MJ. Items the pipe drops itself wait 20 ticks, and a
 * loaded pipe waits 20 ticks before it uses power. Obsidian pipes do not connect to each other.
 */
public class ObsidianBehaviour extends PipeBehaviour {
    public static final long POWER_PER_ITEM = Power.MJ / 2;
    public static final long POWER_PER_METRE = Power.MJ / 4;
    private static final double INSERT_SPEED = 0.04;
    private static final int DROP_GAP = 20;
    private static final int MAX_DISTANCE = 4;
    private static final long MAX_REQUEST = 512 * Power.MJ;

    /** Upstream {@code entityDropTime}: items this pipe dropped, to the game time they may be picked up. */
    private final Map<Entity, Long> entityDropTime = new WeakHashMap<>();
    private int toWaitTicks;

    private final EnergyPort energy = new EnergyPort() {
        @Override
        public long receive(long amount, boolean simulate) {
            if (amount <= 0) return 0;
            long offered = Power.fromUnits(Math.min(amount, Power.toUnits(MAX_REQUEST)));
            long used = offered - receivePower(offered, simulate);
            if (used <= 0) return 0;
            return Math.min(amount, (used + Power.MICRO_MJ_PER_UNIT - 1) / Power.MICRO_MJ_PER_UNIT);
        }

        @Override public long extract(long amount, boolean simulate) { return 0; }
        @Override public long stored() { return 0; }
        @Override public long capacity() { return Power.toUnits(MAX_REQUEST); }
        @Override public boolean canReceive() { return true; }
        @Override public boolean canExtract() { return false; }
        @Override public boolean acceptsRedstoneEngine() { return true; }
    };

    public ObsidianBehaviour(PipeBlockEntity pipe) {
        super(pipe);
    }

    @Override
    public boolean canConnect(Direction face, PipeBehaviour other) {
        return !(other instanceof ObsidianBehaviour);
    }

    @Override
    public void onTick() {
        if (toWaitTicks > 0) toWaitTicks--;
    }

    @Override
    public void onEntityCollide(Entity entity) {
        Direction openFace = openFace();
        if (openFace != null) trySuckEntity(entity, openFace, Long.MAX_VALUE, false);
    }

    @Override
    public void onDrop(ItemEntity entity) {
        entityDropTime.put(entity, pipe.getLevel().getGameTime() + DROP_GAP);
    }

    /** The face opposite the pipe's only connection, or null unless it has exactly one. */
    @Nullable
    public Direction openFace() {
        Direction openFace = null;
        for (Direction face : Direction.values()) {
            if (!pipe.isConnected(face)) continue;
            if (openFace != null) return null;
            openFace = face.getOpposite();
        }
        return openFace;
    }

    /** Upstream {@code getSuckingBox}: the slice {@code distance} blocks out from the open face. */
    public AABB suckingBox(Direction openFace, int distance) {
        AABB box = new AABB(Vec3.atCenterOf(pipe.getBlockPos()), Vec3.atCenterOf(pipe.getBlockPos())).inflate(0.4);
        box = box.move(openFace.getStepX() * distance, openFace.getStepY() * distance, openFace.getStepZ() * distance);
        return switch (openFace.getAxis()) {
            case X -> box.inflate(0.5, distance, distance);
            case Y -> box.inflate(distance, 0.5, distance);
            case Z -> box.inflate(distance, distance, 0.5);
        };
    }

    /** Upstream {@code IMjRedstoneReceiver.receivePower}: sucks in the first item it can afford; returns the power left. */
    public long receivePower(long microJoules, boolean simulate) {
        if (toWaitTicks > 0) return microJoules;
        Direction openFace = openFace();
        if (openFace == null) return microJoules;
        for (int distance = 1; distance <= MAX_DISTANCE; distance++) {
            List<Entity> entities = pipe.getLevel().getEntitiesOfClass(Entity.class, suckingBox(openFace, distance));
            for (Entity entity : entities) {
                long leftOver = trySuckEntity(entity, openFace, microJoules, simulate);
                if (leftOver < microJoules) return leftOver;
            }
        }
        return Math.max(0, microJoules - Power.MJ);
    }

    @Override
    public long receiveRedstonePower(long microJoules, boolean simulate) {
        return receivePower(microJoules, simulate);
    }

    @Override
    public EnergyPort energyPort(@Nullable Direction side) {
        return energy;
    }

    /** @return the power left over; {@link Long#MAX_VALUE} sucks the whole stack for free. */
    private long trySuckEntity(Entity entity, Direction faceFrom, long power, boolean simulate) {
        if (!entity.isAlive() || entity instanceof LivingEntity) return power;
        Long tickPickup = entityDropTime.get(entity);
        if (tickPickup != null) {
            if (pipe.getLevel().getGameTime() < tickPickup) return power;
            entityDropTime.remove(entity);
        }
        if (!(pipe.flow() instanceof ItemFlow items)) return power;
        long powerPerItem;
        int max;
        if (power == Long.MAX_VALUE) {
            max = Integer.MAX_VALUE;
            powerPerItem = 0;
        } else {
            double distance = Math.sqrt(entity.distanceToSqr(Vec3.atCenterOf(pipe.getBlockPos())));
            powerPerItem = (long) (Math.max(1, distance) * POWER_PER_METRE + POWER_PER_ITEM);
            max = (int) Math.min(Integer.MAX_VALUE, power / powerPerItem);
        }
        ItemStack extracted = extract(entity, max, simulate);
        if (extracted.isEmpty()) return power;
        if (!simulate) items.insertItemsForce(extracted, faceFrom, null, INSERT_SPEED);
        return power - powerPerItem * extracted.getCount();
    }

    /**
     * Upstream {@code ItemTransactorHelper.getTransactorForEntity}: an entity's inventory (vanilla containers such
     * as chest minecarts) first, then a dropped item. Upstream also took arrows that can be picked up.
     */
    private static ItemStack extract(Entity entity, int max, boolean simulate) {
        if (max < 1) return ItemStack.EMPTY;
        if (entity instanceof Container container) {
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (stack.isEmpty()) continue;
                int count = Math.min(max, stack.getCount());
                return simulate ? copyWithCount(stack, count) : container.removeItem(slot, count);
            }
            return ItemStack.EMPTY;
        }
        if (entity instanceof ItemEntity item) {
            ItemStack current = item.getItem().copy();
            if (current.isEmpty()) return ItemStack.EMPTY;
            ItemStack extracted = current.split(max);
            if (!simulate) {
                if (current.isEmpty()) item.discard();
                else item.setItem(current);
            }
            return extracted;
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack copyWithCount(ItemStack stack, int count) {
        ItemStack copy = stack.copy();
        copy.setCount(count);
        return copy;
    }

    /** Upstream skips writing which items it dropped and waits {@code DROP_GAP} ticks after loading instead. */
    @Override
    public void load(CompoundTag tag) {
        toWaitTicks = DROP_GAP;
    }
}
