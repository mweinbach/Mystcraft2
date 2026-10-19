package ports.jurassicraft.structures;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import ports.jurassicraft.PortMod;

@GameTestHolder(PortMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SpawnParkCommandGameTests {
    private SpawnParkCommandGameTests() {}
    private static void check(GameTestHelper h, boolean value, String message) { if (!value) h.fail(message); }
    private static Set<String> suggestions(CommandDispatcher<CommandSourceStack> dispatcher, CommandSourceStack source, String input) {
        return dispatcher.getCompletionSuggestions(dispatcher.parse(input, source)).join().getList().stream()
                .map(suggestion -> suggestion.getText()).collect(Collectors.toSet());
    }
    private static void fails(GameTestHelper h, CommandDispatcher<CommandSourceStack> dispatcher, CommandSourceStack source,
            String command, String expectedMessage) {
        try {
            dispatcher.execute(command, source);
            h.fail("command unexpectedly succeeded: " + command);
        } catch (CommandSyntaxException expected) {
            if (expectedMessage != null) check(h, expected.getMessage().contains(expectedMessage),
                    "incorrect error for " + command + ": " + expected.getMessage());
        }
    }

    @GameTest(template = "cleaning_station_test", templateNamespace = PortMod.MOD_ID)
    public static void permissionsErrorsAndCompletions(GameTestHelper h) {
        var server = h.getLevel().getServer();
        var dispatcher = server.getCommands().getDispatcher();
        var source = server.createCommandSourceStack().withLevel(h.getLevel()).withPermission(2).withSuppressedOutput();
        check(h, dispatcher.getRoot().getChild("spawnjc") != null, "command was not registered on the Forge bus");
        check(h, suggestions(dispatcher, source, "spawnjc ").containsAll(Set.of("visitor_center", "raptor_paddock")),
                "structure completions are missing");
        check(h, suggestions(dispatcher, source, "spawnjc raptor_paddock ")
                .containsAll(Set.of("none", "clockwise_90", "clockwise_180", "counterclockwise_90")),
                "rotation completions are missing");
        check(h, suggestions(dispatcher, source, "spawnjc visitor_center clockwise_90 ")
                .containsAll(Set.of("none", "front_back", "left_right")), "mirror completions are missing");
        fails(h, dispatcher, source.withPermission(0), "spawnjc raptor_paddock", null);
        fails(h, dispatcher, source, "spawnjc imaginary", "Unknown JurassiCraft structure");
        fails(h, dispatcher, source, "spawnjc raptor_paddock wrong", "Unknown rotation");
        fails(h, dispatcher, source, "spawnjc raptor_paddock none wrong", "Unknown mirror");
        fails(h, dispatcher, source, "spawnjc raptor_paddock none none badseed", null);
        h.succeed();
    }

    @GameTest(template = "park_structure_test", templateNamespace = PortMod.MOD_ID, timeoutTicks = 200)
    public static void commandPlacesTransformedPaddockAndLoot(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos root = h.absolutePos(BlockPos.ZERO);
        // Make the entire fixture flat and remove leftovers from previous runs.
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) {
            // GameTest fixtures can be far underground. Clear all terrain above the
            // fixture too, so groundHeight sees this floor on first and later runs.
            int worldX = root.getX() + x, worldZ = root.getZ() + z;
            int top = Math.max(root.getY() + 36, level.getHeight(Heightmap.Types.WORLD_SURFACE, worldX, worldZ));
            for (int y = root.getY() + 2; y < top; y++) {
                cursor.set(worldX, y, worldZ);
                if (!level.getBlockState(cursor).isAir()) level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 2);
            }
            level.setBlock(cursor.set(root.getX() + x, root.getY() + 1, root.getZ() + z), Blocks.STONE.defaultBlockState(), 2);
        }
        BlockPos request = root.offset(22, 2, 31);
        var source = level.getServer().createCommandSourceStack().withLevel(level).withPermission(2)
                .withPosition(Vec3.atLowerCornerOf(request)).withSuppressedOutput();
        int result = level.getServer().getCommands().performPrefixedCommand(source,
                "spawnjc RAPTOR_PADDOCK CLOCKWISE_90 FRONT_BACK 12345");
        check(h, result == 1, "command failed to place paddock");
        Rotation rotation = Rotation.CLOCKWISE_90;
        Mirror mirror = Mirror.FRONT_BACK;
        BlockPos origin = new BlockPos(request.getX(), root.getY() + 1, request.getZ());
        BlockPos marker = origin.offset(StructureTemplate.transform(new BlockPos(27, 5, 10), mirror, rotation, BlockPos.ZERO));
        check(h, level.getBlockState(marker).isAir(), "loot marker must be removed");
        var chest = level.getBlockEntity(marker.below());
        check(h, chest instanceof ChestBlockEntity
                && chest.saveWithoutMetadata().getString("LootTable").equals("minecraft:chests/village/village_weaponsmith"),
                "command must apply the paddock chest loot marker");
        BlockPos door = origin.offset(StructureTemplate.transform(new BlockPos(26, 5, 9), mirror, rotation, BlockPos.ZERO));
        var lower = level.getBlockState(door);
        var upper = level.getBlockState(door.above());
        check(h, new ResourceLocation(PortMod.MOD_ID, "calamites_door").equals(ForgeRegistries.BLOCKS.getKey(lower.getBlock()))
                && lower.getBlock() == upper.getBlock(), "command must place both door halves");
        check(h, lower.getValue(DoorBlock.FACING) == rotation.rotate(mirror.mirror(Direction.SOUTH)),
                "command must transform the door facing");
        h.succeed();
    }
}
