package ports.jurassicraft.village;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;

/**
 * Upstream village piece weights are 0 or 1 for the geneticist house and for the
 * digsite as a whole. Jigsaw pools have no counter, so assembly records placements
 * against the structure random that is generating the village.
 */
public final class VillageLimits {
    public enum Category { GENETICIST, DIGSITE }

    private static final ThreadLocal<RandomSource> ASSEMBLY = new ThreadLocal<>();
    private static final Map<RandomSource, Caps> CAPS = Collections.synchronizedMap(new WeakHashMap<>());

    private VillageLimits() {}

    public static void begin(RandomSource random) { ASSEMBLY.set(random); }

    public static void end() { ASSEMBLY.remove(); }

    /** Selection probes ask for jigsaws; an already placed piece expanding its streets does not. */
    public static boolean permit(Category category) {
        RandomSource random = ASSEMBLY.get();
        if (random == null) return true;
        Caps caps = caps(random);
        return caps.placed(category) < caps.limit(category);
    }

    public static void record(StructurePoolElement element) {
        if (!(element instanceof LimitedVillageElement limited)) return;
        RandomSource random = ASSEMBLY.get();
        if (random == null) return;
        caps(random).place(limited.category());
    }

    private static Caps caps(RandomSource random) {
        return CAPS.computeIfAbsent(random, ignored -> new Caps(random.nextInt(2), random.nextInt(2)));
    }

    private static final class Caps {
        private final int geneticist;
        private final int digsite;
        private int placedGeneticist;
        private int placedDigsite;

        private Caps(int geneticist, int digsite) {
            this.geneticist = geneticist;
            this.digsite = digsite;
        }

        private int limit(Category category) {
            return category == Category.GENETICIST ? geneticist : digsite;
        }

        private int placed(Category category) {
            return category == Category.GENETICIST ? placedGeneticist : placedDigsite;
        }

        private void place(Category category) {
            if (category == Category.GENETICIST) placedGeneticist++;
            else placedDigsite++;
        }
    }
}
