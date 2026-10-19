package ports.jurassicraft.fence;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.entity.DinosaurEntity;
import ports.jurassicraft.entity.DinosaurRegistry;

/** Optional in-world checks. */
@GameTestHolder(PortMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FenceGameTests {
    private FenceGameTests() {}

    private static BlockPos at(int x, int y, int z) { return new BlockPos(x, y, z); }
    private static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
    private static boolean powered(GameTestHelper helper, BlockPos relativeWire) {
        return FenceWireBlock.powered(helper.getLevel(), helper.absolutePos(relativeWire));
    }
    private static void base(GameTestHelper helper, int x, int z) {
        helper.setBlock(at(x, 1, z), FenceRegistry.BASE.get());
    }
    private static void wire(GameTestHelper helper, int x, int z) {
        helper.setBlock(at(x, 2, z), FenceRegistry.WIRE.get());
    }

    @GameTest(template = "fence_test", templateNamespace = PortMod.MOD_ID)
    public static void connectionRangeAndDisconnection(GameTestHelper helper) {
        for (int x = 2; x <= 11; x++) base(helper, x, 2);
        helper.setBlock(at(2, 2, 2), FenceRegistry.POLE.get());
        wire(helper, 10, 2);
        wire(helper, 11, 2);
        BlockPos near = at(10, 2, 2), far = at(11, 2, 2);
        check(helper, !powered(helper, near), "wire must be unpowered without a signal");
        helper.setBlock(at(1, 1, 2), Blocks.REDSTONE_BLOCK);
        check(helper, powered(helper, near), "connected wire exactly eight blocks from the pole must be powered");
        check(helper, !powered(helper, far), "a wire nine blocks from every powered pole must be unpowered");
        helper.setBlock(at(6, 1, 2), Blocks.AIR);
        check(helper, !powered(helper, near), "breaking the base path must disconnect power");
        base(helper, 6, 2);
        check(helper, powered(helper, near), "restoring the base path must restore power");
        helper.setBlock(at(1, 1, 2), Blocks.AIR);
        check(helper, !powered(helper, near), "removing the redstone signal must remove power");
        helper.succeed();
    }

    @GameTest(template = "fence_test", templateNamespace = PortMod.MOD_ID)
    public static void diagonalBasesConduct(GameTestHelper helper) {
        base(helper, 2, 2);
        base(helper, 3, 3);
        helper.setBlock(at(2, 2, 2), FenceRegistry.POLE.get());
        wire(helper, 3, 3);
        helper.setBlock(at(1, 1, 2), Blocks.REDSTONE_BLOCK);
        check(helper, powered(helper, at(3, 2, 3)), "same-height diagonal bases must conduct power");
        helper.succeed();
    }

    @GameTest(template = "fence_test", templateNamespace = PortMod.MOD_ID)
    public static void powerRequiresContinuousWireColumn(GameTestHelper helper) {
        base(helper, 2, 2); base(helper, 3, 2);
        helper.setBlock(at(2, 2, 2), FenceRegistry.POLE.get());
        helper.setBlock(at(2, 3, 2), FenceRegistry.WIRE.get());
        helper.setBlock(at(3, 2, 2), FenceRegistry.WIRE.get());
        helper.setBlock(at(3, 3, 2), FenceRegistry.WIRE.get());
        helper.setBlock(at(1, 1, 2), Blocks.REDSTONE_BLOCK);
        check(helper, !powered(helper, at(2, 3, 2)), "wire above a pole should not conduct");
        check(helper, powered(helper, at(3, 3, 2)), "a continuous stack of wire should conduct");
        helper.setBlock(at(3, 2, 2), Blocks.AIR);
        check(helper, !powered(helper, at(3, 3, 2)), "a gap should disconnect the upper wire");
        helper.succeed();
    }

    @GameTest(template = "fence_test", templateNamespace = PortMod.MOD_ID)
    public static void rangeIsMeasuredFromThePoweredPole(GameTestHelper helper) {
        // Every part of this bent path lies within eight blocks of the pole;
        // the first part lies outside the endpoint wire's own radius.
        base(helper, 3, 4);
        for (int z = 5; z <= 7; z++) base(helper, 3, z);
        for (int x = 4; x <= 10; x++) base(helper, x, 7);
        for (int z = 4; z <= 6; z++) base(helper, 10, z);
        base(helper, 11, 4);
        helper.setBlock(at(3, 2, 4), FenceRegistry.POLE.get());
        wire(helper, 11, 4);
        helper.setBlock(at(2, 1, 4), Blocks.REDSTONE_BLOCK);
        check(helper, powered(helper, at(11, 2, 4)), "connected path inside the pole's range must conduct");
        helper.succeed();
    }

    @GameTest(template = "fence_test", templateNamespace = PortMod.MOD_ID)
    public static void connectedShapesUpdate(GameTestHelper helper) {
        BlockPos pole = at(2, 2, 2), wire = at(3, 2, 2);
        helper.setBlock(wire, FenceRegistry.WIRE.get());
        helper.setBlock(pole, FenceRegistry.POLE.get());
        check(helper, helper.getBlockState(wire).getValue(IronBarsBlock.WEST), "wire must connect to adjacent pole");
        helper.setBlock(pole, Blocks.AIR);
        check(helper, helper.getBlockState(wire).getValue(IronBarsBlock.WEST)
                        && helper.getBlockState(wire).getValue(IronBarsBlock.EAST), "an isolated wire must extend in all directions");
        helper.setBlock(at(4, 2, 2), FenceRegistry.WIRE.get());
        check(helper, !helper.getBlockState(wire).getValue(IronBarsBlock.WEST)
                        && helper.getBlockState(wire).getValue(IronBarsBlock.EAST), "wire must disconnect from a removed pole");
        helper.setBlock(pole, FenceRegistry.WIRE.get());
        check(helper, helper.getBlockState(wire).getValue(IronBarsBlock.WEST), "wire must reconnect to adjacent wire");
        helper.succeed();
    }

    @GameTest(template = "fence_test", templateNamespace = PortMod.MOD_ID)
    public static void onlyPoweredWireShocks(GameTestHelper helper) {
        base(helper, 2, 2);
        base(helper, 3, 2);
        helper.setBlock(at(2, 2, 2), FenceRegistry.POLE.get());
        BlockPos relativeWire = at(3, 2, 2);
        wire(helper, 3, 2);
        BlockPos absoluteWire = helper.absolutePos(relativeWire);
        var state = helper.getBlockState(relativeWire);
        Pig pig = EntityType.PIG.create(helper.getLevel());
        if (pig == null) { helper.fail("could not create pig"); return; }
        float health = pig.getHealth();
        pig.tickCount = 10;
        state.getBlock().entityInside(state, helper.getLevel(), absoluteWire, pig);
        check(helper, pig.getHealth() == health, "unpowered wire must not shock");
        helper.setBlock(at(1, 1, 2), Blocks.REDSTONE_BLOCK);
        pig.tickCount = 9;
        state.getBlock().entityInside(state, helper.getLevel(), absoluteWire, pig);
        check(helper, pig.getHealth() == health - 1.0F, "powered wire must shock immediately");
        pig.tickCount = 10;
        state.getBlock().entityInside(state, helper.getLevel(), absoluteWire, pig);
        check(helper, pig.getHealth() == health - 1.0F, "normal damage immunity must prevent duplicate shocks");
        Player creative = helper.makeMockPlayer();
        creative.getAbilities().invulnerable = true;
        creative.tickCount = 10;
        float creativeHealth = creative.getHealth();
        state.getBlock().entityInside(state, helper.getLevel(), absoluteWire, creative);
        check(helper, creative.getHealth() == creativeHealth, "creative players must remain invulnerable");
        helper.succeed();
    }

    @GameTest(template = "fence_test", templateNamespace = PortMod.MOD_ID)
    public static void playerAndRaptorsCanClimb(GameTestHelper helper) {
        BlockPos pos = at(3, 2, 2);
        wire(helper, 3, 2);
        BlockPos absolute = helper.absolutePos(pos);
        var state = helper.getBlockState(pos);
        DinosaurEntity velo = DinosaurRegistry.type(Species.VELOCIRAPTOR).create(helper.getLevel());
        DinosaurEntity micro = DinosaurRegistry.type(Species.MICRORAPTOR).create(helper.getLevel());
        DinosaurEntity trike = DinosaurRegistry.type(Species.TRICERATOPS).create(helper.getLevel());
        if (velo == null || micro == null || trike == null) { helper.fail("could not create test dinosaurs"); return; }
        check(helper, state.isLadder(helper.getLevel(), absolute, helper.makeMockPlayer()), "players must be able to climb wire");
        check(helper, state.isLadder(helper.getLevel(), absolute, velo), "velociraptors must be able to climb wire");
        check(helper, state.isLadder(helper.getLevel(), absolute, micro), "microraptors must be able to climb wire");
        check(helper, !state.isLadder(helper.getLevel(), absolute, trike), "non-climbing dinosaurs must not climb wire");
        helper.succeed();
    }
    @GameTest(template = "fence_test", templateNamespace = PortMod.MOD_ID)
    public static void poleLightsAndArmsFollowNeighbors(GameTestHelper helper) {
        BlockPos lower = at(4, 2, 4), upper = lower.above();
        base(helper, 4, 4);
        helper.setBlock(lower, FenceRegistry.POLE.get());
        helper.setBlock(upper, FenceRegistry.POLE.get());
        helper.setBlock(at(5, 2, 4), FenceRegistry.WIRE.get());
        check(helper, !helper.getBlockState(lower).getValue(FencePoleBlock.ACTIVE)
                        && helper.getBlockState(lower).getValue(BlockStateProperties.EAST),
                "unpowered poles should connect their arms to nearby wire");
        helper.setBlock(at(3, 1, 4), Blocks.REDSTONE_BLOCK);
        check(helper, helper.getBlockState(lower).getValue(FencePoleBlock.ACTIVE)
                        && helper.getBlockState(upper).getValue(FencePoleBlock.ACTIVE),
                "a redstone signal should light the pole and stacked poles");
        helper.setBlock(at(3, 1, 4), Blocks.AIR);
        check(helper, !helper.getBlockState(lower).getValue(FencePoleBlock.ACTIVE)
                        && !helper.getBlockState(upper).getValue(FencePoleBlock.ACTIVE),
                "removing power should turn off both poles");
        helper.succeed();
    }
    @GameTest(template = "fence_test", templateNamespace = PortMod.MOD_ID)
    public static void wiresConnectAcrossOneBlockRise(GameTestHelper helper) {
        base(helper, 1, 2); base(helper, 2, 2);
        helper.setBlock(at(1, 2, 2), FenceRegistry.POLE.get());
        helper.setBlock(at(3, 2, 2), FenceRegistry.BASE.get());
        helper.setBlock(at(2, 2, 2), FenceRegistry.WIRE.get());
        helper.setBlock(at(3, 3, 2), FenceRegistry.WIRE.get());
        helper.setBlock(at(0, 1, 2), Blocks.REDSTONE_BLOCK);
        helper.runAfterDelay(2, () -> {
            check(helper, helper.getBlockState(at(2, 2, 2)).getValue(FenceWireBlock.UP) == Direction.EAST,
                    "lower wire must slope toward a higher wire");
            check(helper, helper.getBlockState(at(3, 3, 2)).getValue(IronBarsBlock.WEST),
                    "higher wire must connect back to the lower wire");
            check(helper, powered(helper, at(2, 2, 2)) && powered(helper, at(3, 3, 2)),
                    "base connections should conduct across the height change");
            helper.setBlock(at(3, 3, 2), Blocks.AIR);
            helper.runAfterDelay(2, () -> {
                check(helper, helper.getBlockState(at(2, 2, 2)).getValue(FenceWireBlock.UP) == Direction.DOWN,
                        "removing the higher wire must remove the slope");
                helper.succeed();
            });
        });
    }
    @GameTest(template = "fence_test", templateNamespace = PortMod.MOD_ID)
    public static void wireCollisionMatchesItsConnections(GameTestHelper helper) {
        BlockPos wire = at(5, 2, 5);
        helper.setBlock(wire, FenceRegistry.WIRE.get());
        var state = helper.getBlockState(wire);
        var bounds = state.getCollisionShape(helper.getLevel(), helper.absolutePos(wire), CollisionContext.empty()).bounds();
        check(helper, bounds.minX == 0 && bounds.maxX == 1 && bounds.minZ == 0 && bounds.maxZ == 1,
                "isolated wire should form the original full-width cross");
        helper.setBlock(wire.east(), FenceRegistry.POLE.get());
        state = helper.getBlockState(wire);
        bounds = state.getCollisionShape(helper.getLevel(), helper.absolutePos(wire), CollisionContext.empty()).bounds();
        check(helper, bounds.minX > 0 && bounds.maxX == 1 && bounds.minZ > 0 && bounds.maxZ < 1,
                "a single east connection should have a narrow collision boundary");
        helper.succeed();
    }
}
