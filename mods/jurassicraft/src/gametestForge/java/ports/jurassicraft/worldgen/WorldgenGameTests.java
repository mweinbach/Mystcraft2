package ports.jurassicraft.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.egg.FossilNestRegistry;

@GameTestHolder(PortMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WorldgenGameTests {
    private WorldgenGameTests() {}
    private static void require(GameTestHelper helper, boolean value, String message) { if (!value) helper.fail(message); }

    @GameTest(template = "plant_culture_test", templateNamespace = PortMod.MOD_ID)
    public static void trackwayReplacesOnlyStone(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos start = new BlockPos(0, 1, 1);
        helper.setBlock(start, Blocks.STONE);
        helper.setBlock(start.east(), Blocks.BEDROCK);
        helper.setBlock(start.east(2), Blocks.CHEST);
        var track = FossilNestRegistry.trackway(1).get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.EAST);
        require(helper, FossilTrackwayFeature.placeTrack(level, helper.absolutePos(start), Direction.EAST, track, 3), "stone not replaced");
        require(helper, helper.getBlockState(start).equals(track), "track type or direction changed");
        require(helper, helper.getBlockState(start.east()).is(Blocks.BEDROCK), "bedrock replaced");
        require(helper, helper.getBlockState(start.east(2)).is(Blocks.CHEST), "block entity replaced");
        helper.succeed();
    }
}
