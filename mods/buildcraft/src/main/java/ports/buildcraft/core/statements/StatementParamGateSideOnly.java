package ports.buildcraft.core.statements;

import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.statement.Statement;
import ports.buildcraft.statement.StatementContainer;
import ports.buildcraft.statement.StatementParameter;

/** Upstream {@code StatementParamGateSideOnly}: limits a redstone statement to the face the gate is on. */
public enum StatementParamGateSideOnly implements StatementParameter {
    ANY(false),
    SPECIFIC(true);

    public static final String TAG = "buildcraft:redstoneGateSideOnly";
    private static final StatementParamGateSideOnly[] POSSIBLE_ANY = {ANY, SPECIFIC};
    private static final StatementParamGateSideOnly[] POSSIBLE_SPECIFIC = {SPECIFIC, ANY};
    private static final ResourceLocation SPRITE = BuildCraft.id("textures/triggers/redstone_gate_side_only.png");

    public final boolean isSpecific;

    StatementParamGateSideOnly(boolean isSpecific) {
        this.isSpecific = isSpecific;
    }

    public static StatementParamGateSideOnly readFromNbt(CompoundTag tag) {
        return tag.getBoolean("isOn") ? SPECIFIC : ANY;
    }

    @Override
    public void writeToNbt(CompoundTag tag) {
        tag.putBoolean("isOn", isSpecific);
    }

    @Override
    public ItemStack getItemStack() {
        return ItemStack.EMPTY;
    }

    @Nullable
    @Override
    public ResourceLocation getSprite() {
        return isSpecific ? SPRITE : null;
    }

    @Override
    public DrawType getDrawType() {
        return DrawType.SPRITE_ONLY;
    }

    @Nullable
    @Override
    public StatementParameter onClick(StatementContainer source, Statement statement, ItemStack stack, MouseClick mouse) {
        return null;
    }

    @Nullable
    @Override
    public Component getDescription() {
        return isSpecific ? Component.translatable("gate.parameter.redstone.gateSideOnly") : null;
    }

    @Override
    public String getUniqueTag() {
        return TAG;
    }

    @Override
    public StatementParameter rotateLeft() {
        return this;
    }

    @Override
    public StatementParameter[] getPossible(StatementContainer source) {
        return isSpecific ? POSSIBLE_SPECIFIC : POSSIBLE_ANY;
    }
}
