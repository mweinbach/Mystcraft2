package ports.jurassicraft.genetics;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;

/** Verifies compatibility with the original randomization and GeneticAttributes NBT. */
public final class DinosaurAttributesLogicTest {
    private DinosaurAttributesLogicTest() {}

    public static void main(String[] args) { run(); }

    public static void run() {
        // These hexadecimal float values were produced by the original 1.12 algorithm
        // with java.util.Random seeds; the 1.19 LegacyRandomSource uses the same RNG.
        var parent1 = new DinosaurAttributes(.85F, .9F, .95F, .86F);
        var parent2 = new DinosaurAttributes(1.15F, 1.1F, 1.05F, 1.14F);
        check(DinosaurAttributes.random(RandomSource.create(1)).equals(new DinosaurAttributes(
                0x1.17a456p0F, 0x1.fcc76ap-1F, 0x1.d20fp-1F, 0x1.cf7918p-1F)), "upstream creation seed 1");
        check(DinosaurAttributes.random(RandomSource.create(12345)).equals(new DinosaurAttributes(
                0x1.e3b282p-1F, 0x1.26cdeep0F, 0x1.1cc35p0F, 0x1.e5680ep-1F)), "upstream creation seed 12345");
        check(DinosaurAttributes.combine(RandomSource.create(2), parent1, parent2).equals(new DinosaurAttributes(
                0x1.11c088p0F, 0x1.38d23cp0F, 0x1.092472p0F, 0x1.9f515ep-1F)), "upstream inheritance seed 2");
        check(DinosaurAttributes.combine(RandomSource.create(3), parent1, parent2).equals(new DinosaurAttributes(
                0x1.1f41f6p0F, 0x1.043108p0F, 0x1.f577e6p-1F, 0x1.c1da3ep-1F)), "upstream inheritance seed 3");

        CompoundTag legacy = new CompoundTag();
        legacy.putFloat("Scale", 1.1F); legacy.putFloat("Damage", .9F);
        legacy.putFloat("Health", 1.05F); legacy.putFloat("Speed", .8F);
        legacy.putString("OtherModData", "kept");
        var saved = DinosaurAttributes.read(legacy).orElseThrow();
        CompoundTag roundtrip = legacy.copy();
        saved.write(roundtrip);
        check(roundtrip.equals(legacy), "legacy tag roundtrip and unrelated fields");
        roundtrip.remove("Speed");
        check(DinosaurAttributes.read(roundtrip).isEmpty(), "incomplete attributes rejected");
        roundtrip.putString("Speed", "fast");
        check(DinosaurAttributes.read(roundtrip).isEmpty(), "non-numeric attributes rejected");
        roundtrip.putFloat("Speed", Float.NaN);
        check(DinosaurAttributes.read(roundtrip).isEmpty(), "NaN rejected");
        roundtrip.putFloat("Speed", Float.POSITIVE_INFINITY);
        check(DinosaurAttributes.read(roundtrip).isEmpty(), "infinity rejected");
        roundtrip.putFloat("Speed", 0);
        check(DinosaurAttributes.read(roundtrip).isEmpty(), "non-positive attributes rejected");
        check(DinosaurAttributes.read(null).isEmpty(), "absent attributes rejected");

        // Equal parents make an interpolated trait exactly 2; a mutation is easily
        // distinguished from inheritance. Verify that all four traits can mutate.
        var identical = new DinosaurAttributes(2, 2, 2, 2);
        int[] mutations = new int[4];
        RandomSource random = RandomSource.create(44501);
        for (int i = 0; i < 3000; i++) {
            var child = DinosaurAttributes.combine(random, identical, identical);
            if (child.scale() != 2) mutations[0]++;
            if (child.damage() != 2) mutations[1]++;
            if (child.health() != 2) mutations[2]++;
            if (child.speed() != 2) mutations[3]++;
        }
        for (int count : mutations) check(count > 800 && count < 1200, "each trait has an independent one-in-three mutation");
        System.out.println("Dinosaur attribute checks passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
