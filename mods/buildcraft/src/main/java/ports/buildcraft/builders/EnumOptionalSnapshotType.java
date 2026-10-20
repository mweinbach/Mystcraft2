package ports.buildcraft.builders;

import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.util.StringRepresentable;
import ports.buildcraft.builders.snapshot.EnumSnapshotType;

/** Upstream {@code EnumOptionalSnapshotType}: the snapshot a builder holds, shown in its slot. */
public enum EnumOptionalSnapshotType implements StringRepresentable {
    NONE(null),
    TEMPLATE(EnumSnapshotType.TEMPLATE),
    BLUEPRINT(EnumSnapshotType.BLUEPRINT);

    @Nullable public final EnumSnapshotType type;

    EnumOptionalSnapshotType(@Nullable EnumSnapshotType type) {
        this.type = type;
    }

    public static EnumOptionalSnapshotType fromNullable(@Nullable EnumSnapshotType type) {
        if (type == null) return NONE;
        return switch (type) {
            case TEMPLATE -> TEMPLATE;
            case BLUEPRINT -> BLUEPRINT;
        };
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
