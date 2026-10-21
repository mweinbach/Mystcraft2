package ports.buildcraft.statement;

import java.util.Arrays;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;

/**
 * Upstream {@code FullStatement}: one statement slot of a gate with its parameters. Upstream's network
 * form and GUI change listener are replaced by the gate's NBT sync and its GUI channel.
 */
public class FullStatement<S extends Statement> {
    public final StatementType<S> type;
    private final StatementParameter[] params;
    @Nullable
    private S statement;

    public FullStatement(StatementType<S> type, int maxParams) {
        this.type = type;
        this.params = new StatementParameter[maxParams];
    }

    public void readFromNbt(CompoundTag tag) {
        statement = type.readFromNbt(tag.getCompound("s"));
        if (statement == null) {
            Arrays.fill(params, null);
            return;
        }
        for (int p = 0; p < params.length; p++) params[p] = StatementManager.readParameter(tag.getCompound(Integer.toString(p)));
    }

    public CompoundTag writeToNbt() {
        CompoundTag tag = new CompoundTag();
        if (statement != null) {
            tag.put("s", type.writeToNbt(statement));
            for (int p = 0; p < params.length; p++) {
                if (params[p] != null) tag.put(Integer.toString(p), StatementManager.writeParameter(params[p]));
            }
        }
        return tag;
    }

    @Nullable
    public S get() {
        return statement;
    }

    /** Upstream keeps a parameter one slot past the statement's maximum, which {@link Statement#createParameter} then clears. */
    public void set(@Nullable S to) {
        statement = to;
        if (statement == null) {
            Arrays.fill(params, null);
            return;
        }
        for (int i = 0; i < params.length; i++) {
            params[i] = i > statement.maxParameters() ? null : statement.createParameter(params[i], i);
        }
    }

    public boolean canSet(@Nullable S value) {
        return value == null || value.minParameters() <= params.length;
    }

    /** Upstream {@code IReference.convertToType}, falling back to the statement type's own conversion. */
    @Nullable
    public S convertToType(@Nullable Object value) {
        if (value == null) return null;
        S converted = null;
        if (type.clazz.isInstance(value)) converted = type.clazz.cast(value);
        else if (value instanceof GuiSlot slot) converted = slot.convertTo(type.clazz);
        return converted != null ? converted : type.convertToType(value);
    }

    @Nullable
    public StatementParameter get(int index) {
        return params[index];
    }

    public void set(int index, @Nullable StatementParameter param) {
        params[index] = param;
    }

    /** Upstream {@code ParamRef.canSet}: the statement must keep the value as its parameter in that slot. */
    public boolean canSet(int index, @Nullable StatementParameter param) {
        return statement != null && index >= 0 && index < params.length && statement.createParameter(param, index) == param;
    }

    public int getParamCount() {
        return params.length;
    }

    public StatementParameter[] getParameters() {
        return params;
    }
}
