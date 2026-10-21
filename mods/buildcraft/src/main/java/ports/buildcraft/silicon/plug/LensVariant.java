package ports.buildcraft.silicon.plug;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.DyeColor;
import ports.buildcraft.statement.ColourText;

/**
 * Upstream {@code ItemPluggableLens.LensData}: a lens or filter of one dye colour, or clear. Upstream's 34 damage
 * values become one item each.
 */
public record LensVariant(@Nullable DyeColor colour, boolean filter) {
    public static final List<LensVariant> VALUES;

    static {
        List<LensVariant> values = new ArrayList<>();
        for (boolean filter : new boolean[] {false, true}) {
            for (DyeColor colour : DyeColor.values()) values.add(new LensVariant(colour, filter));
            values.add(new LensVariant(null, filter));
        }
        VALUES = List.copyOf(values);
    }

    public String itemName() {
        return (filter ? "filter_" : "lens_") + (colour == null ? "clear" : colour.getName());
    }

    /** Upstream {@code getItemStackDisplayName}: the colour, in its own text colour, then "Lens" or "Filter". */
    public MutableComponent localizedName() {
        Component colourName = colour == null ? Component.translatable("color.clear") : ColourText.of(colour);
        return Component.translatable(filter ? "item.buildcraft.filter" : "item.buildcraft.lens", colourName);
    }
}
