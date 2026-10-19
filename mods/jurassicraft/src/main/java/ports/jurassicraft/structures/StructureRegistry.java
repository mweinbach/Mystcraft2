package ports.jurassicraft.structures;

import ports.jurassicraft.platform.Platform;
import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
import ports.jurassicraft.PortMod;

/** Native structure codecs, pieces and placement. */
public final class StructureRegistry {
    public static final Supplier<StructureType<ParkStructure>> PADDOCK = Platform.register(Registry.STRUCTURE_TYPE_REGISTRY, "raptor_paddock", () -> () -> ParkStructure.PADDOCK_CODEC);
    public static final Supplier<StructureType<ParkStructure>> VISITOR = Platform.register(Registry.STRUCTURE_TYPE_REGISTRY, "visitor_centre", () -> () -> ParkStructure.VISITOR_CODEC);
    public static final Supplier<StructurePieceType> PIECE = Platform.register(Registry.STRUCTURE_PIECE_REGISTRY, "park_structure", () -> ParkStructurePiece::load);
    public static final Supplier<StructurePlacementType<VisitorPlacement>> VISITOR_PLACEMENT = Platform.register(Registry.STRUCTURE_PLACEMENT_TYPE_REGISTRY, "visitor_centre", () -> () -> VisitorPlacement.CODEC);
    public static final Supplier<StructurePlacementType<PaddockPlacement>> PADDOCK_PLACEMENT = Platform.register(Registry.STRUCTURE_PLACEMENT_TYPE_REGISTRY, "raptor_paddock", () -> () -> PaddockPlacement.CODEC);
    private StructureRegistry() {}
    public static void init() { }
}
