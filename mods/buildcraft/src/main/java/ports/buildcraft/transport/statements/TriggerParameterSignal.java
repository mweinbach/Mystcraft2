package ports.buildcraft.transport.statements;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import ports.buildcraft.statement.ColourText;
import ports.buildcraft.statement.Gate;
import ports.buildcraft.statement.Statement;
import ports.buildcraft.statement.StatementContainer;
import ports.buildcraft.statement.StatementParameter;

/** Upstream {@code TriggerParameterSignal}: another wire colour a pipe signal trigger needs on, or off. */
public final class TriggerParameterSignal implements StatementParameter {
    public static final String TAG = "buildcraft:pipeWireTrigger";
    public static final TriggerParameterSignal EMPTY = new TriggerParameterSignal(false, null);
    private static final Map<DyeColor, TriggerParameterSignal> ON = new EnumMap<>(DyeColor.class);
    private static final Map<DyeColor, TriggerParameterSignal> OFF = new EnumMap<>(DyeColor.class);

    static {
        for (DyeColor colour : DyeColor.values()) {
            ON.put(colour, new TriggerParameterSignal(true, colour));
            OFF.put(colour, new TriggerParameterSignal(false, colour));
        }
    }

    public final boolean active;
    @Nullable
    public final DyeColor colour;

    private TriggerParameterSignal(boolean active, @Nullable DyeColor colour) {
        this.active = active;
        this.colour = colour;
    }

    public static TriggerParameterSignal get(boolean active, @Nullable DyeColor colour) {
        if (colour == null) return EMPTY;
        return (active ? ON : OFF).get(colour);
    }

    public static TriggerParameterSignal readFromNbt(CompoundTag tag) {
        if (!tag.contains("color", Tag.TAG_ANY_NUMERIC)) return EMPTY;
        return get(tag.getBoolean("active"), DyeColor.byId(tag.getByte("color")));
    }

    @Override
    public void writeToNbt(CompoundTag tag) {
        if (colour == null) return;
        tag.putByte("color", (byte) colour.getId());
        tag.putBoolean("active", active);
    }

    @Override
    public ItemStack getItemStack() {
        return ItemStack.EMPTY;
    }

    @Override
    public DrawType getDrawType() {
        return DrawType.SPRITE_ONLY;
    }

    @Nullable
    @Override
    public ResourceLocation getSprite() {
        return colour == null ? null : TriggerPipeSignal.spriteFor(active, colour);
    }

    @Nullable
    @Override
    public StatementParameter onClick(StatementContainer source, Statement statement, ItemStack stack, MouseClick mouse) {
        return null;
    }

    @Nullable
    @Override
    public Component getDescription() {
        return colour == null ? null : Component.translatable("gate.trigger.pipe.wire." + (active ? "active" : "inactive"), ColourText.of(colour));
    }

    @Override
    public String getUniqueTag() {
        return TAG;
    }

    @Override
    public StatementParameter rotateLeft() {
        return this;
    }

    /** No parameter, then each colour the gate's pipe has wire of, on and off. */
    @Nullable
    @Override
    public StatementParameter[] getPossible(StatementContainer source) {
        if (!(source instanceof Gate gate)) return null;
        List<StatementParameter> possible = new ArrayList<>();
        possible.add(EMPTY);
        for (DyeColor colour : DyeColor.values()) {
            if (TriggerPipeSignal.doesGateHaveColour(gate, colour)) {
                possible.add(get(true, colour));
                possible.add(get(false, colour));
            }
        }
        return possible.toArray(new StatementParameter[0]);
    }
}
