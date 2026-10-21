package ports.buildcraft.silicon.gate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.lib.BCMenu;
import ports.buildcraft.platform.Platform;
import ports.buildcraft.silicon.BCSilicon;
import ports.buildcraft.statement.ActionWrapper;
import ports.buildcraft.statement.FullStatement;
import ports.buildcraft.statement.Statement;
import ports.buildcraft.statement.StatementManager;
import ports.buildcraft.statement.StatementParameter;
import ports.buildcraft.statement.StatementWrapper;
import ports.buildcraft.statement.TriggerWrapper;

/**
 * Upstream {@code ContainerGate}: the player inventory below the gate's pairs, and the triggers and actions
 * the gate can use, sent once as the menu opens. Whether each trigger and action is on is synced as menu
 * data. Statement, parameter and grouping changes come back on {@link #CHANNEL}; the server only accepts
 * statements the gate could use, as the GUI offers them.
 */
public class GateMenu extends BCMenu {
    public static final ResourceLocation CHANNEL = BuildCraft.id("gate");
    private static final int OP_STATEMENT = 0, OP_PARAMETER = 1, OP_CONNECTION = 2;

    public final Direction side;
    public final GateVariant variant;
    public final int slotHeight;
    public final List<Group<TriggerWrapper>> triggerGroups;
    public final List<Group<ActionWrapper>> actionGroups;
    private final Set<TriggerWrapper> possibleTriggers;
    private final Set<ActionWrapper> possibleActions;
    @Nullable private final PluggableGate gate;
    private final BitsSlot triggerOn, actionOn;

    /** One group of the statement sources: the centre's, led by an empty slot, or one face's. */
    public record Group<S extends StatementWrapper>(@Nullable Direction part, List<S> values) {}

    /** What the client is sent as the menu opens. */
    private record Opening(BlockPos pos, Direction side, GateVariant variant, SortedSet<TriggerWrapper> triggers, SortedSet<ActionWrapper> actions) {
        static Opening read(FriendlyByteBuf buffer) {
            BlockPos pos = buffer.readBlockPos();
            Direction side = buffer.readEnum(Direction.class);
            GateVariant variant = GateVariant.readFromNbt(buffer.readNbt());
            SortedSet<TriggerWrapper> triggers = new TreeSet<>();
            int count = buffer.readVarInt();
            for (int i = 0; i < count; i++) {
                Statement statement = StatementManager.getStatement(buffer.readUtf());
                int part = buffer.readByte();
                TriggerWrapper wrapper = statement == null ? null : TriggerType.read(statement, part);
                if (wrapper != null && wrapper.minParameters() <= variant.numTriggerArgs()) triggers.add(wrapper);
            }
            SortedSet<ActionWrapper> actions = new TreeSet<>();
            count = buffer.readVarInt();
            for (int i = 0; i < count; i++) {
                Statement statement = StatementManager.getStatement(buffer.readUtf());
                int part = buffer.readByte();
                ActionWrapper wrapper = statement == null ? null : ActionType.read(statement, part);
                if (wrapper != null && wrapper.minParameters() <= variant.numActionArgs()) actions.add(wrapper);
            }
            return new Opening(pos, side, variant, triggers, actions);
        }

        void write(FriendlyByteBuf buffer) {
            buffer.writeBlockPos(pos);
            buffer.writeEnum(side);
            buffer.writeNbt(variant.writeToNbt());
            buffer.writeVarInt(triggers.size());
            for (TriggerWrapper trigger : triggers) writeStatement(buffer, trigger);
            buffer.writeVarInt(actions.size());
            for (ActionWrapper action : actions) writeStatement(buffer, action);
        }
    }

    public GateMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, inventory, Opening.read(buffer), null);
    }

    private GateMenu(int id, Inventory inventory, Opening opening, @Nullable PluggableGate gate) {
        super(BCSilicon.GATE_MENU.get(), id, opening.pos);
        this.gate = gate;
        side = opening.side;
        variant = opening.variant;
        possibleTriggers = opening.triggers;
        possibleActions = opening.actions;
        triggerGroups = groups(possibleTriggers);
        actionGroups = groups(possibleActions);
        int slots = variant.numSlots();
        slotHeight = slots > 4 ? (slots + 1) / 2 : slots;
        addFullPlayerInventory(inventory, 33 + slotHeight * 18);
        triggerOn = new BitsSlot(gate == null ? null : () -> gate.logic().triggerOn);
        actionOn = new BitsSlot(gate == null ? null : () -> gate.logic().actionOn);
        addDataSlot(triggerOn);
        addDataSlot(actionOn);
    }

    public static void open(ServerPlayer player, PluggableGate gate) {
        GateLogic logic = gate.logic();
        Opening opening = new Opening(gate.holder.getBlockPos(), gate.side, logic.variant, logic.getAllValidTriggers(), logic.getAllValidActions());
        Platform.get().openMenu(player, new SimpleMenuProvider((id, inventory, p) -> new GateMenu(id, inventory, opening, gate),
                logic.variant.localizedName()), opening::write);
    }

    private static void writeStatement(FriendlyByteBuf buffer, StatementWrapper wrapper) {
        buffer.writeUtf(wrapper.getUniqueTag());
        buffer.writeByte(StatementWrapper.partIndex(wrapper.sourcePart));
    }

    /** Upstream {@code ContainerGate.refresh}: the centre group always comes first, led by an empty slot. */
    private static <S extends StatementWrapper> List<Group<S>> groups(Collection<S> sorted) {
        List<S> centre = new ArrayList<>();
        centre.add(null);
        Map<Direction, List<S>> faces = new EnumMap<>(Direction.class);
        for (S wrapper : sorted) {
            if (wrapper.sourcePart == null) centre.add(wrapper);
            else faces.computeIfAbsent(wrapper.sourcePart, part -> new ArrayList<>()).add(wrapper);
        }
        List<Group<S>> groups = new ArrayList<>();
        groups.add(new Group<>(null, centre));
        for (Direction face : Direction.values()) {
            List<S> values = faces.get(face);
            if (values != null) groups.add(new Group<>(face, values));
        }
        return groups;
    }

    public boolean isTriggerOn(int index) {
        return triggerOn.isSet(index);
    }

    public boolean isActionOn(int index) {
        return actionOn.isSet(index);
    }

    /** True if {@code value} is one of the gate's possible triggers, as the GUI's rings and sources offer. */
    public boolean isPossibleTrigger(@Nullable TriggerWrapper value) {
        return value != null && possibleTriggers.contains(value);
    }

    public boolean isPossibleAction(@Nullable ActionWrapper value) {
        return value != null && possibleActions.contains(value);
    }

    @Override
    public boolean stillValid(Player player) {
        return super.stillValid(player) && (gate == null || gate.holder.getPluggable(side) == gate && !gate.holder.isRemoved());
    }

    // Client to server

    public static void writeStatementChange(FriendlyByteBuf buffer, int containerId, int index, boolean action, @Nullable StatementWrapper value) {
        buffer.writeVarInt(containerId);
        buffer.writeByte(OP_STATEMENT);
        buffer.writeByte(index);
        buffer.writeBoolean(action);
        buffer.writeBoolean(value != null);
        if (value != null) writeStatement(buffer, value);
    }

    public static void writeParameterChange(FriendlyByteBuf buffer, int containerId, int index, boolean action, int param, StatementParameter value) {
        buffer.writeVarInt(containerId);
        buffer.writeByte(OP_PARAMETER);
        buffer.writeByte(index);
        buffer.writeBoolean(action);
        buffer.writeByte(param);
        buffer.writeNbt(StatementManager.writeParameter(value));
    }

    public static void writeConnectionChange(FriendlyByteBuf buffer, int containerId, int index, boolean connected) {
        buffer.writeVarInt(containerId);
        buffer.writeByte(OP_CONNECTION);
        buffer.writeByte(index);
        buffer.writeBoolean(connected);
    }

    public static void receive(ServerPlayer player, FriendlyByteBuf buffer) {
        int containerId = buffer.readVarInt();
        if (player.containerMenu instanceof GateMenu menu && menu.containerId == containerId && menu.gate != null && menu.stillValid(player)) {
            menu.handle(menu.gate.logic(), buffer);
        }
    }

    private void handle(GateLogic logic, FriendlyByteBuf buffer) {
        int op = buffer.readByte();
        int index = buffer.readUnsignedByte();
        if (op == OP_CONNECTION) {
            boolean connected = buffer.readBoolean();
            if (index >= logic.connections.length) return;
            logic.connections[index] = connected;
            changed(logic);
            return;
        }
        boolean action = buffer.readBoolean();
        if (index >= logic.statements.length) return;
        GateLogic.StatementPair pair = logic.statements[index];
        if (op == OP_STATEMENT) {
            boolean present = buffer.readBoolean();
            String tag = present ? buffer.readUtf() : "";
            int part = present ? buffer.readByte() : StatementWrapper.CENTER_INDEX;
            Statement statement = present ? StatementManager.getStatement(tag) : null;
            if (present && statement == null) return;
            boolean set = action
                    ? setIfAllowed(pair.action, statement == null ? null : ActionType.read(statement, part), possibleActions, logic, true)
                    : setIfAllowed(pair.trigger, statement == null ? null : TriggerType.read(statement, part), possibleTriggers, logic, false);
            if (set) changed(logic);
        } else if (op == OP_PARAMETER) {
            int param = buffer.readUnsignedByte();
            CompoundTag tag = buffer.readNbt();
            FullStatement<?> full = action ? pair.action : pair.trigger;
            StatementParameter value = tag == null ? null : StatementManager.readParameter(tag);
            if (value == null || !full.canSet(param, value)) return;
            full.set(param, value);
            changed(logic);
        }
    }

    /**
     * Sets a statement the gate could use: one of its possible statements, or one already in another of its
     * pairs, which the GUI lets the player drag across.
     */
    private static <S extends StatementWrapper> boolean setIfAllowed(FullStatement<S> full, @Nullable S value, Set<S> possible,
            GateLogic logic, boolean action) {
        if (value != null && !possible.contains(value) && !inGate(logic, value, action)) return false;
        if (!full.canSet(value)) return false;
        full.set(value);
        return true;
    }

    private static boolean inGate(GateLogic logic, StatementWrapper value, boolean action) {
        for (GateLogic.StatementPair pair : logic.statements) {
            if (value.equals(action ? pair.action.get() : pair.trigger.get())) return true;
        }
        return false;
    }

    private static void changed(GateLogic logic) {
        logic.pluggable.queueSync();
        logic.pluggable.holder.setChanged();
    }

    /** A boolean per pair packed into one menu data value: read from the gate on the server, stored on the client. */
    private static final class BitsSlot extends DataSlot {
        @Nullable private final Supplier<boolean[]> source;
        private int value;

        BitsSlot(@Nullable Supplier<boolean[]> source) {
            this.source = source;
        }

        boolean isSet(int index) {
            return ((get() >>> index) & 1) == 1;
        }

        @Override
        public int get() {
            if (source == null) return value;
            boolean[] bits = source.get();
            int packed = 0;
            for (int i = 0; i < bits.length; i++) {
                if (bits[i]) packed |= 1 << i;
            }
            return packed;
        }

        @Override
        public void set(int value) {
            this.value = value;
        }
    }
}
