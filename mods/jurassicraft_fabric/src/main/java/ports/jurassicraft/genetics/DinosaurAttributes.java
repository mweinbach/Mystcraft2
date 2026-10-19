package ports.jurassicraft.genetics;

import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.RandomSource;

/**
 * Heritable dinosaur traits, separate from the ten-letter genome. The NBT keys and
 * randomization match the original DinosaurAttributes stored in GeneticAttributes.
 */
public record DinosaurAttributes(float scale, float damage, float health, float speed) {
    private static final float MINIMUM = 0.8F;
    private static final float MAXIMUM = 1.2F;

    public DinosaurAttributes {
        if (!positiveFinite(scale) || !positiveFinite(damage) || !positiveFinite(health) || !positiveFinite(speed))
            throw new IllegalArgumentException("Dinosaur attributes must be finite and positive");
    }

    /** Generate an individual using the upstream scale/stat correlation. */
    public static DinosaurAttributes random(RandomSource random) {
        float scale = between(random, MINIMUM, MAXIMUM);
        float scaleStat = (2.0F + scale) / 3.0F;
        float damage = between(random, MINIMUM, MAXIMUM) * scaleStat;
        float health = between(random, MINIMUM, MAXIMUM) * scaleStat;
        float speed = between(random, MINIMUM, MAXIMUM) / scaleStat;
        return new DinosaurAttributes(scale, damage, health, speed);
    }

    /** Interpolate each parent's trait, with an independent one-in-three mutation. */
    public static DinosaurAttributes combine(RandomSource random, DinosaurAttributes first, DinosaurAttributes second) {
        float scale = between(random, first.scale, second.scale);
        if (random.nextInt(3) == 0) scale = between(random, MINIMUM, MAXIMUM);
        float scaleStat = (2.0F + scale) / 3.0F;
        float damage = between(random, first.damage, second.damage);
        if (random.nextInt(3) == 0) damage = between(random, MINIMUM, MAXIMUM) * scaleStat;
        float health = between(random, first.health, second.health);
        if (random.nextInt(3) == 0) health = between(random, MINIMUM, MAXIMUM) * scaleStat;
        float speed = between(random, first.speed, second.speed);
        if (random.nextInt(3) == 0) speed = between(random, MINIMUM, MAXIMUM) / scaleStat;
        return new DinosaurAttributes(scale, damage, health, speed);
    }

    /** Write into the original GeneticAttributes subcompound without deleting other fields. */
    public void write(CompoundTag tag) {
        tag.putFloat("Scale", scale);
        tag.putFloat("Damage", damage);
        tag.putFloat("Health", health);
        tag.putFloat("Speed", speed);
    }

    /** Reject incomplete or unsafe data so callers can retain their default attributes. */
    public static Optional<DinosaurAttributes> read(CompoundTag tag) {
        if (tag == null || !tag.contains("Scale", Tag.TAG_ANY_NUMERIC) || !tag.contains("Damage", Tag.TAG_ANY_NUMERIC)
                || !tag.contains("Health", Tag.TAG_ANY_NUMERIC) || !tag.contains("Speed", Tag.TAG_ANY_NUMERIC)) return Optional.empty();
        float scale = tag.getFloat("Scale"), damage = tag.getFloat("Damage"), health = tag.getFloat("Health"), speed = tag.getFloat("Speed");
        if (!positiveFinite(scale) || !positiveFinite(damage) || !positiveFinite(health) || !positiveFinite(speed)) return Optional.empty();
        return Optional.of(new DinosaurAttributes(scale, damage, health, speed));
    }

    private static boolean positiveFinite(float value) { return Float.isFinite(value) && value > 0.0F; }
    private static float between(RandomSource random, float a, float b) {
        float min = Math.min(a, b), max = Math.max(a, b);
        return (float) (random.nextDouble() * (max - min) + min);
    }
}
