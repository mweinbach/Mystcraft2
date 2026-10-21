package ports.buildcraft.statement;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Upstream {@code IGuiSlot} and {@code IConvertable}: something a gate GUI slot can show and drag around. */
public interface GuiSlot {
    String getUniqueTag();

    @Nullable
    Component getDescription();

    default List<Component> getTooltip() {
        Component description = getDescription();
        return description == null ? List.of() : List.of(description);
    }

    /** The full path of a 16 by 16 icon texture, or null to draw nothing. */
    @Nullable
    ResourceLocation getSprite();

    /** Upstream {@code IConvertable.convertTo}: this slot as {@code type}, or something equivalent to it, or null. */
    @Nullable
    default <T> T convertTo(Class<T> type) {
        return type.isInstance(this) ? type.cast(this) : null;
    }
}
