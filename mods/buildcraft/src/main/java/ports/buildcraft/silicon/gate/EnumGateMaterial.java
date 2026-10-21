package ports.buildcraft.silicon.gate;

import java.util.Locale;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Upstream {@code EnumGateMaterial}: the gate's body, which sets its number of trigger and action pairs. */
public enum EnumGateMaterial {
    CLAY_BRICK(Blocks.BRICKS, 1, false),
    IRON(Blocks.IRON_BLOCK, 2, true),
    NETHER_BRICK(Blocks.NETHER_BRICKS, 4, true),
    GOLD(Blocks.GOLD_BLOCK, 8, true);

    public static final EnumGateMaterial[] VALUES = values();

    /** The block whose sound the gate makes when placed. */
    public final Block block;
    public final int numSlots;
    public final boolean canBeModified;
    public final String tag = name().toLowerCase(Locale.ROOT);

    EnumGateMaterial(Block block, int numSlots, boolean canBeModified) {
        this.block = block;
        this.numSlots = numSlots;
        this.canBeModified = canBeModified;
    }

    public static EnumGateMaterial getByOrdinal(int ordinal) {
        return ordinal < 0 || ordinal >= VALUES.length ? CLAY_BRICK : VALUES[ordinal];
    }
}
