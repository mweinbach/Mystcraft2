package ports.gridwright.worker;

import ports.gridwright.registry.ModItems;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Slot layouts of the workers. Every layout ends with a 3x3 output buffer on the right that hoppers
 * and pipes may empty from any face; the slots before it are filled by the player or by pipes.
 */
public enum WorkerLayout {
    /** Biome marker, three upgrades, output. */
    QUANTUM_QUARRY(List.of(new Slot(Role.MARKER, 26, 20)), true),
    /** Three upgrades, output. */
    ENDER_QUARRY(List.of(), true),
    /** The tool it mines with, output. */
    MECHANICAL_MINER(List.of(new Slot(Role.TOOL, 26, 36)), false),
    /** Nine items it uses, then output for whatever the use gives back. */
    MECHANICAL_USER(grid(Role.INPUT, 8), false),
    /** The 3x3 crafting grid (pattern and ingredients at once), then output for results and leftovers. */
    MECHANICAL_CRAFTER(grid(Role.INPUT, 8), false);

    public enum Role { MARKER, UPGRADE, TOOL, INPUT, OUTPUT }

    public record Slot(Role role, int x, int y) { }

    public static final int IMAGE_HEIGHT = 196;
    public static final int INVENTORY_Y = 114;
    public static final int OUTPUT_X = 98;
    public static final int GRID_Y = 18;

    private final List<Slot> slots;
    private final int firstOutput;

    WorkerLayout(List<Slot> leading, boolean upgrades) {
        List<Slot> all = new ArrayList<>(leading);
        if (upgrades) {
            for (int i = 0; i < 3; i++) all.add(new Slot(Role.UPGRADE, 8 + 18 * i, 44));
        }
        firstOutput = all.size();
        all.addAll(grid(Role.OUTPUT, OUTPUT_X));
        slots = List.copyOf(all);
    }

    private static List<Slot> grid(Role role, int left) {
        List<Slot> grid = new ArrayList<>(9);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) grid.add(new Slot(role, left + 18 * column, GRID_Y + 18 * row));
        }
        return grid;
    }

    public int size() {
        return slots.size();
    }

    public Slot slot(int index) {
        return slots.get(index);
    }

    public int firstOutput() {
        return firstOutput;
    }

    public boolean isOutput(int index) {
        return index >= firstOutput && index < slots.size();
    }

    /** The first slot index with {@code role}, or -1. */
    public int first(Role role) {
        for (int i = 0; i < slots.size(); i++) if (slots.get(i).role() == role) return i;
        return -1;
    }

    public int[] allSlots() {
        return IntStream.range(0, slots.size()).toArray();
    }

    /** What a player or pipe may put in {@code index}. */
    public boolean accepts(int index, ItemStack stack) {
        if (index < 0 || index >= slots.size()) return false;
        return switch (slots.get(index).role()) {
            case MARKER -> stack.is(ModItems.BIOME_MARKER.get());
            case UPGRADE -> Upgrades.isUpgrade(stack);
            case TOOL, INPUT -> true;
            case OUTPUT -> false;
        };
    }
}
