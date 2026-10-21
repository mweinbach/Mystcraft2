package ports.buildcraft.statement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Upstream {@code StatementManager}: every statement by unique tag, parameter readers by kind, and the
 * providers that offer statements to a container. Upstream {@code StatementTypeParam}'s NBT form is
 * {@link #writeParameter} and {@link #readParameter}.
 */
public final class StatementManager {
    private static final Map<String, Statement> STATEMENTS = new HashMap<>();
    private static final Map<String, Function<CompoundTag, StatementParameter>> PARAMETERS = new HashMap<>();
    private static final List<TriggerProvider> TRIGGER_PROVIDERS = new ArrayList<>();
    private static final List<ActionProvider> ACTION_PROVIDERS = new ArrayList<>();

    static {
        registerParameter(StatementParameterItemStack::new);
    }

    private StatementManager() {}

    public static void registerTriggerProvider(TriggerProvider provider) {
        if (!TRIGGER_PROVIDERS.contains(provider)) TRIGGER_PROVIDERS.add(provider);
    }

    public static void registerActionProvider(ActionProvider provider) {
        if (!ACTION_PROVIDERS.contains(provider)) ACTION_PROVIDERS.add(provider);
    }

    public static void registerStatement(String tag, Statement statement) {
        STATEMENTS.put(tag, statement);
    }

    /** Registers a parameter reader under the kind of the parameter it reads from an empty tag. */
    public static void registerParameter(Function<CompoundTag, StatementParameter> reader) {
        PARAMETERS.put(reader.apply(new CompoundTag()).getUniqueTag(), reader);
    }

    @Nullable
    public static Statement getStatement(String tag) {
        return STATEMENTS.get(tag);
    }

    @Nullable
    public static StatementParameter readParameter(CompoundTag tag) {
        Function<CompoundTag, StatementParameter> reader = PARAMETERS.get(tag.getString("kind"));
        return reader == null ? null : reader.apply(tag);
    }

    public static CompoundTag writeParameter(@Nullable StatementParameter parameter) {
        CompoundTag tag = new CompoundTag();
        if (parameter != null) {
            parameter.writeToNbt(tag);
            tag.putString("kind", parameter.getUniqueTag());
        }
        return tag;
    }

    public static List<Trigger.Internal> getInternalTriggers(StatementContainer container) {
        LinkedHashSet<Trigger.Internal> triggers = new LinkedHashSet<>();
        for (TriggerProvider provider : TRIGGER_PROVIDERS) provider.addInternalTriggers(triggers, container);
        return new ArrayList<>(triggers);
    }

    public static List<Trigger.InternalSided> getInternalSidedTriggers(StatementContainer container, Direction side) {
        LinkedHashSet<Trigger.InternalSided> triggers = new LinkedHashSet<>();
        for (TriggerProvider provider : TRIGGER_PROVIDERS) provider.addInternalSidedTriggers(triggers, container, side);
        return new ArrayList<>(triggers);
    }

    public static List<Trigger.External> getExternalTriggers(Direction side, BlockEntity tile) {
        LinkedHashSet<Trigger.External> triggers = new LinkedHashSet<>();
        for (TriggerProvider provider : TRIGGER_PROVIDERS) provider.addExternalTriggers(triggers, side, tile);
        return new ArrayList<>(triggers);
    }

    public static List<Action.Internal> getInternalActions(StatementContainer container) {
        LinkedHashSet<Action.Internal> actions = new LinkedHashSet<>();
        for (ActionProvider provider : ACTION_PROVIDERS) provider.addInternalActions(actions, container);
        return new ArrayList<>(actions);
    }

    public static List<Action.InternalSided> getInternalSidedActions(StatementContainer container, Direction side) {
        LinkedHashSet<Action.InternalSided> actions = new LinkedHashSet<>();
        for (ActionProvider provider : ACTION_PROVIDERS) provider.addInternalSidedActions(actions, container, side);
        return new ArrayList<>(actions);
    }

    public static List<Action.External> getExternalActions(Direction side, BlockEntity tile) {
        LinkedHashSet<Action.External> actions = new LinkedHashSet<>();
        for (ActionProvider provider : ACTION_PROVIDERS) provider.addExternalActions(actions, side, tile);
        return new ArrayList<>(actions);
    }
}
