package ports.buildcraft.energy;

import ports.buildcraft.core.SpringBlock;

/**
 * Upstream {@code EnumSpring.OIL}: the spring under large oil wells, which every 6000 ticks and on random
 * ticks has a one in eight chance to put an oil source above itself when that space is empty.
 */
public class OilSpringBlock extends SpringBlock {
    public static final int TICK_RATE = 6000;
    public static final int CHANCE = 8;

    public OilSpringBlock(Properties properties) {
        super(properties, TICK_RATE, CHANCE, () -> BCEnergy.OIL.heat(0).block().defaultBlockState());
    }
}
