package ports.jurassicraft.structures;

import com.mojang.serialization.Codec;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
import ports.jurassicraft.config.JurassicConfig;

/** Random-spread placement whose grid size follows the original one-in-N rarity. */
public final class PaddockPlacement extends RandomSpreadStructurePlacement {
    public static final Codec<PaddockPlacement> CODEC = Codec.unit(PaddockPlacement::new);
    private static final int SALT = 185379823;

    public PaddockPlacement() { this(JurassicConfig.RAPTOR_PADDOCK_RARITY.get()); }

    PaddockPlacement(int rarity) {
        this(spacingFor(rarity), true);
    }

    private PaddockPlacement(int spacing, boolean ignored) {
        // Keep vanilla random-spread locate support, including negative chunk coordinates.
        super(spacing, (spacing - 1) / 4, RandomSpreadType.LINEAR, SALT);
    }

    static int spacingFor(int rarity) {
        return Math.max(1, (int) Math.round(Math.sqrt(Math.max(1, rarity))));
    }

    @Override public StructurePlacementType<?> type() { return StructureRegistry.PADDOCK_PLACEMENT.get(); }
}
