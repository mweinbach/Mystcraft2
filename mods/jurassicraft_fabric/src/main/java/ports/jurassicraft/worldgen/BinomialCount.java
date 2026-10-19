package ports.jurassicraft.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.IntProviderType;

/** Number of successful independent placement attempts, for ore count placement. */
public final class BinomialCount extends IntProvider {
    public static final Codec<BinomialCount> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(0, 256).fieldOf("trials").forGetter(value -> value.trials),
            Codec.floatRange(0.0F, 1.0F).fieldOf("probability").forGetter(value -> value.probability)
    ).apply(instance, BinomialCount::new));
    public static final IntProviderType<BinomialCount> TYPE = () -> CODEC;

    private final int trials;
    private final float probability;

    public BinomialCount(int trials, float probability) {
        this.trials = trials;
        this.probability = probability;
    }

    @Override public int sample(RandomSource random) {
        int successes = 0;
        for (int i = 0; i < trials; i++) if (random.nextFloat() < probability) successes++;
        return successes;
    }

    @Override public int getMinValue() { return probability == 1.0F ? trials : 0; }
    @Override public int getMaxValue() { return probability == 0.0F ? 0 : trials; }
    @Override public IntProviderType<?> getType() { return TYPE; }
}
