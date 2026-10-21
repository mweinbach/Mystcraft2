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

/** Upstream {@code ActionParameterSignal}: another wire colour a pipe signal action powers. */
public final class ActionParameterSignal implements StatementParameter {
    public static final String TAG = "buildcraft:pipeWireAction";
    public static final ActionParameterSignal EMPTY = new ActionParameterSignal(null);
    private static final Map<DyeColor, ActionParameterSignal> SIGNALS = new EnumMap<>(DyeColor.class);

    static {
        for (DyeColor colour : DyeColor.values()) SIGNALS.put(colour, new ActionParameterSignal(colour));
    }

    @Nullable
    public final DyeColor colour;

    private ActionParameterSignal(@Nullable DyeColor colour) {
        this.colour = colour;
    }

    public static ActionParameterSignal get(@Nullable DyeColor colour) {
        return colour == null ? EMPTY : SIGNALS.get(colour);
    }

    public static ActionParameterSignal readFromNbt(CompoundTag tag) {
        return tag.contains("color", Tag.TAG_ANY_NUMERIC) ? get(DyeColor.byId(tag.getByte("color"))) : EMPTY;
    }

    @Override
    public void writeToNbt(CompoundTag tag) {
        if (colour != null) tag.putByte("color", (byte) colour.getId());
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
        return colour == null ? null : TriggerPipeSignal.spriteFor(true, colour);
    }

    @Nullable
    @Override
    public StatementParameter onClick(StatementContainer source, Statement statement, ItemStack stack, MouseClick mouse) {
        return null;
    }

    @Nullable
    @Override
    public Component getDescription() {
        return colour == null ? null : Component.translatable("gate.action.pipe.wire", ColourText.of(colour));
    }

    @Override
    public String getUniqueTag() {
        return TAG;
    }

    @Override
    public StatementParameter rotateLeft() {
        return this;
    }

    /** No parameter, then each colour the gate's pipe has wire of. */
    @Nullable
    @Override
    public StatementParameter[] getPossible(StatementContainer source) {
        if (!(source instanceof Gate gate)) return null;
        List<StatementParameter> possible = new ArrayList<>();
        possible.add(EMPTY);
        for (DyeColor colour : DyeColor.values()) {
            if (TriggerPipeSignal.doesGateHaveColour(gate, colour)) possible.add(get(colour));
        }
        return possible.toArray(new StatementParameter[0]);
    }
}
