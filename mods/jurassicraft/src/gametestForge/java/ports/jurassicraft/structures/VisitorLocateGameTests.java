package ports.jurassicraft.structures;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Pair;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceOrTagLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.server.level.ServerLevel;
import java.util.Optional;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import ports.jurassicraft.PortMod;

@GameTestHolder(PortMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VisitorLocateGameTests {
    private VisitorLocateGameTests() {}
    private static void require(GameTestHelper helper, boolean value, String message) { if (!value) helper.fail(message); }

    @GameTest(template = "cleaning_station_test", templateNamespace = PortMod.MOD_ID)
    public static void unrelatedLocateRequestsAndPermissionsStayVanilla(GameTestHelper helper) throws CommandSyntaxException {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.register(Commands.literal("locate").requires(source -> source.hasPermission(2))
                .then(Commands.literal("structure").then(Commands.argument("structure", ResourceOrTagLocationArgument.resourceOrTag(Registry.STRUCTURE_REGISTRY))
                        .executes(context -> 731))));
        VisitorLocate.install(dispatcher.getRoot());
        var source = helper.getLevel().getServer().createCommandSourceStack().withLevel(helper.getLevel()).withPermission(2).withSuppressedOutput();
        require(helper, dispatcher.execute("locate structure minecraft:stronghold", source) == 731, "unrelated structures must use the original executor");
        require(helper, dispatcher.execute("locate structure #minecraft:eye_of_ender_located", source) == 731, "unrelated tags must use the original executor");
        boolean rejected = false;
        try { dispatcher.execute("locate structure jurassicraft:visitor_centre", source.withPermission(0)); }
        catch (CommandSyntaxException expected) { rejected = true; }
        require(helper, rejected, "the original locate permission must be retained");
        helper.succeed();
    }

    @GameTest(template = "cleaning_station_test", templateNamespace = PortMod.MOD_ID, timeoutTicks = 400)
    public static void visitorLocateChecksStartsAndMixedTags(GameTestHelper helper) throws CommandSyntaxException {
        var level = helper.getLevel();
        var registry = level.registryAccess().registryOrThrow(Registry.STRUCTURE_REGISTRY);
        var visitor = registry.getHolderOrThrow(VisitorLocate.VISITOR);
        StructureParityChecks.visitorChunkIsSearchable(helper);
        var candidate = VisitorPlacement.candidateForLocate(level);
        var position = candidate.flatMap(chunk -> VisitorLocate.actualStart(level, visitor.value(), chunk));
        StructureParityChecks.vanillaSearchFindsVisitor(helper, visitor, candidate, position);
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        var source = level.getServer().createCommandSourceStack().withLevel(level).withPermission(2)
                .withPosition(Vec3.atLowerCornerOf(origin)).withSuppressedOutput();
        var commands = level.getServer().getCommands();
        int direct = commands.performPrefixedCommand(source, "locate structure jurassicraft:visitor_centre");
        require(helper, direct == position.map(pos -> distance(origin, pos)).orElse(0),
                "the command must return the valid visitor start, or fail if the candidate is rejected");
        require(helper, VisitorPlacement.candidate(level, false, false).isEmpty(), "disabled generation must not select new candidates");
        var existingCandidate = VisitorPlacement.candidate(level, false, true);
        var existingPosition = existingCandidate.flatMap(chunk -> VisitorLocate.actualStart(level, visitor.value(), chunk));
        require(helper, existingPosition.equals(position)
                && commands.performPrefixedCommand(source, "locate structure jurassicraft:visitor_centre")
                        == existingPosition.map(pos -> distance(origin, pos)).orElse(0),
                "disabled generation must not hide an existing start or invent one");

        var tag = registry.getTag(TagKey.create(Registry.STRUCTURE_REGISTRY, new ResourceLocation(PortMod.MOD_ID, "park_structures")));
        require(helper, tag.isPresent() && tag.get().contains(visitor), "park tag must include the visitor centre");
        Holder<Structure> paddock = registry.getHolderOrThrow(net.minecraft.resources.ResourceKey.create(
                Registry.STRUCTURE_REGISTRY, new ResourceLocation(PortMod.MOD_ID, "raptor_paddock")));
        require(helper, tag.get().contains(paddock), "park tag must include the paddock");
        // Exercise the actual Brigadier parser and installed command with bounded
        // locations, without asking vanilla to search a huge unsuitable world.
        BlockPos mixedOrigin = origin.atY(0), visitorPos = mixedOrigin.offset(80, 0, 0), paddockPos = mixedOrigin.offset(20, 0, 0);
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.register(Commands.literal("locate").requires(s -> s.hasPermission(2))
                .then(Commands.literal("structure").then(Commands.argument("structure", ResourceOrTagLocationArgument.resourceOrTag(Registry.STRUCTURE_REGISTRY))
                        .executes(context -> 731))));
        VisitorLocate.install(dispatcher.getRoot(), new VisitorLocate.Search() {
            @Override public Optional<BlockPos> visitor(ServerLevel world, Structure structure) {
                return structure == visitor.value() ? Optional.of(visitorPos) : Optional.empty();
            }
            @Override public Pair<BlockPos, Holder<Structure>> vanilla(ServerLevel world, HolderSet<Structure> structures, BlockPos from) {
                return structures.contains(paddock) ? Pair.of(paddockPos, paddock) : null;
            }
        });
        var mixedSource = source.withPosition(Vec3.atLowerCornerOf(mixedOrigin));
        require(helper, dispatcher.execute("locate structure jurassicraft:visitor_centre", mixedSource) == 80,
                "the direct visitor command must select its own start");
        require(helper, dispatcher.execute("locate structure #jurassicraft:park_structures", mixedSource) == 20,
                "a mixed tag must select the closer paddock");
        require(helper, dispatcher.execute("locate structure #jurassicraft:park_structures",
                        mixedSource.withPosition(Vec3.atLowerCornerOf(mixedOrigin.offset(100, 0, 0)))) == 20,
                "a mixed tag must select the visitor when it is closer");
        helper.succeed();
    }

    private static int distance(BlockPos origin, BlockPos destination) {
        long x = (long) destination.getX() - origin.getX(), z = (long) destination.getZ() - origin.getZ();
        return (int) Math.sqrt(x * x + z * z);
    }
}
