package ports.jurassicraft.structures;

import java.util.function.Supplier;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
import ports.jurassicraft.PortMod;

/** Native structure codecs, pieces, placements and commands. */
public final class StructureRegistry {
    public static final Supplier<StructureType<ParkStructure>> PADDOCK = hold(Registry.register(Registry.STRUCTURE_TYPES,id("raptor_paddock"),() -> ParkStructure.PADDOCK_CODEC));
    public static final Supplier<StructureType<ParkStructure>> VISITOR = hold(Registry.register(Registry.STRUCTURE_TYPES,id("visitor_centre"),() -> ParkStructure.VISITOR_CODEC));
    public static final Supplier<StructurePieceType> PIECE = hold(Registry.register(Registry.STRUCTURE_PIECE,id("park_structure"),ParkStructurePiece::load));
    public static final Supplier<StructurePlacementType<VisitorPlacement>> VISITOR_PLACEMENT = hold(Registry.register(Registry.STRUCTURE_PLACEMENT_TYPE,id("visitor_centre"),() -> VisitorPlacement.CODEC));
    public static final Supplier<StructurePlacementType<PaddockPlacement>> PADDOCK_PLACEMENT = hold(Registry.register(Registry.STRUCTURE_PLACEMENT_TYPE,id("raptor_paddock"),() -> PaddockPlacement.CODEC));
    private StructureRegistry() {}
    private static ResourceLocation id(String path) { return new ResourceLocation(PortMod.MOD_ID,path); }
    private static <T> Supplier<T> hold(T value) { return () -> value; }
    public static void init() { CommandRegistrationCallback.EVENT.register((dispatcher,access,environment) -> { SpawnParkCommand.register(dispatcher); VisitorLocate.install(dispatcher.getRoot()); }); }
}
