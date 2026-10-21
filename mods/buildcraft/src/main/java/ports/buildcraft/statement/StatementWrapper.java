package ports.buildcraft.statement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Upstream {@code StatementWrapper}: a statement as placed in a gate, with the part of the pipe it
 * reads or acts on. Upstream's {@code EnumPipePart} is a nullable face, null being the centre.
 */
public abstract class StatementWrapper implements Statement, Comparable<StatementWrapper> {
    public static final int CENTER_INDEX = 6;
    /** Upstream {@code EnumPipePart.next()}. */
    private static final Direction[] NEXT = {Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.UP, Direction.DOWN, Direction.NORTH};

    public final Statement delegate;
    @Nullable
    public final Direction sourcePart;

    protected StatementWrapper(Statement delegate, @Nullable Direction sourcePart) {
        this.delegate = delegate;
        this.sourcePart = sourcePart;
    }

    /** Upstream {@code EnumPipePart.getIndex()}: the face's ordinal, or 6 for the centre. */
    public static int partIndex(@Nullable Direction part) {
        return part == null ? CENTER_INDEX : part.ordinal();
    }

    /** Upstream {@code EnumPipePart.fromMeta}: anything outside the six faces is the centre. */
    @Nullable
    public static Direction partFromIndex(int index) {
        return index >= 0 && index < 6 ? Direction.from3DDataValue(index) : null;
    }

    static Direction nextPart(@Nullable Direction part) {
        return part == null ? Direction.DOWN : NEXT[part.ordinal()];
    }

    @Override
    public String getUniqueTag() {
        return delegate.getUniqueTag();
    }

    @Override
    public int maxParameters() {
        return delegate.maxParameters();
    }

    @Override
    public int minParameters() {
        return delegate.minParameters();
    }

    @Nullable
    @Override
    public Component getDescription() {
        return delegate.getDescription();
    }

    @Nullable
    @Override
    public StatementParameter createParameter(int index) {
        return delegate.createParameter(index);
    }

    @Override
    public Statement rotateLeft() {
        return delegate.rotateLeft();
    }

    @Nullable
    @Override
    public ResourceLocation getSprite() {
        return delegate.getSprite();
    }

    @Nullable
    public BlockEntity getNeighbourTile(StatementContainer source) {
        return sourcePart == null ? null : source.getNeighbourTile(sourcePart);
    }

    @Override
    public abstract StatementWrapper[] getPossible();

    @Override
    public boolean isPossibleOrdered() {
        return delegate.isPossibleOrdered();
    }

    @Override
    public List<Component> getTooltip() {
        List<Component> list = delegate.getTooltip();
        if (sourcePart != null) {
            list = new ArrayList<>(list);
            list.add(Component.translatable("gate.side", SideText.of(sourcePart)));
        }
        return list;
    }

    @Nullable
    @Override
    public <T> T convertTo(Class<T> type) {
        T converted = delegate.convertTo(type);
        if (converted != null) return converted;
        if (type.isAssignableFrom(TriggerWrapper.class)) {
            Trigger trigger = delegate.convertTo(Trigger.class);
            if (trigger != null) return type.cast(TriggerWrapper.wrap(trigger, sourcePart));
        } else if (type.isAssignableFrom(ActionWrapper.class)) {
            Action action = delegate.convertTo(Action.class);
            if (action != null) return type.cast(ActionWrapper.wrap(action, sourcePart));
        }
        return null;
    }

    /** Upstream's order: the centre first, then faces from east to down; then by place in the ring; then by tag. */
    @Override
    public int compareTo(StatementWrapper other) {
        if (sourcePart != other.sourcePart) return Integer.compare(partIndex(other.sourcePart), partIndex(sourcePart));
        if (delegate == other.delegate) return 0;
        if (delegate.getClass() == other.delegate.getClass()) {
            Statement[] possible = delegate.getPossible();
            if (Arrays.equals(possible, other.delegate.getPossible())) {
                int indexThis = -1, indexThat = -1;
                for (int i = 0; i < possible.length; i++) {
                    if (possible[i] == delegate) indexThis = i;
                    if (possible[i] == other.delegate) indexThat = i;
                }
                if (indexThis != indexThat && indexThis != -1 && indexThat != -1) return Integer.compare(indexThis, indexThat);
            }
        }
        return getUniqueTag().compareTo(other.getUniqueTag());
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourcePart, getUniqueTag());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != getClass()) return false;
        StatementWrapper other = (StatementWrapper) obj;
        return sourcePart == other.sourcePart && getUniqueTag().equals(other.getUniqueTag());
    }
}
