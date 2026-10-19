package ports.jurassicraft.compat.emi;

import java.util.List;
import ports.jurassicraft.compat.emi.RecipePreviews.Kind;
import ports.jurassicraft.hatching.HatcheryRegistry;
import ports.jurassicraft.machine.DnaMachineKind;

/** Machine menu slot and screen geometry shared by the optional EMI handlers and tests. */
final class EmiRouting {
    record Area(int x, int y, int width, int height) {
        boolean contains(double px, double py) { return px >= x && py >= y && px < x + width && py < y + height; }
    }
    record Plan(int inventoryStart, List<Integer> targetSlots) {}
    private EmiRouting() {}

    static Kind dna(DnaMachineKind machine, boolean hybridizer) {
        if (hybridizer) return null;
        return switch (machine) {
            case EXTRACTOR -> Kind.EXTRACTING;
            case SEQUENCER -> Kind.SEQUENCING;
            case COMBINATOR -> Kind.COMBINING;
            case SYNTHESIZER -> Kind.SYNTHESIZING;
        };
    }
    static Kind hatchery(HatcheryRegistry.Machine machine) {
        return switch (machine) {
            case EMBRYONIC -> Kind.EMBRYONIC;
            case CALCIFIER -> Kind.CALCIFYING;
            case INCUBATOR -> Kind.INCUBATING;
        };
    }
    static Plan plan(Kind kind) {
        return switch (kind) {
            case CLEANING -> new Plan(8, List.of(0));
            case GRINDING -> new Plan(12, List.of(0));
            case EXTRACTING -> new Plan(DnaMachineKind.EXTRACTOR.slots, List.of(0, 1));
            case SEQUENCING -> new Plan(DnaMachineKind.SEQUENCER.slots, List.of(0, 1));
            case COMBINING -> new Plan(DnaMachineKind.COMBINATOR.slots, List.of(0, 1));
            case SYNTHESIZING -> new Plan(DnaMachineKind.SYNTHESIZER.slots, List.of(0, 1, 2));
            case EMBRYONIC -> new Plan(HatcheryRegistry.Machine.EMBRYONIC.slots, List.of(0, 1, 2));
            case CALCIFYING -> new Plan(HatcheryRegistry.Machine.CALCIFIER.slots, List.of(0, 1));
            case INCUBATING -> new Plan(HatcheryRegistry.Machine.INCUBATOR.slots, List.of(0, 5));
            case CULTIVATING -> new Plan(4, List.of(0));
            case SKELETON -> new Plan(26, java.util.stream.IntStream.rangeClosed(1, 25).boxed().toList());
            case BREEDING -> new Plan(9, List.of(0, 3));
            case PEAT -> null;
        };
    }
    static List<Area> areas(Kind kind) {
        if (kind == null) return List.of();
        return switch (kind) {
            case CLEANING, SYNTHESIZING, EMBRYONIC -> List.of(new Area(79, 34, 24, 16));
            case GRINDING -> List.of(new Area(80, 32, 24, 17));
            case EXTRACTING -> List.of(new Area(77, 36, 24, 16));
            case SEQUENCING -> List.of(new Area(87, 21, 22, 6), new Area(87, 41, 22, 6), new Area(87, 61, 22, 6));
            case COMBINING -> List.of(new Area(93, 30, 8, 24));
            case CALCIFYING -> List.of(new Area(67, 31, 24, 16));
            // This area avoids all egg slots, temperature sliders and the peat slot.
            case INCUBATING -> List.of(new Area(5, 49, 16, 16));
            case CULTIVATING -> List.of(new Area(95, 49, 22, 16));
            case SKELETON -> List.of(new Area(110, 49, 22, 18));
            case BREEDING -> List.of(new Area(88, 35, 24, 17));
            case PEAT -> List.of();
        };
    }
    static boolean hit(Kind kind, double x, double y) {
        for (Area area : areas(kind)) if (area.contains(x, y)) return true;
        return false;
    }
}
