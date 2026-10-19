package ports.jurassicraft.structures;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Arrays;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.material.Material;
import ports.jurassicraft.PortMod;

/** Original operator command: /spawnjc <structure> [rotation] [mirror] [seed]. */
public final class SpawnParkCommand {
    private static final DynamicCommandExceptionType UNKNOWN =
            new DynamicCommandExceptionType(value -> Component.literal("Unknown JurassiCraft structure: " + value));
    private static final DynamicCommandExceptionType ROTATION =
            new DynamicCommandExceptionType(value -> Component.literal("Unknown rotation: " + value));
    private static final DynamicCommandExceptionType MIRROR =
            new DynamicCommandExceptionType(value -> Component.literal("Unknown mirror: " + value));
    private static final DynamicCommandExceptionType MISSING =
            new DynamicCommandExceptionType(value -> Component.literal("Missing JurassiCraft structure template: " + value));
    private static final SimpleCommandExceptionType TOO_STEEP =
            new SimpleCommandExceptionType(Component.literal("The terrain is too steep for the raptor paddock"));
    private static final SimpleCommandExceptionType OUT_OF_WORLD =
            new SimpleCommandExceptionType(Component.literal("Structure would be outside the world bounds"));
    private static final String[] NAMES = {"visitor_center", "raptor_paddock"};
    private static final String[] ROTATIONS = Arrays.stream(Rotation.values()).map(value -> value.name().toLowerCase(Locale.ROOT)).toArray(String[]::new);
    private static final String[] MIRRORS = Arrays.stream(Mirror.values()).map(value -> value.name().toLowerCase(Locale.ROOT)).toArray(String[]::new);

    private SpawnParkCommand() {}


    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("spawnjc").requires(source -> source.hasPermission(2))
                .then(Commands.argument("structure", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(NAMES, builder))
                        .executes(context -> execute(context, false, false, false))
                        .then(Commands.argument("rotation", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(ROTATIONS, builder))
                                .executes(context -> execute(context, true, false, false))
                                .then(Commands.argument("mirror", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(MIRRORS, builder))
                                        .executes(context -> execute(context, true, true, false))
                                        .then(Commands.argument("seed", LongArgumentType.longArg())
                                                .executes(context -> execute(context, true, true, true)))))));
    }

    private static int execute(CommandContext<CommandSourceStack> context, boolean hasRotation, boolean hasMirror,
            boolean hasSeed) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "structure").toLowerCase(Locale.ROOT);
        boolean visitor;
        if (name.equals("visitor_center")) visitor = true;
        else if (name.equals("raptor_paddock")) visitor = false;
        else throw UNKNOWN.create(name);
        RandomSource random = hasSeed ? RandomSource.create(LongArgumentType.getLong(context, "seed")) : RandomSource.create();
        Rotation rotation = hasRotation ? rotation(StringArgumentType.getString(context, "rotation")) : Rotation.getRandom(random);
        Mirror mirror = hasMirror ? mirror(StringArgumentType.getString(context, "mirror"))
                : Mirror.values()[random.nextInt(Mirror.values().length)];
        CommandSourceStack source = context.getSource();
        BlockPos placed = place(source.getLevel(), new BlockPos(source.getPosition()), visitor, rotation, mirror, random);
        source.sendSuccess(Component.literal("Placed " + name + " at " + placed.getX() + " " + placed.getY() + " " + placed.getZ()), true);
        return 1;
    }

    private static Rotation rotation(String value) throws CommandSyntaxException {
        try { return Rotation.valueOf(value.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException invalid) { throw ROTATION.create(value); }
    }

    private static Mirror mirror(String value) throws CommandSyntaxException {
        try { return Mirror.valueOf(value.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException invalid) { throw MIRROR.create(value); }
    }

    static BlockPos place(ServerLevel level, BlockPos requested, boolean visitor, Rotation rotation, Mirror mirror,
            RandomSource random) throws CommandSyntaxException {
        ResourceLocation id = new ResourceLocation(PortMod.MOD_ID, visitor ? "visitor_centre" : "raptor_paddock");
        StructureTemplate template = level.getStructureManager().get(id).orElseThrow(() -> MISSING.create(id));
        if (template.getSize().getX() <= 0 || template.getSize().getY() <= 0 || template.getSize().getZ() <= 0)
            throw MISSING.create(id);
        if (!level.isInWorldBounds(requested)) throw OUT_OF_WORLD.create();
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation).setMirror(mirror);
        BlockPos origin;
        if (visitor) {
            // The upstream command aligns the local entrance with the sender's ground position.
            BlockPos entrance = StructureTemplate.transform(new BlockPos(38, 3, 1), mirror, rotation, BlockPos.ZERO);
            origin = new BlockPos(requested.getX(), groundHeight(level, requested.getX(), requested.getZ()), requested.getZ())
                    .subtract(entrance);
        } else {
            BoundingBox footprint = template.getBoundingBox(settings, requested);
            if (!validHorizontalBounds(level, footprint, requested.getY())) throw OUT_OF_WORLD.create();
            int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
            for (int x = footprint.minX(); x <= footprint.maxX(); x++) for (int z = footprint.minZ(); z <= footprint.maxZ(); z++) {
                if (x != footprint.minX() && x != footprint.maxX() && z != footprint.minZ() && z != footprint.maxZ()) continue;
                int height = groundHeight(level, x, z);
                min = Math.min(min, height);
                max = Math.max(max, height);
            }
            if ((max + min) / 2 - min > 8) throw TOO_STEEP.create();
            origin = new BlockPos(requested.getX(), min - 1, requested.getZ());
        }
        BoundingBox footprint = template.getBoundingBox(settings, origin);
        if (!validHorizontalBounds(level, footprint, origin.getY()) || footprint.minY() < level.getMinBuildHeight()
                || footprint.maxY() >= level.getMaxBuildHeight()) throw OUT_OF_WORLD.create();
        // A single full-footprint placement avoids spawning a hanging entity once per intersecting chunk.
        BoundingBox clip = new BoundingBox(footprint.minX(), level.getMinBuildHeight(), footprint.minZ(),
                footprint.maxX(), level.getMaxBuildHeight() - 1, footprint.maxZ());
        ParkStructurePiece piece = new ParkStructurePiece(level.getStructureManager(), id, origin, rotation, mirror);
        piece.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), random, clip,
                new ChunkPos(origin), origin);
        return origin;
    }

    private static boolean validHorizontalBounds(ServerLevel level, BoundingBox box, int y) {
        return level.isInWorldBounds(new BlockPos(box.minX(), y, box.minZ()))
                && level.isInWorldBounds(new BlockPos(box.maxX(), y, box.maxZ()));
    }

    private static int groundHeight(ServerLevel level, int x, int z) {
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = top - 1; y >= level.getMinBuildHeight(); y--) {
            Material material = level.getBlockState(cursor.set(x, y, z)).getMaterial();
            if (material == Material.DIRT || material == Material.GRASS || material == Material.SAND
                    || material == Material.STONE || material.isLiquid()) return y + 1;
        }
        return level.getMinBuildHeight();
    }
}
