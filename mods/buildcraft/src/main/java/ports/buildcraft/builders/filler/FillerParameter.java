package ports.buildcraft.builders.filler;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.statement.Statement;
import ports.buildcraft.statement.StatementContainer;
import ports.buildcraft.statement.StatementParameter;

/**
 * Upstream {@code IStatementParameter} as the filler's pattern parameters use it: an enum constant
 * saved under its type's unique tag, with a sprite, a description and the values offered when the
 * player clicks it.
 */
public interface FillerParameter extends StatementParameter {
    Map<String, Function<CompoundTag, FillerParameter>> READERS = Map.of(
            PatternParameterAxis.TAG, PatternParameterAxis::readFromNbt,
            PatternParameterCenter.TAG, PatternParameterCenter::readFromNbt,
            PatternParameterFacing.TAG, PatternParameterFacing::readFromNbt,
            PatternParameterHollow.TAG, PatternParameterHollow::readFromNbt,
            PatternParameterRotation.TAG, PatternParameterRotation::readFromNbt,
            PatternParameterXZDir.TAG, PatternParameterXZDir::readFromNbt,
            PatternParameterYDir.TAG, PatternParameterYDir::readFromNbt);

    String uniqueTag();

    String translationKey();

    /** The sprite's name under {@code textures/filler/parameters}. */
    String spriteName();

    default ResourceLocation sprite() {
        return BuildCraft.id("textures/filler/parameters/" + spriteName() + ".png");
    }

    /** Every value of this parameter's type, in declaration order. */
    FillerParameter[] allValues();

    int ordinal();

    /** Upstream {@code getPossible}: when ordered, nulls leave gaps in the selection layout. */
    default FillerParameter[] getPossible() {
        return allValues();
    }

    default boolean isPossibleOrdered() {
        return false;
    }

    /** Upstream {@code GuiElementStatementParam}: an unordered list loses its nulls. */
    default List<FillerParameter> possibleForGui() {
        FillerParameter[] possible = getPossible();
        if (isPossibleOrdered()) return Arrays.asList(possible);
        return Arrays.stream(possible).filter(Objects::nonNull).toList();
    }

    @Override
    void writeToNbt(CompoundTag tag);

    @Override
    default String getUniqueTag() {
        return uniqueTag();
    }

    @Override
    default Component getDescription() {
        return Component.translatable(translationKey());
    }

    @Override
    default ResourceLocation getSprite() {
        return sprite();
    }

    @Override
    default ItemStack getItemStack() {
        return ItemStack.EMPTY;
    }

    @Override
    default StatementParameter onClick(StatementContainer source, Statement statement, ItemStack stack, MouseClick mouse) {
        return null;
    }

    @Override
    default StatementParameter rotateLeft() {
        return this;
    }

    @Override
    default StatementParameter[] getPossible(StatementContainer source) {
        return getPossible();
    }

    /** Upstream {@code StatementTypeParam.writeToNbt}. */
    static CompoundTag save(@Nullable FillerParameter parameter) {
        CompoundTag tag = new CompoundTag();
        if (parameter != null) {
            parameter.writeToNbt(tag);
            tag.putString("kind", parameter.uniqueTag());
        }
        return tag;
    }

    @Nullable
    static FillerParameter load(CompoundTag tag) {
        Function<CompoundTag, FillerParameter> reader = READERS.get(tag.getString("kind"));
        return reader == null ? null : reader.apply(tag);
    }
}
