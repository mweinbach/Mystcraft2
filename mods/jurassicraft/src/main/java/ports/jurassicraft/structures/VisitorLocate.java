package ports.jurassicraft.structures;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.datafixers.util.Pair;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceOrTagLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.commands.LocateCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.Structure;
import ports.jurassicraft.PortMod;

/** Makes vanilla's structure command aware of the single-candidate visitor placement. */
public final class VisitorLocate {
    static final ResourceKey<Structure> VISITOR = ResourceKey.create(Registry.STRUCTURE_REGISTRY,
            new ResourceLocation(PortMod.MOD_ID, "visitor_centre"));
    interface Search {
        Optional<BlockPos> visitor(ServerLevel level, Structure structure);
        Pair<BlockPos, Holder<Structure>> vanilla(ServerLevel level, HolderSet<Structure> structures, BlockPos origin);
    }
    private static final Search WORLD_SEARCH = new Search() {
        @Override public Optional<BlockPos> visitor(ServerLevel level, Structure structure) {
            return VisitorPlacement.candidateForLocate(level).flatMap(candidate -> actualStart(level, structure, candidate));
        }
        @Override public Pair<BlockPos, Holder<Structure>> vanilla(ServerLevel level, HolderSet<Structure> structures, BlockPos origin) {
            return level.getChunkSource().getGenerator().findNearestMapStructure(level, structures, origin, 100, false);
        }
    };
    private VisitorLocate() {}

    public static void install(CommandNode<CommandSourceStack> root) {
        install(root, WORLD_SEARCH);
    }

    // A dispatcher-local search enables deterministic command tests without
    // mutating world generation, config or global state.
    static void install(CommandNode<CommandSourceStack> root, Search search) {
        CommandNode<CommandSourceStack> locate = root.getChild("locate");
        CommandNode<CommandSourceStack> parent = locate == null ? null : locate.getChild("structure");
        CommandNode<CommandSourceStack> argument = parent == null ? null : parent.getChild("structure");
        if (!(argument instanceof ArgumentCommandNode<?, ?> typed)
                || !(typed.getType() instanceof ResourceOrTagLocationArgument<?>) || argument.getCommand() == null) return;
        // Brigadier merges an identically named child, replacing only its executor.
        // Keep the original parser, suggestions, requirements and all other commands.
        Command<CommandSourceStack> original = argument.getCommand();
        parent.addChild(Commands.argument("structure", ResourceOrTagLocationArgument.resourceOrTag(Registry.STRUCTURE_REGISTRY))
                .executes(context -> locate(context, original, search)).build());
    }

    private static int locate(CommandContext<CommandSourceStack> context, Command<CommandSourceStack> original, Search search)
            throws CommandSyntaxException {
        @SuppressWarnings("unchecked")
        ResourceOrTagLocationArgument.Result<Structure> requested = context.getArgument("structure", ResourceOrTagLocationArgument.Result.class);
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registry.STRUCTURE_REGISTRY);
        Optional<Holder<Structure>> visitor = registry.getHolder(VISITOR);
        if (visitor.isEmpty() || !requested.test(visitor.get())) return original.run(context);

        Optional<BlockPos> position = search.visitor(level, visitor.get().value());
        if (position.isEmpty()) return original.run(context);

        Optional<HolderSet<Structure>> structures = requested.unwrap().map(
                key -> registry.getHolder(key).map(holder -> (HolderSet<Structure>) HolderSet.direct(holder)),
                tag -> registry.getTag(tag).map(set -> (HolderSet<Structure>) set));
        if (structures.isEmpty()) return original.run(context);
        BlockPos origin = new BlockPos(source.getPosition());
        Pair<BlockPos, Holder<Structure>> vanilla = search.vanilla(level, structures.get(), origin);
        Pair<BlockPos, Holder<Structure>> ours = Pair.of(position.get().atY(0), visitor.get());
        Pair<BlockPos, Holder<Structure>> result = nearest(origin, ours, vanilla);
        return LocateCommand.showLocateResult(source, requested, origin, result, "commands.locate.structure.success", false);
    }

    /** Read or generate structure starts, but never report a rejected candidate. */
    static Optional<BlockPos> actualStart(ServerLevel level, Structure structure, ChunkPos candidate) {
        var chunk = level.getChunkSource().getChunk(candidate.x, candidate.z, ChunkStatus.STRUCTURE_STARTS, true);
        if (chunk == null) return Optional.empty();
        var start = chunk.getStartForStructure(structure);
        return start != null && start.isValid() ? Optional.of(start.getBoundingBox().getCenter()) : Optional.empty();
    }

    static Pair<BlockPos, Holder<Structure>> nearest(BlockPos origin, Pair<BlockPos, Holder<Structure>> candidate,
            Pair<BlockPos, Holder<Structure>> vanilla) {
        return vanilla != null && origin.distSqr(vanilla.getFirst()) <= origin.distSqr(candidate.getFirst()) ? vanilla : candidate;
    }
}
