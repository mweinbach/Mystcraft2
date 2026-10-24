package ports.gridwright.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import ports.gridwright.grid.ProducerBlockEntity;
import ports.gridwright.grid.ProducerKind;
import ports.gridwright.registry.ModBlocks;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static ports.gridwright.gametest.GameTestSupport.Owner;
import static ports.gridwright.gametest.GameTestSupport.TEMPLATE;
import static ports.gridwright.gametest.GameTestSupport.check;
import static ports.gridwright.gametest.GameTestSupport.producer;
import static ports.gridwright.gametest.GameTestSupport.snapshot;
import static ports.gridwright.gametest.GameTestSupport.waterColumn;

/** Each Grid Power producer: what it measures, and that its owner's grid receives it. */
public class ProducerGameTests {

    private static final double EPSILON = 1e-9;

    private static void near(double actual, double expected, String what) {
        check(Math.abs(actual - expected) < EPSILON, what + ": expected " + expected + ", got " + actual);
    }

    @GameTest(template = TEMPLATE)
    public static void producerFormulas(GameTestHelper helper) {
        near(ProducerKind.solar(true, true, false), 4.0, "solar in sun");
        near(ProducerKind.solar(true, true, true), 2.0, "solar in rain");
        near(ProducerKind.solar(false, true, false), 0.0, "solar at night");
        near(ProducerKind.solar(true, false, false), 0.0, "covered solar");
        near(ProducerKind.lunar(true, true, false), 4.0, "lunar at night");
        near(ProducerKind.lunar(true, true, true), 2.0, "lunar in rain");
        near(ProducerKind.lunar(false, true, false), 0.0, "lunar by day");
        check(ProducerKind.sunUp(0.0F), "Sun should be up at noon");
        check(ProducerKind.sunUp(0.2F), "Sun should be up in the afternoon");
        check(!ProducerKind.sunUp(0.3F), "Sun should be down after dusk");
        check(!ProducerKind.sunUp(0.5F), "Sun should be down at midnight");
        check(ProducerKind.sunUp(0.8F), "Sun should be up after dawn");
        near(ProducerKind.water(0), 0.0, "dry water mill");
        near(ProducerKind.water(1), 3.0, "one side");
        near(ProducerKind.water(4), 12.0, "four sides");
        near(ProducerKind.wind(63, 63, 8, false, false, true), 1.0, "wind at sea level");
        near(ProducerKind.wind(63 + 32, 63, 8, false, false, true), 3.0, "wind 32 blocks up");
        near(ProducerKind.wind(320, 63, 8, false, false, true), 6.0, "wind height cap");
        near(ProducerKind.wind(63 + 32, 63, 4, false, false, true), 1.5, "half-enclosed wind mill");
        near(ProducerKind.wind(63, 63, 8, true, false, true), 1.5, "wind in rain");
        near(ProducerKind.wind(63, 63, 8, true, true, true), 2.0, "wind in a thunderstorm");
        near(ProducerKind.wind(62, 63, 8, false, false, true), 0.0, "wind below sea level");
        near(ProducerKind.wind(100, 63, 8, false, false, false), 0.0, "wind without a sky");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void waterMillCountsFlowingSides(GameTestHelper helper) {
        Owner owner = Owner.random("gwJ");
        BlockPos pos = new BlockPos(3, 2, 3);
        ProducerBlockEntity mill = producer(helper, ModBlocks.WATER_MILL, pos, owner);
        helper.startSequence()
                .thenExecuteAfter(2, () -> near(mill.output(), 0.0, "dry water mill"))
                .thenExecute(() -> waterColumn(helper, pos, Direction.NORTH))
                .thenWaitUntil(() -> near(mill.output(), 3.0, "one flowing side"))
                .thenExecute(() -> waterColumn(helper, pos, Direction.SOUTH))
                .thenWaitUntil(() -> {
                    near(mill.output(), 6.0, "two flowing sides");
                    near(snapshot(helper, owner.gridKey()).capacity(), 6.0, "owner's capacity");
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void windMillNeedsOpenAir(GameTestHelper helper) {
        Owner owner = Owner.random("gwK");
        BlockPos pos = new BlockPos(3, 4, 3);
        ProducerBlockEntity mill = producer(helper, ModBlocks.WIND_MILL, pos, owner);
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(pos);
        AtomicReference<Double> open = new AtomicReference<>();
        helper.startSequence()
                .thenExecuteAfter(2, () -> {
                    check(ProducerKind.openSides(level, absolute) == 8, "Mill should start in open air");
                    double expected = ProducerKind.wind(absolute.getY(), level.getSeaLevel(), 8, level.isRaining(),
                            level.isThundering(), level.dimensionType().hasSkyLight());
                    near(mill.output(), expected, "open wind mill");
                    near(snapshot(helper, owner.gridKey()).capacity(), expected, "owner's capacity");
                    open.set(expected);
                })
                .thenExecute(() -> {
                    for (Direction direction : Direction.Plane.HORIZONTAL) helper.setBlock(pos.relative(direction), Blocks.STONE);
                })
                .thenWaitUntil(() -> {
                    check(ProducerKind.openSides(level, absolute) == 4, "Four sides should be blocked");
                    near(mill.output(), open.get() / 2, "half-enclosed wind mill");
                })
                .thenSucceed();
    }

    /**
     * Panels at noon and at midnight report exactly what their formula gives for where they stand, and
     * their owner's grid receives it. Fabric's GameTest arenas are open to the sky once their barrier
     * roof is lifted, so the solar panel must give its full output at noon and nothing at midnight;
     * Forge's are underground, where it must give nothing at any hour. A covered panel never produces.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void panelsFollowTheSkyAndTheHour(GameTestHelper helper) {
        Owner owner = Owner.random("gwL");
        BlockPos solarPos = new BlockPos(2, 2, 2);
        BlockPos lunarPos = new BlockPos(5, 2, 2);
        ProducerBlockEntity solar = producer(helper, ModBlocks.SOLAR_PANEL, solarPos, owner);
        ProducerBlockEntity lunar = producer(helper, ModBlocks.LUNAR_PANEL, lunarPos, owner);
        helper.setBlock(lunarPos.above(), Blocks.STONE);
        ServerLevel level = helper.getLevel();
        long savedTime = level.getDayTime();
        List<BlockPos> roof = openSky(level, helper.absolutePos(solarPos));
        helper.startSequence()
                .thenExecute(() -> level.setDayTime(6_000))
                .thenWaitUntil(() -> expectPanels(helper, solar, solarPos, lunar, lunarPos, true))
                .thenExecute(() -> level.setDayTime(18_000))
                .thenWaitUntil(() -> expectPanels(helper, solar, solarPos, lunar, lunarPos, false))
                .thenExecute(() -> {
                    level.setDayTime(savedTime);
                    for (BlockPos pos : roof) level.setBlockAndUpdate(pos, Blocks.BARRIER.defaultBlockState());
                })
                .thenSucceed();
    }

    /**
     * A thunderstorm at noon darkens the sky enough that vanilla calls it night, but panels go by the
     * sun: an exposed solar panel gives its rainy output and an exposed lunar panel nothing. Runs in its
     * own batch because it changes the weather, which the wind mill test reads.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 200, batch = "gridwright_weather")
    public static void panelsIgnoreStormDarkness(GameTestHelper helper) {
        Owner owner = Owner.random("gwW");
        BlockPos solarPos = new BlockPos(2, 2, 2);
        BlockPos lunarPos = new BlockPos(5, 2, 2);
        ProducerBlockEntity solar = producer(helper, ModBlocks.SOLAR_PANEL, solarPos, owner);
        ProducerBlockEntity lunar = producer(helper, ModBlocks.LUNAR_PANEL, lunarPos, owner);
        ServerLevel level = helper.getLevel();
        long savedTime = level.getDayTime();
        List<BlockPos> roof = new ArrayList<>(openSky(level, helper.absolutePos(solarPos)));
        roof.addAll(openSky(level, helper.absolutePos(lunarPos)));
        helper.startSequence()
                .thenExecute(() -> {
                    level.setDayTime(6_000);
                    level.setWeatherParameters(0, 6_000, true, true);
                    level.setRainLevel(1.0F);
                    level.setThunderLevel(1.0F);
                })
                .thenWaitUntil(() -> {
                    check(level.isRaining() && level.isThundering(), "Storm has not started yet");
                    check(ProducerKind.sunUp(level), "The sun should be up at noon in a storm");
                    boolean skylight = level.dimensionType().hasSkyLight() && !level.dimensionType().hasFixedTime();
                    boolean solarSky = level.canSeeSky(helper.absolutePos(solarPos).above());
                    boolean lunarSky = level.canSeeSky(helper.absolutePos(lunarPos).above());
                    // Where the arena is open, prove this is the case vanilla's isDay() gets wrong.
                    if (solarSky && skylight) check(!level.isDay(), "Storm should darken the sky into vanilla night");
                    double expected = ProducerKind.solar(skylight, solarSky, true);
                    if (solarSky && skylight) near(expected, ProducerKind.PANEL_GP / 2, "rainy solar output");
                    near(solar.output(), expected, "solar panel in a noon storm (sky " + solarSky + ")");
                    near(lunar.output(), 0.0, "lunar panel in a noon storm (sky " + lunarSky + ")");
                    near(snapshot(helper, owner.gridKey()).capacity(), expected, "owner's capacity");
                })
                .thenExecute(() -> {
                    level.setWeatherParameters(6_000, 0, false, false);
                    level.setRainLevel(0.0F);
                    level.setThunderLevel(0.0F);
                    level.setDayTime(savedTime);
                    for (BlockPos pos : roof) level.setBlockAndUpdate(pos, Blocks.BARRIER.defaultBlockState());
                })
                .thenSucceed();
    }

    /**
     * GameTest roofs each arena with barriers. Where only that roof stands between {@code pos} and the
     * sky, removes it and returns where it was; where the arena is underground, changes nothing.
     */
    private static List<BlockPos> openSky(ServerLevel level, BlockPos pos) {
        List<BlockPos> barriers = new ArrayList<>();
        for (BlockPos above = pos.above(); above.getY() < level.getMaxBuildHeight(); above = above.above()) {
            if (level.getBlockState(above).is(Blocks.BARRIER)) barriers.add(above);
            else if (!level.getBlockState(above).isAir()) return List.of();
        }
        for (BlockPos barrier : barriers) level.setBlockAndUpdate(barrier, Blocks.AIR.defaultBlockState());
        return barriers;
    }

    private static void expectPanels(GameTestHelper helper, ProducerBlockEntity solar, BlockPos solarPos,
                                     ProducerBlockEntity lunar, BlockPos lunarPos, boolean noon) {
        ServerLevel level = helper.getLevel();
        check(ProducerKind.sunUp(level) == noon, "Sun position does not match the hour");
        boolean skylight = level.dimensionType().hasSkyLight() && !level.dimensionType().hasFixedTime();
        boolean solarSky = level.canSeeSky(helper.absolutePos(solarPos).above());
        check(!level.canSeeSky(helper.absolutePos(lunarPos).above()), "The lunar panel should be covered");
        double expected = ProducerKind.solar(skylight && noon, solarSky, level.isRaining());
        near(solar.output(), expected, "solar panel (" + (noon ? "noon" : "midnight") + ", sky " + solarSky + ")");
        near(lunar.output(), 0.0, "covered lunar panel");
        if (!solarSky) near(solar.output(), 0.0, "solar panel without a sky");
        near(snapshot(helper, solar.gridKey(level.getServer())).capacity(), expected, "owner's capacity");
    }
}
