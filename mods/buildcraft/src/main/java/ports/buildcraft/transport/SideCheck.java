package ports.buildcraft.transport;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;

/**
 * Upstream {@code PipeEventItem.SideCheck} and {@code PipeEventFluid.SideCheck}: which faces a
 * stack or fluid may leave through, grouped by priority (lower values first).
 */
public final class SideCheck {
    public final ItemStack stack;
    /** The travelling item's colour, which lenses give and filters sort by; null for fluids and unpainted items. */
    @Nullable
    public final DyeColor colour;
    @Nullable
    public final Direction from;
    private final int[] priority = new int[6];
    private final EnumSet<Direction> allowed = EnumSet.allOf(Direction.class);

    public SideCheck(ItemStack stack, @Nullable DyeColor colour, @Nullable Direction from) {
        this.stack = stack;
        this.colour = colour;
        this.from = from;
    }

    public boolean isAllowed(Direction side) {
        return allowed.contains(side);
    }

    public void disallow(Direction side) {
        allowed.remove(side);
    }

    public void disallowAll() {
        allowed.clear();
    }

    public void disallowAllExcept(Direction side) {
        boolean had = allowed.contains(side);
        allowed.clear();
        if (had) allowed.add(side);
    }

    public void disallowAllExcept(Collection<Direction> sides) {
        allowed.retainAll(sides);
    }

    public void increasePriority(Direction side) {
        increasePriority(side, 1);
    }

    public void increasePriority(Direction side, int by) {
        priority[side.ordinal()] -= by;
    }

    public void decreasePriority(Direction side) {
        increasePriority(side, -1);
    }

    /** Item routing: every allowed face, grouped into sets of equal priority. */
    public List<EnumSet<Direction>> order() {
        if (allowed.isEmpty()) return List.of();
        if (allowed.size() == 1 || allEqual()) return List.of(EnumSet.copyOf(allowed));
        int[] ordered = Arrays.copyOf(priority, 6);
        Arrays.sort(ordered);
        List<EnumSet<Direction>> list = new ArrayList<>();
        int last = 0;
        for (int i = 0; i < 6; i++) {
            int current = ordered[i];
            if (i != 0 && current == last) continue;
            last = current;
            EnumSet<Direction> set = EnumSet.noneOf(Direction.class);
            for (Direction face : Direction.values()) {
                if (allowed.contains(face) && priority[face.ordinal()] == current) set.add(face);
            }
            if (!set.isEmpty()) list.add(set);
        }
        return list;
    }

    /** Fluid routing: only the highest-priority group of allowed faces. */
    public EnumSet<Direction> firstOrder() {
        List<EnumSet<Direction>> order = order();
        return order.isEmpty() ? EnumSet.noneOf(Direction.class) : order.get(0);
    }

    private boolean allEqual() {
        for (int i = 1; i < priority.length; i++) {
            if (priority[i] != priority[0]) return false;
        }
        return true;
    }
}
