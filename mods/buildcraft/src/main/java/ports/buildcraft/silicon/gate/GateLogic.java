package ports.buildcraft.silicon.gate;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.SortedSet;
import java.util.TreeSet;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BlockEntity;
import ports.buildcraft.statement.Action;
import ports.buildcraft.statement.ActionWrapper;
import ports.buildcraft.statement.FullStatement;
import ports.buildcraft.statement.Gate;
import ports.buildcraft.statement.RedstoneStatementContainer;
import ports.buildcraft.statement.Statement;
import ports.buildcraft.statement.StatementManager;
import ports.buildcraft.statement.StatementParameter;
import ports.buildcraft.statement.Trigger;
import ports.buildcraft.statement.TriggerWrapper;
import ports.buildcraft.transport.PipeBlockEntity;
import ports.buildcraft.transport.wire.WireEmitter;

/**
 * Upstream {@code GateLogic}: a gate's trigger and action pairs, how they are grouped, and resolving them
 * each tick, including which wire colours its actions emit. Upstream's statement and glow messages become
 * the pluggable's client data; whether each trigger and action is on reaches open GUIs through {@link GateMenu}.
 */
public class GateLogic implements Gate, RedstoneStatementContainer, WireEmitter {
    public final PluggableGate pluggable;
    public final GateVariant variant;
    public final StatementPair[] statements;
    /** Whether each pair is grouped with the next one. */
    public final boolean[] connections;
    public final boolean[] triggerOn, actionOn;
    /** Whether any action is active; the gate glows on clients while it is. */
    public boolean isOn;
    private final EnumSet<DyeColor> wireBroadcasts = EnumSet.noneOf(DyeColor.class);

    public GateLogic(PluggableGate pluggable, GateVariant variant) {
        this.pluggable = pluggable;
        this.variant = variant;
        int slots = variant.numSlots();
        statements = new StatementPair[slots];
        for (int s = 0; s < slots; s++) statements[s] = new StatementPair();
        connections = new boolean[slots - 1];
        triggerOn = new boolean[slots];
        actionOn = new boolean[slots];
    }

    // Saving and loading

    /** Upstream {@code GateLogic(PluggableGate, NBTTagCompound)}: the configuration and the colours being emitted. */
    public void readFromNbt(CompoundTag tag) {
        readConfigData(tag);
        wireBroadcasts.clear();
        int broadcasts = tag.getInt("wireBroadcasts");
        for (DyeColor colour : DyeColor.values()) {
            if ((broadcasts & (1 << colour.getId())) != 0) wireBroadcasts.add(colour);
        }
    }

    /** Upstream's legacy string statements came from 1.12 worlds, which the port does not load. */
    public void readConfigData(CompoundTag tag) {
        short packed = tag.getShort("connections");
        for (int i = 0; i < connections.length; i++) connections[i] = ((packed >>> i) & 1) == 1;
        for (int i = 0; i < statements.length; i++) {
            statements[i].trigger.readFromNbt(tag.getCompound("trigger[" + i + "]"));
            statements[i].action.readFromNbt(tag.getCompound("action[" + i + "]"));
        }
    }

    public CompoundTag writeToNbt() {
        CompoundTag tag = new CompoundTag();
        tag.put("variant", variant.writeToNbt());
        short packed = 0;
        for (int i = 0; i < connections.length; i++) {
            if (connections[i]) packed |= (short) (1 << i);
        }
        tag.putShort("connections", packed);
        for (int s = 0; s < statements.length; s++) {
            if (statements[s].trigger.get() != null) tag.put("trigger[" + s + "]", statements[s].trigger.writeToNbt());
            if (statements[s].action.get() != null) tag.put("action[" + s + "]", statements[s].action.writeToNbt());
        }
        int broadcasts = 0;
        for (DyeColor colour : wireBroadcasts) broadcasts |= 1 << colour.getId();
        tag.putInt("wireBroadcasts", broadcasts);
        return tag;
    }

    /** What the gate copier stores: the saved data without the colours being emitted. */
    public CompoundTag writeConfigData() {
        CompoundTag tag = writeToNbt();
        tag.remove("wireBroadcasts");
        return tag;
    }

    /** True when no pair holds a statement and no pairs are grouped: what upstream's copier meant by no information. */
    public boolean isEmpty() {
        for (boolean connected : connections) {
            if (connected) return false;
        }
        for (StatementPair pair : statements) {
            if (pair.trigger.get() != null || pair.action.get() != null) return false;
        }
        return true;
    }

    // Gate

    @Override
    public Direction getSide() {
        return pluggable.side;
    }

    @Override
    public PipeBlockEntity getPipe() {
        return pluggable.holder;
    }

    @Override
    public BlockEntity getTile() {
        return pluggable.holder;
    }

    @Nullable
    @Override
    public BlockEntity getNeighbourTile(Direction side) {
        return pluggable.holder.neighbourTile(side);
    }

    @Override
    public int getRedstoneInput(@Nullable Direction side) {
        return pluggable.holder.getRedstoneInput(side);
    }

    @Override
    public boolean setRedstoneOutput(@Nullable Direction side, int value) {
        return pluggable.holder.setRedstoneOutput(side, value);
    }

    // WireEmitter

    @Override
    public boolean isEmitting(DyeColor colour) {
        return wireBroadcasts.contains(colour);
    }

    @Override
    public void emitWire(DyeColor colour) {
        wireBroadcasts.add(colour);
    }

    // Logic

    /** True if the GUI shows the pairs in two columns; the server needs it for which pairs may be grouped. */
    public boolean isSplitInTwo() {
        return variant.numSlots() > 4;
    }

    public void resolveActions() {
        int groupCount = 0;
        int groupActive = 0;
        boolean prevIsOn = isOn;
        isOn = false;
        Arrays.fill(triggerOn, false);
        Arrays.fill(actionOn, false);
        wireBroadcasts.clear();

        for (int triggerIndex = 0; triggerIndex < statements.length; triggerIndex++) {
            StatementPair pair = statements[triggerIndex];
            TriggerWrapper trigger = pair.trigger.get();
            groupCount++;
            if (trigger != null && trigger.isTriggerActive(this, pair.trigger.getParameters().clone())) {
                groupActive++;
                triggerOn[triggerIndex] = true;
            }
            if (connections.length == triggerIndex || !connections[triggerIndex]) {
                boolean allActionsActive = variant.logic() == EnumGateLogic.AND ? groupActive == groupCount : groupActive > 0;
                for (int i = groupCount - 1; i >= 0; i--) {
                    int actionIndex = triggerIndex - i;
                    FullStatement<ActionWrapper> fullAction = statements[actionIndex].action;
                    ActionWrapper action = fullAction.get();
                    actionOn[actionIndex] = allActionsActive;
                    if (action == null) continue;
                    if (allActionsActive) {
                        isOn = true;
                        StatementParameter[] parameters = fullAction.getParameters().clone();
                        action.actionActivate(this, parameters);
                        pluggable.holder.behaviour().onActionActivate(action.getDelegate(), parameters);
                    } else {
                        action.actionDeactivated(this, fullAction.getParameters());
                    }
                }
                groupActive = 0;
                groupCount = 0;
            }
        }
        if (isOn != prevIsOn) pluggable.queueSync();
    }

    public void onTick() {
        if (pluggable.holder.getLevel() == null || pluggable.holder.getLevel().isClientSide) return;
        resolveActions();
    }

    public SortedSet<TriggerWrapper> getAllValidTriggers() {
        SortedSet<TriggerWrapper> set = new TreeSet<>();
        for (Trigger.Internal trigger : StatementManager.getInternalTriggers(this)) {
            if (isValidTrigger(trigger)) set.add(new TriggerWrapper.Internal(trigger));
        }
        for (Direction face : Direction.values()) {
            for (Trigger.InternalSided trigger : StatementManager.getInternalSidedTriggers(this, face)) {
                if (isValidTrigger(trigger)) set.add(new TriggerWrapper.InternalSided(trigger, face));
            }
            BlockEntity neighbour = getNeighbourTile(face);
            if (neighbour == null) continue;
            for (Trigger.External trigger : StatementManager.getExternalTriggers(face, neighbour)) {
                if (isValidTrigger(trigger)) set.add(new TriggerWrapper.External(trigger, face));
            }
        }
        return set;
    }

    public SortedSet<ActionWrapper> getAllValidActions() {
        SortedSet<ActionWrapper> set = new TreeSet<>();
        for (Action.Internal action : StatementManager.getInternalActions(this)) {
            if (isValidAction(action)) set.add(new ActionWrapper.Internal(action));
        }
        for (Direction face : Direction.values()) {
            for (Action.InternalSided action : StatementManager.getInternalSidedActions(this, face)) {
                if (isValidAction(action)) set.add(new ActionWrapper.InternalSided(action, face));
            }
            BlockEntity neighbour = getNeighbourTile(face);
            if (neighbour == null) continue;
            for (Action.External action : StatementManager.getExternalActions(face, neighbour)) {
                if (isValidAction(action)) set.add(new ActionWrapper.External(action, face));
            }
        }
        return set;
    }

    public boolean isValidTrigger(@Nullable Statement statement) {
        return statement != null && statement.minParameters() <= variant.numTriggerArgs();
    }

    public boolean isValidAction(@Nullable Statement statement) {
        return statement != null && statement.minParameters() <= variant.numActionArgs();
    }

    public final class StatementPair {
        public final FullStatement<TriggerWrapper> trigger = new FullStatement<>(TriggerType.INSTANCE, variant.numTriggerArgs());
        public final FullStatement<ActionWrapper> action = new FullStatement<>(ActionType.INSTANCE, variant.numActionArgs());
    }
}
