package ports.jurassicraft.structures;

import net.minecraft.core.Registry;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import ports.jurassicraft.PortMod;

/** Native structure codecs, pieces and placement. Call register on the mod bus. */
public final class StructureRegistry {
    private static final DeferredRegister<StructureType<?>> TYPES = DeferredRegister.create(Registry.STRUCTURE_TYPE_REGISTRY, PortMod.MOD_ID);
    private static final DeferredRegister<StructurePieceType> PIECES = DeferredRegister.create(Registry.STRUCTURE_PIECE_REGISTRY, PortMod.MOD_ID);
    private static final DeferredRegister<StructurePlacementType<?>> PLACEMENTS = DeferredRegister.create(Registry.STRUCTURE_PLACEMENT_TYPE_REGISTRY, PortMod.MOD_ID);
    private static final DeferredRegister<StructureProcessorType<?>> PROCESSORS = DeferredRegister.create(Registry.STRUCTURE_PROCESSOR_REGISTRY, PortMod.MOD_ID);
    public static final RegistryObject<StructureType<ParkStructure>> PADDOCK = TYPES.register("raptor_paddock", () -> () -> ParkStructure.PADDOCK_CODEC);
    public static final RegistryObject<StructureType<ParkStructure>> VISITOR = TYPES.register("visitor_centre", () -> () -> ParkStructure.VISITOR_CODEC);
    public static final RegistryObject<StructurePieceType> PIECE = PIECES.register("park_structure", () -> ParkStructurePiece::load);
    public static final RegistryObject<StructurePlacementType<VisitorPlacement>> VISITOR_PLACEMENT = PLACEMENTS.register("visitor_centre", () -> () -> VisitorPlacement.CODEC);
    public static final RegistryObject<StructurePlacementType<PaddockPlacement>> PADDOCK_PLACEMENT = PLACEMENTS.register("raptor_paddock", () -> () -> PaddockPlacement.CODEC);
    public static final RegistryObject<StructureProcessorType<HangingProcessor>> HANGING = PROCESSORS.register("structure_hanging", () -> () -> HangingProcessor.CODEC);
    private StructureRegistry() {}
    public static void register(IEventBus bus) { TYPES.register(bus); PIECES.register(bus); PLACEMENTS.register(bus); PROCESSORS.register(bus); }
}
