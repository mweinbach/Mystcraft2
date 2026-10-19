package ports.jurassicraft.structures;

import com.mojang.serialization.Codec;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.IntBinaryOperator;
import java.util.function.IntPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.config.JurassicConfig;

/** A single template piece placed by vanilla's chunk-clipped structure system. */
public final class ParkStructure extends Structure {
    public static final Codec<ParkStructure> PADDOCK_CODEC = simpleCodec(settings -> new ParkStructure(settings, false));
    public static final Codec<ParkStructure> VISITOR_CODEC = simpleCodec(settings -> new ParkStructure(settings, true));
    private final boolean visitor;
    public ParkStructure(StructureSettings settings, boolean visitor) { super(settings); this.visitor = visitor; }
    @Override public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (!(visitor ? JurassicConfig.GENERATE_VISITOR_CENTRE.get() : JurassicConfig.GENERATE_RAPTOR_PADDOCK.get())) return Optional.empty();
        ResourceLocation templateId = new ResourceLocation(PortMod.MOD_ID, visitor ? "visitor_centre" : "raptor_paddock");
        Rotation rotation = Rotation.getRandom(context.random());
        Mirror mirror = Mirror.values()[context.random().nextInt(Mirror.values().length)];
        int x = context.chunkPos().getMiddleBlockX(), z = context.chunkPos().getMiddleBlockZ();
        var template = context.structureTemplateManager().getOrCreate(templateId);
        if (template.getSize().getX() == 0) return Optional.empty();
        BlockPos origin = new BlockPos(x, 0, z);
        int y;
        if (visitor) {
            // The original aligns the entrance's local (38,3,1) to the ground.
            BlockPos entrance = net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.transform(
                    new BlockPos(38, 3, 1), mirror, rotation, BlockPos.ZERO);
            y = context.chunkGenerator().getBaseHeight(x + entrance.getX(), z + entrance.getZ(), Heightmap.Types.OCEAN_FLOOR_WG,
                    context.heightAccessor(), context.randomState()) - entrance.getY();
        } else {
            BoundingBox box = template.getBoundingBox(new StructurePlaceSettings().setRotation(rotation).setMirror(mirror), origin);
            var height = paddockHeight(box,
                    (bx, bz) -> context.chunkGenerator().getBaseHeight(bx, bz, Heightmap.Types.OCEAN_FLOOR_WG,
                            context.heightAccessor(), context.randomState()),
                    quartY -> context.validBiome().test(context.chunkGenerator().getBiomeSource().getNoiseBiome(
                            QuartPos.fromBlock(x), quartY, QuartPos.fromBlock(z), context.randomState().sampler())));
            if (height.isEmpty()) return Optional.empty();
            y = height.getAsInt();
        }
        BlockPos placed = new BlockPos(x, y, z);
        return Optional.of(new GenerationStub(placed, builder -> builder.addPiece(
                new ParkStructurePiece(context.structureTemplateManager(), templateId, placed, rotation, mirror))));
    }

    /** Reject impossible biomes before sampling the whole original perimeter. */
    static OptionalInt paddockHeight(BoundingBox box, IntBinaryOperator surface, IntPredicate validBiomeAtQuartY) {
        int first = surface.applyAsInt(box.minX(), box.minZ());
        // For terrain whose range is at most 16, the final floor min-1 is
        // necessarily between first-17 and first-1. Check every quart in that
        // range, since biome sources may vary vertically or come from other mods.
        if (first >= Integer.MIN_VALUE + 17) {
            boolean possible = false;
            for (int q = QuartPos.fromBlock(first - 17), last = QuartPos.fromBlock(first - 1); q <= last; q++) {
                if (validBiomeAtQuartY.test(q)) { possible = true; break; }
            }
            if (!possible) return OptionalInt.empty();
        }
        int min = first, max = first;
        for (int bx = box.minX(); bx <= box.maxX(); bx++) for (int bz = box.minZ(); bz <= box.maxZ(); bz++) {
            if (bx != box.minX() && bx != box.maxX() && bz != box.minZ() && bz != box.maxZ()) continue;
            if (bx == box.minX() && bz == box.minZ()) continue;
            int height = surface.applyAsInt(bx, bz);
            min = Math.min(min, height); max = Math.max(max, height);
        }
        if (max - min > 16) return OptionalInt.empty();
        return OptionalInt.of(min - 1);
    }
    @Override public StructureType<?> type() { return visitor ? StructureRegistry.VISITOR.get() : StructureRegistry.PADDOCK.get(); }
}
