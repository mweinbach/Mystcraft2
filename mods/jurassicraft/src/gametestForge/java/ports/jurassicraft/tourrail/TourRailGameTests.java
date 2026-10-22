package ports.jurassicraft.tourrail;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.vehicle.ExplorerEntity;
import ports.jurassicraft.vehicle.VehicleEntity;
import ports.jurassicraft.vehicle.VehicleRegistry;

/** Optional in-world checks. */
@GameTestHolder(PortMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TourRailGameTests {
    private TourRailGameTests() {}
    private static void require(GameTestHelper h, boolean value, String message) { if (!value) h.fail(message); }
    private static void rail(GameTestHelper h, BlockPos relative, TourRailShape shape) {
        h.getLevel().setBlock(h.absolutePos(relative), TourRailRegistry.RAIL.get().defaultBlockState().setValue(TourRailBlock.SHAPE, shape), 3);
    }
    @GameTest(template = "vehicle_test", templateNamespace = PortMod.MOD_ID)
    public static void explorerRunsWithPassengerOutsideDriverSeat(GameTestHelper h) {
        for (int x = 2; x < 9; x++) for (int z = 5; z < 10; z++) h.setBlock(new BlockPos(x,0,z), Blocks.STONE);
        for (int x = 3; x <= 7; x++) rail(h, new BlockPos(x,1,7), TourRailShape.EAST_WEST);
        ExplorerEntity explorer = h.spawn(VehicleRegistry.EXPLORER.get(), new BlockPos(3,1,7));
        Player passenger = h.makeMockPlayer();
        require(h, passenger.startRiding(explorer) && explorer.changeSeat(passenger, 1), "passenger should move out of the driver seat");
        require(h, explorer.getControllingPassenger() == null, "the driver seat should be empty");
        passenger.fallDistance = 12;
        double start = explorer.getX();
        for (int i = 0; i < 4; i++) explorer.tick();
        require(h, passenger.fallDistance == 0, "riders on automatic tour rails should have their fall distance cleared");
        require(h, explorer.isOnTourRail() && explorer.getX() > start + .2,
                "a seated passenger should keep the automatic tour moving");
        passenger.stopRiding();
        double stopped = explorer.getX();
        for (int i = 0; i < 4; i++) explorer.tick();
        require(h, Math.abs(explorer.getX() - stopped) < .001, "an empty Explorer should stop on the rail");
        explorer.discard();
        h.succeed();
    }
    @GameTest(template = "vehicle_test", templateNamespace = PortMod.MOD_ID)
    public static void explorerClimbsAndDescends(GameTestHelper h) {
        for (int x = 0; x < 15; x++) for (int z = 0; z < 15; z++) h.setBlock(new BlockPos(x,0,z), Blocks.STONE);
        h.setBlock(new BlockPos(5,1,7), Blocks.STONE);
        h.setBlock(new BlockPos(6,1,7), Blocks.STONE);
        rail(h, new BlockPos(3,1,7), TourRailShape.EAST_WEST);
        rail(h, new BlockPos(4,1,7), TourRailShape.ASCENDING_EAST);
        rail(h, new BlockPos(5,2,7), TourRailShape.EAST_WEST);
        rail(h, new BlockPos(6,2,7), TourRailShape.EAST_WEST);
        BlockPos slope = h.absolutePos(new BlockPos(4,1,7)), high = h.absolutePos(new BlockPos(5,2,7));
        require(h, TourRailConnections.connected(h.getLevel(), slope, high) && TourRailConnections.connected(h.getLevel(), high, slope), "slope must connect both ways");
        ExplorerEntity explorer = h.spawn(VehicleRegistry.EXPLORER.get(), new BlockPos(3,1,7));
        Player driver = h.makeMockPlayer();
        require(h, driver.startRiding(explorer), "driver should mount");
        for (int i = 0; i < 15; i++) explorer.tick();
        CompoundTag saved = explorer.saveWithoutId(new CompoundTag());
        ExplorerEntity loaded = VehicleRegistry.EXPLORER.get().create(h.getLevel());
        require(h, loaded != null, "explorer should be restorable");
        loaded.load(saved);
        require(h, loaded.isOnTourRail() && saved.contains("TourRail"), "track state must survive save/load");
        for (int i = 0; i < 30; i++) explorer.tick();
        require(h, explorer.isOnTourRail() && explorer.getX() > high.getX() + .4 && explorer.getY() > high.getY(),
                "Explorer must ascend onto the upper support");
        require(h, explorer.acceptControls(driver, VehicleEntity.BACK), "driver should select reverse");
        for (int i = 0; i < 45; i++) explorer.tick();
        BlockPos lower = h.absolutePos(new BlockPos(3,1,7));
        require(h, explorer.getX() < lower.getX() + .7 && explorer.getY() < lower.getY() + .4,
                "Explorer must descend from the upper support");
        explorer.ejectPassengers();
        explorer.discard();
        h.succeed();
    }
    @GameTest(template = "vehicle_test", templateNamespace = PortMod.MOD_ID)
    public static void explorerDoesNotSnapThroughBridge(GameTestHelper h) {
        for (int x = 4; x <= 10; x++) for (int z = 4; z <= 10; z++) {
            h.setBlock(new BlockPos(x,0,z), Blocks.STONE);
            h.setBlock(new BlockPos(x,2,z), Blocks.STONE);
        }
        rail(h, new BlockPos(7,1,7), TourRailShape.EAST_WEST);
        ExplorerEntity explorer = h.spawn(VehicleRegistry.EXPLORER.get(), new BlockPos(7,3,7));
        double originalY = explorer.getY();
        explorer.tick();
        require(h, !explorer.isOnTourRail() && explorer.getY() >= originalY - .01,
                "an Explorer on a bridge must not teleport to a track beneath it");
        explorer.discard();
        h.succeed();
    }
    @GameTest(template = "vehicle_test", templateNamespace = PortMod.MOD_ID)
    public static void placedTracksFindDiagonalTransition(GameTestHelper h) {
        for (int x = 3; x < 8; x++) for (int z = 3; z < 8; z++) h.setBlock(new BlockPos(x,0,z), Blocks.STONE);
        BlockPos a = new BlockPos(4,1,5), turn = new BlockPos(5,1,5), b = new BlockPos(6,1,4);
        for (BlockPos pos : new BlockPos[] {a, turn, b}) h.setBlock(pos, TourRailRegistry.RAIL.get());
        // setBlock notifies cardinal neighbors only. The diagonal is what makes the transition,
        // so settle each placed rail now. A one-tick schedule does not run within three GameTest
        // ticks when the batch is behind and the chunk is not ticking yet.
        var level = h.getLevel();
        for (BlockPos pos : new BlockPos[] {a, turn, b}) {
            BlockPos absolute = h.absolutePos(pos);
            level.getBlockState(absolute).tick(level, absolute, level.getRandom());
        }
        BlockPos ca = h.absolutePos(a), ct = h.absolutePos(turn), cb = h.absolutePos(b);
        require(h, level.getBlockState(ct).getValue(TourRailBlock.SHAPE) == TourRailShape.HORIZONTAL_NE,
                "placed rails should form a diagonal transition");
        require(h, TourRailConnections.connected(level, ca, ct) && TourRailConnections.connected(level, ct, cb),
                "automatically shaped rails should connect");
        h.succeed();
    }
    @GameTest(template = "vehicle_test", templateNamespace = PortMod.MOD_ID)
    public static void fastTrackMovesExplorerFaster(GameTestHelper h) {
        for (int x = 2; x < 8; x++) for (int z = 4; z < 11; z++) h.setBlock(new BlockPos(x,0,z), Blocks.STONE);
        for (int x = 3; x <= 6; x++) {
            h.setBlock(new BlockPos(x,1,5), TourRailRegistry.RAIL.get().defaultBlockState().setValue(TourRailBlock.SHAPE, TourRailShape.EAST_WEST));
            h.setBlock(new BlockPos(x,1,9), TourRailRegistry.FAST.get().defaultBlockState().setValue(TourRailBlock.SHAPE, TourRailShape.EAST_WEST));
        }
        ExplorerEntity normal = h.spawn(VehicleRegistry.EXPLORER.get(), new BlockPos(3,1,5));
        ExplorerEntity fast = h.spawn(VehicleRegistry.EXPLORER.get(), new BlockPos(3,1,9));
        require(h, h.makeMockPlayer().startRiding(normal) && h.makeMockPlayer().startRiding(fast), "drivers should mount");
        for (int i = 0; i < 2; i++) { normal.tick(); fast.tick(); }
        require(h, fast.getX() > normal.getX() + .2, "fast track should advance farther than normal track");
        normal.ejectPassengers();
        fast.ejectPassengers();
        normal.discard();
        fast.discard();
        h.succeed();
    }
    @GameTest(template = "vehicle_test", templateNamespace = PortMod.MOD_ID)
    public static void diagonalTransitionConnects(GameTestHelper h) {
        for (int x = 5; x < 9; x++) for (int z = 4; z < 9; z++) h.setBlock(new BlockPos(x,0,z), Blocks.STONE);
        BlockPos transition = new BlockPos(6,1,6), south = new BlockPos(6,1,7), diagonal = new BlockPos(7,1,5);
        rail(h, transition, TourRailShape.VERTICAL_NE);
        rail(h, south, TourRailShape.NORTH_SOUTH);
        rail(h, diagonal, TourRailShape.DIAGONAL_NE_SW);
        BlockPos center = h.absolutePos(transition), s = h.absolutePos(south), d = h.absolutePos(diagonal);
        require(h, TourRailConnections.connected(h.getLevel(), center, s), "south connection");
        require(h, TourRailConnections.connected(h.getLevel(), center, d) && TourRailConnections.connected(h.getLevel(), d, center), "diagonal connection");
        h.succeed();
    }
}
