package ports.buildcraft.builders.filler;

import java.util.Arrays;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import ports.buildcraft.statement.StatementParameter;

/**
 * Upstream {@code FullStatement<IFillerPattern>} with four parameter slots, saved under upstream's NBT
 * keys. Changing the pattern keeps parameters that still have the right type and resets the rest.
 */
public final class PatternStatement {
    public static final int MAX_PARAMS = 4;
    private Pattern pattern = FillerPatterns.NONE;
    private final FillerParameter[] params = new FillerParameter[MAX_PARAMS];

    public Pattern get() {
        return pattern;
    }

    /** Upstream {@code FullStatement.set}, including its off-by-one that refreshes the slot at {@code maxParameters}. */
    /**
     * Upstream {@code FullStatement.set(statement, params)}. The loop starts at the last slot and stops above 0, so a
     * gate never writes the pattern's first parameter: that slot keeps the default {@link #set(Pattern)} just stored.
     */
    public void set(Pattern pattern, StatementParameter[] incoming) {
        set(pattern);
        if (incoming == null) return;
        for (int i = Math.min(params.length, incoming.length) - 1; i > 0; i--) {
            if (incoming[i] instanceof FillerParameter parameter) params[i] = parameter;
        }
    }

    public void set(@Nullable Pattern to) {
        pattern = to == null ? FillerPatterns.NONE : to;
        if (to == null) {
            Arrays.fill(params, null);
            return;
        }
        for (int i = 0; i < params.length; i++) {
            params[i] = i > pattern.maxParameters() ? null : pattern.createParameter(params[i], i);
        }
    }

    @Nullable
    public FillerParameter get(int index) {
        return params[index];
    }

    public void set(int index, @Nullable FillerParameter param) {
        params[index] = param;
    }

    /** Upstream {@code ParamRef.canSet}: the pattern would keep this value in this slot. */
    public boolean canSet(int index, @Nullable FillerParameter param) {
        return param != null && pattern.createParameter(param, index) == param;
    }

    public FillerParameter[] parameters() {
        return params.clone();
    }

    public void copyFrom(PatternStatement other) {
        pattern = other.pattern;
        System.arraycopy(other.params, 0, params, 0, params.length);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        CompoundTag statement = new CompoundTag();
        statement.putString("kind", pattern.uniqueTag());
        tag.put("s", statement);
        for (int p = 0; p < params.length; p++) {
            if (params[p] != null) tag.put(Integer.toString(p), FillerParameter.save(params[p]));
        }
        return tag;
    }

    public void load(CompoundTag tag) {
        Pattern loaded = FillerPatterns.byTag(tag.getCompound("s").getString("kind"));
        pattern = loaded == null ? FillerPatterns.NONE : loaded;
        for (int p = 0; p < params.length; p++) params[p] = FillerParameter.load(tag.getCompound(Integer.toString(p)));
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeNbt(save());
    }

    public void read(FriendlyByteBuf buffer) {
        CompoundTag tag = buffer.readNbt();
        if (tag != null) load(tag);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PatternStatement other && pattern == other.pattern && Arrays.equals(params, other.params);
    }

    @Override
    public int hashCode() {
        return 31 * pattern.hashCode() + Arrays.hashCode(params);
    }
}
