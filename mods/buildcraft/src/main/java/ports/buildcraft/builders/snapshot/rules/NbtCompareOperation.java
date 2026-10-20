package ports.buildcraft.builders.snapshot.rules;

import java.util.Arrays;
import net.minecraft.nbt.Tag;

/** Upstream {@code EnumNbtCompareOperation}: values are compared as the JSON they would be written as. */
public enum NbtCompareOperation {
    EQ("=") {
        @Override
        public boolean compare(Tag a, Tag b) {
            return NbtJson.toJson(a).toString().equals(NbtJson.toJson(b).toString());
        }
    },
    NQE("!=") {
        @Override
        public boolean compare(Tag a, Tag b) {
            return !EQ.compare(a, b);
        }
    };

    public final String symbol;

    NbtCompareOperation(String symbol) {
        this.symbol = symbol;
    }

    public static NbtCompareOperation byName(String name) {
        return Arrays.stream(values())
                .filter(type -> type.symbol.equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Compare operation not found: " + name));
    }

    public abstract boolean compare(Tag a, Tag b);
}
