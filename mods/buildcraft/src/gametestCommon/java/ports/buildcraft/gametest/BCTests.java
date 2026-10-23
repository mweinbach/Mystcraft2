package ports.buildcraft.gametest;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.carver.CarvingContext;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.builders.BCBuilders;
import ports.buildcraft.builders.QuarryBlock;
import ports.buildcraft.builders.QuarryBlockEntity;
import ports.buildcraft.core.BCCore;
import ports.buildcraft.core.FragileFluidShardItem;
import ports.buildcraft.core.MarkerBlock;
import ports.buildcraft.core.MarkerPathBlockEntity;
import ports.buildcraft.core.MarkerVolumeBlockEntity;
import ports.buildcraft.core.Wrenches;
import ports.buildcraft.core.marker.MarkerCaches;
import ports.buildcraft.core.marker.PathSubCache;
import ports.buildcraft.core.marker.VolumeConnection;
import ports.buildcraft.core.marker.VolumeSubCache;
import ports.buildcraft.core.marker.volume.Lock;
import ports.buildcraft.core.marker.volume.VolumeBox;
import ports.buildcraft.core.marker.volume.VolumeBoxes;
import ports.buildcraft.energy.BCEnergy;
import ports.buildcraft.energy.BCFluid;
import ports.buildcraft.energy.CombustionEngineBlockEntity;
import ports.buildcraft.energy.CombustionEngineMenu;
import ports.buildcraft.energy.EngineBlock;
import ports.buildcraft.energy.EngineBlockEntity;
import ports.buildcraft.energy.FuelRegistry;
import ports.buildcraft.energy.OilBiomes;
import ports.buildcraft.energy.OilWellFeature;
import ports.buildcraft.energy.PowerStage;
import ports.buildcraft.energy.RefineryRecipes;
import ports.buildcraft.energy.StirlingEngineBlockEntity;
import ports.buildcraft.factory.BCFactory;
import ports.buildcraft.factory.DistillerBlockEntity;
import ports.buildcraft.factory.DistillerMenu;
import ports.buildcraft.factory.HeatExchangerBlock;
import ports.buildcraft.factory.HeatExchangerBlockEntity;
import ports.buildcraft.factory.MinerBlockEntity;
import ports.buildcraft.factory.MiningWellBlockEntity;
import ports.buildcraft.factory.PumpBlockEntity;
import ports.buildcraft.factory.TankBlockEntity;
import ports.buildcraft.lib.FluidAmount;
import ports.buildcraft.lib.Power;
import ports.buildcraft.lib.SimplexNoise;
import ports.buildcraft.lib.marker.MarkerSubCache;
import ports.buildcraft.platform.EnergyPort;
import ports.buildcraft.platform.FluidPort;
import ports.buildcraft.platform.Platform;
import ports.buildcraft.silicon.BCSilicon;
import ports.buildcraft.silicon.LaserBlockEntity;
import ports.buildcraft.statement.StatementParameter;
import ports.buildcraft.transport.BCTransport;
import ports.buildcraft.transport.LimiterPipeBlock;
import ports.buildcraft.transport.PipeBlock;
import ports.buildcraft.transport.PipeBlockEntity;
import ports.buildcraft.transport.PipeDefinition;
import ports.buildcraft.transport.SideCheck;
import ports.buildcraft.transport.behaviour.DiamondFluidBehaviour;
import ports.buildcraft.transport.behaviour.DiamondItemBehaviour;
import ports.buildcraft.transport.behaviour.DirectionalBehaviour;
import ports.buildcraft.transport.behaviour.LimiterBehaviour;
import ports.buildcraft.transport.flow.ItemFlow;
import ports.buildcraft.transport.flow.PowerFlow;
import ports.buildcraft.transport.statements.BCTransportStatements;
import ports.buildcraft.transport.pluggable.BlockerPluggable;
import ports.buildcraft.transport.pluggable.PowerAdaptorPluggable;

/**
 * Loader-neutral GameTest bodies. Forge and Fabric each wrap these in their own test classes so
 * that the same gameplay assertions run against both loader adapters.
 */
public final class BCTests {
    /** The shared 9x8x9 template: a stone floor under open air. Vanilla places it one block above the structure block, so the floor is at y = 1. */
    public static final String TEMPLATE = "arena";
    private static final double EYE_HEIGHT = 1.62;

    private BCTests() {}

    static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }

    static <T extends BlockEntity> T tile(GameTestHelper helper, BlockPos pos, Class<T> type) {
        BlockEntity tile = helper.getBlockEntity(pos);
        if (!type.isInstance(tile)) throw new GameTestAssertException("expected " + type.getSimpleName() + " at " + pos + ", found " + tile);
        return type.cast(tile);
    }

    static ChestBlockEntity chest(GameTestHelper helper, BlockPos pos, ItemStack... contents) {
        helper.setBlock(pos, Blocks.CHEST);
        ChestBlockEntity chest = tile(helper, pos, ChestBlockEntity.class);
        for (int i = 0; i < contents.length; i++) chest.setItem(i, contents[i]);
        return chest;
    }

    static int count(ChestBlockEntity chest, Item item) {
        int total = 0;
        for (int i = 0; i < chest.getContainerSize(); i++) {
            if (chest.getItem(i).is(item)) total += chest.getItem(i).getCount();
        }
        return total;
    }

    static <T extends EngineBlockEntity> T engine(GameTestHelper helper, BlockPos pos, EngineBlock block, Direction facing, Class<T> type) {
        helper.setBlock(pos, block.defaultBlockState().setValue(EngineBlock.FACING, facing));
        return tile(helper, pos, type);
    }

    static <T extends MinerBlockEntity> T fullyPowered(GameTestHelper helper, T miner) {
        helper.onEachTick(() -> miner.battery().set(miner.battery().capacity()));
        return miner;
    }

    static PipeBlockEntity pipe(GameTestHelper helper, BlockPos pos, PipeDefinition definition) {
        helper.setBlock(pos, definition.block());
        return tile(helper, pos, PipeBlockEntity.class);
    }

    // ---- power ---------------------------------------------------------------------------------

    /** 1 MJ = 10 Forge Energy / Team Reborn Energy units, through a machine's energy port. */
    public static void mjConversionRatio(GameTestHelper helper) {
        require(helper, Power.UNITS_PER_MJ == 10 && Power.toUnits(Power.MJ) == 10 && Power.fromUnits(10) == Power.MJ,
                "1 MJ must equal 10 energy units");
        require(helper, Power.toUnits(Power.MJ / 20) == 0 && Power.toUnits(Power.MJ / 10) == 1, "units must round down");
        MiningWellBlockEntity well = idleMiningWell(helper, new BlockPos(1, 1, 1));
        long accepted = well.energyPort(Direction.UP).receive(100, false);
        require(helper, accepted == 100, "mining well must accept 100 units, accepted " + accepted);
        require(helper, well.battery().stored() == 10 * Power.MJ, "100 units must store 10 MJ, stored " + well.battery().stored());
        long capacity = well.energyPort(Direction.UP).capacity();
        require(helper, capacity == 5_000, "the 500 MJ mining well buffer must report 5000 units, reported " + capacity);
        helper.succeed();
    }

    /** The shared viscosity scale is Forge's (water = 1000) on both loaders, which the pump and quarry compare against. */
    public static void fluidViscosityScale(GameTestHelper helper) {
        requireViscosity(helper, Fluids.WATER, 1000);
        requireViscosity(helper, Fluids.FLOWING_WATER, 1000);
        requireViscosity(helper, Fluids.LAVA, 6000);
        requireViscosity(helper, BCEnergy.OIL.heat(0).source(), 2000);
        requireViscosity(helper, BCEnergy.FUEL_LIGHT.heat(0).source(), 600);
        helper.succeed();
    }

    private static void requireViscosity(GameTestHelper helper, Fluid fluid, int expected) {
        int actual = Platform.get().viscosity(fluid);
        require(helper, actual == expected, Registry.FLUID.getKey(fluid) + " viscosity must be " + expected + ", got " + actual);
    }

    /**
     * A gas source in a sealed stone box rises to the ceiling and spreads under it but never along the
     * floor, while a liquid source on the roof flows down as usual.
     */
    public static void gaseousFluidsFlowUp(GameTestHelper helper) {
        require(helper, BCEnergy.FUEL_GASEOUS.heat(0).isGaseous() && BCEnergy.FUEL_LIGHT.heat(1).isGaseous()
                && !BCEnergy.FUEL_LIGHT.heat(0).isGaseous() && !BCEnergy.OIL.heat(2).isGaseous(), "gases are the stages at or above their boiling heat");
        for (int x = 1; x <= 7; x++) {
            for (int z = 1; z <= 7; z++) {
                helper.setBlock(new BlockPos(x, 4, z), Blocks.STONE);
                if (x == 1 || x == 7 || z == 1 || z == 7) {
                    for (int y = 1; y <= 3; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                }
            }
        }
        Fluid gas = BCEnergy.FUEL_GASEOUS.heat(0).source();
        Fluid oil = BCEnergy.OIL.heat(0).source();
        helper.setBlock(new BlockPos(4, 1, 4), gas.defaultFluidState().createLegacyBlock());
        helper.setBlock(new BlockPos(4, 6, 4), oil.defaultFluidState().createLegacyBlock());
        helper.succeedWhen(() -> {
            requireFluid(helper, new BlockPos(4, 2, 4), gas, true);
            requireFluid(helper, new BlockPos(4, 3, 4), gas, true);
            requireFluid(helper, new BlockPos(2, 3, 4), gas, true);
            requireFluid(helper, new BlockPos(6, 3, 6), gas, true);
            requireFluid(helper, new BlockPos(5, 1, 4), gas, false);
            requireFluid(helper, new BlockPos(2, 2, 4), gas, false);
            requireFluid(helper, new BlockPos(4, 5, 4), oil, true);
            requireFluid(helper, new BlockPos(4, 7, 4), oil, false);
            require(helper, helper.getLevel().getFluidState(helper.absolutePos(new BlockPos(4, 2, 4))).getValue(BCFluid.FALLING),
                    "a rising gas column must be marked as moving vertically");
        });
    }

    private static void requireFluid(GameTestHelper helper, BlockPos pos, Fluid fluid, boolean present) {
        boolean found = helper.getLevel().getFluidState(helper.absolutePos(pos)).getType().isSame(fluid);
        require(helper, found == present, Registry.FLUID.getKey(fluid) + (present ? " must reach " : " must not reach ") + pos);
    }

    // ---- engines -------------------------------------------------------------------------------

    /** A powered redstone engine pulses a wooden pipe, which pulls items out of a chest into the pipe network. */
    public static void redstoneEngineDrivesWoodenPipe(GameTestHelper helper) {
        ChestBlockEntity source = chest(helper, new BlockPos(0, 1, 1), new ItemStack(Items.COBBLESTONE, 8));
        PipeBlockEntity wood = pipe(helper, new BlockPos(1, 1, 1), BCTransport.WOOD_ITEM);
        pipe(helper, new BlockPos(2, 1, 1), BCTransport.STONE_ITEM);
        pipe(helper, new BlockPos(3, 1, 1), BCTransport.GOLD_ITEM);
        ChestBlockEntity target = chest(helper, new BlockPos(4, 1, 1));
        EngineBlockEntity engine = engine(helper, new BlockPos(1, 2, 1), BCEnergy.REDSTONE_ENGINE.get(), Direction.DOWN, EngineBlockEntity.class);
        helper.setBlock(new BlockPos(1, 3, 1), Blocks.REDSTONE_BLOCK);
        helper.succeedWhen(() -> {
            require(helper, engine.getPowerStage() == PowerStage.BLUE || engine.getPowerStage() == PowerStage.GREEN,
                    "a freshly powered redstone engine must still be cool, got " + engine.getPowerStage());
            require(helper, count(target, Items.COBBLESTONE) >= 1, "cobblestone must reach the far chest; source has "
                    + count(source, Items.COBBLESTONE) + ", engine pumping " + engine.isPumping() + " at " + engine.getPowerStage()
                    + ", wooden pipe faces " + ((DirectionalBehaviour) wood.behaviour()).currentDir()
                    + ", items in pipes " + ((ItemFlow) wood.flow()).itemCount());
            require(helper, count(source, Items.COBBLESTONE) + count(target, Items.COBBLESTONE) <= 8, "items must not duplicate");
        });
    }

    /** A Stirling engine burns coal while powered by redstone and delivers MJ into the machine it faces. */
    public static void stirlingEngineBurnsCoal(GameTestHelper helper) {
        StirlingEngineBlockEntity engine = engine(helper, new BlockPos(1, 1, 1), BCEnergy.STIRLING_ENGINE.get(), Direction.EAST,
                StirlingEngineBlockEntity.class);
        engine.setItem(0, new ItemStack(Items.COAL));
        MiningWellBlockEntity well = idleMiningWell(helper, new BlockPos(2, 1, 1));
        helper.setBlock(new BlockPos(0, 1, 1), Blocks.REDSTONE_BLOCK);
        helper.succeedWhen(() -> {
            require(helper, engine.getItem(0).isEmpty(), "the coal must be consumed");
            require(helper, engine.isBurning(), "the engine must be burning");
            require(helper, well.battery().stored() > 0, "the mining well must receive power");
        });
    }

    /** Fuel and coolant fill separate tanks; burning fuel powers the receiver. */
    public static void combustionEngineBurnsFuel(GameTestHelper helper) {
        CombustionEngineBlockEntity engine = engine(helper, new BlockPos(1, 1, 1), BCEnergy.COMBUSTION_ENGINE.get(), Direction.EAST,
                CombustionEngineBlockEntity.class);
        FluidPort port = engine.fluidPort(Direction.NORTH);
        require(helper, port.fill(BCEnergy.FUEL_LIGHT.heat(0).source(), 1000, false) == 1000, "light fuel must fill the fuel tank");
        require(helper, port.fill(Fluids.WATER, 1000, false) == 1000, "water must fill the coolant tank");
        require(helper, port.fill(Fluids.LAVA, 1000, true) == 0, "lava is neither fuel nor coolant");
        require(helper, engine.fuelTank().amount() == 1000 && engine.coolantTank().amount() == 1000, "fuel and coolant must be separate");
        MiningWellBlockEntity well = idleMiningWell(helper, new BlockPos(2, 1, 1));
        helper.setBlock(new BlockPos(0, 1, 1), Blocks.REDSTONE_BLOCK);
        helper.succeedWhen(() -> {
            require(helper, engine.fuelTank().amount() < 1000, "fuel must burn");
            require(helper, well.battery().stored() > 0, "the mining well must receive power");
        });
    }

    /** Sets an engine's heat the way {@code /data merge block} would, through its saved NBT. */
    static void presetHeat(EngineBlockEntity engine, double heat) {
        CompoundTag tag = engine.saveWithoutMetadata();
        tag.putDouble("heat", heat);
        engine.load(tag);
    }

    /** The block state's stage, which selects the trunk texture, follows upstream's heat thresholds. */
    public static void engineTrunkFollowsHeat(GameTestHelper helper) {
        double[] heats = {30, 100, 160, 200, 240};
        PowerStage[] stages = {PowerStage.BLUE, PowerStage.GREEN, PowerStage.YELLOW, PowerStage.RED, PowerStage.OVERHEAT};
        for (int i = 0; i < heats.length; i++) {
            presetHeat(engine(helper, new BlockPos(i + 1, 1, 1), BCEnergy.COMBUSTION_ENGINE.get(), Direction.UP,
                    CombustionEngineBlockEntity.class), heats[i]);
        }
        helper.runAfterDelay(5, () -> {
            for (int i = 0; i < heats.length; i++) {
                BlockPos pos = new BlockPos(i + 1, 1, 1);
                PowerStage stage = helper.getBlockState(pos).getValue(EngineBlock.STAGE);
                require(helper, stage == stages[i], "heat " + heats[i] + " must show the " + stages[i] + " trunk, got " + stage);
            }
            helper.succeed();
        });
    }

    /** A powered redstone engine warms up through the stages but stops short of overheating, as upstream. */
    public static void redstoneEngineWarmsButNeverOverheats(GameTestHelper helper) {
        EngineBlockEntity cold = engine(helper, new BlockPos(1, 1, 1), BCEnergy.REDSTONE_ENGINE.get(), Direction.UP, EngineBlockEntity.class);
        EngineBlockEntity hot = engine(helper, new BlockPos(3, 1, 1), BCEnergy.REDSTONE_ENGINE.get(), Direction.UP, EngineBlockEntity.class);
        helper.setBlock(new BlockPos(1, 1, 2), Blocks.REDSTONE_BLOCK);
        helper.setBlock(new BlockPos(3, 1, 2), Blocks.REDSTONE_BLOCK);
        presetHeat(hot, 195);
        helper.onEachTick(() -> require(helper, hot.getPowerStage() != PowerStage.OVERHEAT, "a redstone engine must never overheat, heat " + hot.getHeat()));
        helper.runAtTickTime(400, () -> {
            require(helper, cold.getHeat() > EngineBlockEntity.MIN_HEAT + 10, "a powered redstone engine must warm up, heat " + cold.getHeat());
            require(helper, hot.getPowerStage() == PowerStage.RED, "a hot redstone engine must settle at red, got " + hot.getPowerStage()
                    + " at heat " + hot.getHeat());
            helper.succeed();
        });
    }

    // ---- factory -------------------------------------------------------------------------------

    /** Fluid poured into the top of a tank column settles into the lowest tank first. */
    public static void tankColumnFillsFromTheBottom(GameTestHelper helper) {
        for (int y = 1; y <= 3; y++) helper.setBlock(new BlockPos(1, y, 1), BCFactory.TANK.get());
        TankBlockEntity bottom = tile(helper, new BlockPos(1, 1, 1), TankBlockEntity.class);
        TankBlockEntity middle = tile(helper, new BlockPos(1, 2, 1), TankBlockEntity.class);
        TankBlockEntity top = tile(helper, new BlockPos(1, 3, 1), TankBlockEntity.class);
        long filled = top.fluidPort(Direction.UP).fill(Fluids.WATER, 20_000, false);
        require(helper, filled == 20_000, "a three-tank column must accept 20000 mB, accepted " + filled);
        require(helper, top.fluidPort(Direction.UP).fill(Fluids.LAVA, 1000, true) == 0, "a tank must not mix fluids");
        helper.succeedWhen(() -> {
            require(helper, bottom.tank().amount() == TankBlockEntity.CAPACITY, "bottom tank must be full, has " + bottom.tank().amount());
            require(helper, middle.tank().amount() == 4_000, "middle tank must hold the rest, has " + middle.tank().amount());
            require(helper, top.tank().amount() == 0, "top tank must be empty, has " + top.tank().amount());
        });
    }

    /** The pump lowers its tube to a fluid source, drains it and pushes the fluid into an adjacent tank. */
    public static void pumpDrainsSourceIntoTank(GameTestHelper helper) {
        BlockPos source = new BlockPos(1, 1, 1);
        for (Direction side : Direction.Plane.HORIZONTAL) helper.setBlock(source.relative(side), Blocks.STONE);
        helper.setBlock(source, Blocks.WATER);
        helper.setBlock(new BlockPos(1, 4, 1), BCFactory.PUMP.get());
        fullyPowered(helper, tile(helper, new BlockPos(1, 4, 1), PumpBlockEntity.class));
        helper.setBlock(new BlockPos(2, 4, 1), BCFactory.TANK.get());
        TankBlockEntity tank = tile(helper, new BlockPos(2, 4, 1), TankBlockEntity.class);
        helper.succeedWhen(() -> {
            require(helper, !helper.getBlockState(source).is(Blocks.WATER), "the water source must be drained");
            require(helper, tank.tank().fluid() == Fluids.WATER && tank.tank().amount() == 1000,
                    "the tank must receive 1000 mB of water, has " + tank.tank().amount());
        });
    }

    /**
     * An L-shaped pool whose corner drains into a walled hole: the corner has two horizontal sources, but falling water
     * below it is neither solid nor a source, so vanilla never refills it and the pool is finite. The scan reaches (2,2,2)
     * after rejecting the stone at (1,2,2) beside the first cell; that visited stone must not count as water.
     */
    public static void pumpDrainsIrregularFinitePool(GameTestHelper helper) {
        BlockPos[] pool = {new BlockPos(1, 2, 1), new BlockPos(2, 2, 1), new BlockPos(2, 2, 2)};
        PumpBlockEntity pump = pumpOverPool(helper, pool);
        BlockPos hole = new BlockPos(2, 1, 1);
        helper.setBlock(hole.below(), Blocks.STONE);
        for (Direction side : Direction.Plane.HORIZONTAL) helper.setBlock(hole.relative(side), Blocks.STONE);
        helper.setBlock(hole, Blocks.AIR);
        TankBlockEntity tank = tile(helper, pool[0].above(3).north(), TankBlockEntity.class);
        helper.succeedWhen(() -> {
            require(helper, !pump.isInfiniteWaterSource(), "an L-shaped pool must not be an infinite water source");
            require(helper, tank.tank().amount() <= 3000, "the pump must not create water, the tank has " + tank.tank().amount());
            for (BlockPos cell : pool) require(helper, !helper.getBlockState(cell).is(Blocks.WATER), "water left at " + cell);
            require(helper, tank.tank().amount() == 3000, "the tank must receive all 3000 mB, has " + tank.tank().amount());
        });
    }

    /** A 2x2 pool refills itself in vanilla, so the pump fills its tank without removing the sources. */
    public static void pumpTreatsSquarePoolAsInfinite(GameTestHelper helper) {
        BlockPos[] pool = {new BlockPos(1, 1, 1), new BlockPos(2, 1, 1), new BlockPos(1, 1, 2), new BlockPos(2, 1, 2)};
        PumpBlockEntity pump = pumpOverPool(helper, pool);
        TankBlockEntity tank = tile(helper, pool[0].above(3).north(), TankBlockEntity.class);
        helper.succeedWhen(() -> {
            require(helper, pump.isInfiniteWaterSource(), "a 2x2 pool must be an infinite water source");
            for (BlockPos cell : pool) require(helper, helper.getBlockState(cell).getFluidState().isSource(), "source drained at " + cell);
            require(helper, tank.tank().amount() > 4000, "the pump must keep filling, the tank has " + tank.tank().amount());
        });
    }

    /** Water sources at {@code pool} walled and floored with stone, a powered pump over the first cell and a tank beside the pump. */
    private static PumpBlockEntity pumpOverPool(GameTestHelper helper, BlockPos... pool) {
        Set<BlockPos> cells = Set.of(pool);
        for (BlockPos cell : pool) {
            helper.setBlock(cell.below(), Blocks.STONE);
            for (Direction side : Direction.Plane.HORIZONTAL) {
                if (!cells.contains(cell.relative(side))) helper.setBlock(cell.relative(side), Blocks.STONE);
            }
        }
        for (BlockPos cell : pool) helper.setBlock(cell, Blocks.WATER);
        BlockPos pumpPos = pool[0].above(3);
        helper.setBlock(pumpPos, BCFactory.PUMP.get());
        helper.setBlock(pumpPos.north(), BCFactory.TANK.get());
        return fullyPowered(helper, tile(helper, pumpPos, PumpBlockEntity.class));
    }

    /** The mining well breaks the block under its tube and hands the drop to an adjacent chest. */
    public static void miningWellMinesIntoChest(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 0, 1), Blocks.BEDROCK);
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.COBBLESTONE);
        helper.setBlock(new BlockPos(1, 2, 1), BCFactory.MINING_WELL.get());
        fullyPowered(helper, tile(helper, new BlockPos(1, 2, 1), MiningWellBlockEntity.class));
        ChestBlockEntity chest = chest(helper, new BlockPos(2, 2, 1));
        helper.succeedWhen(() -> {
            require(helper, helper.getBlockState(new BlockPos(1, 1, 1)).isAir(), "the cobblestone must be mined");
            require(helper, count(chest, Items.COBBLESTONE) == 1, "the chest must receive the cobblestone");
            require(helper, helper.getBlockState(new BlockPos(1, 0, 1)).is(Blocks.BEDROCK), "bedrock must stop the well");
        });
    }

    /** The quarry clears its area, builds the frame along every edge and then digs inside it. */
    public static void quarryBuildsFrameAndDigs(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 1, 2), BCBuilders.QUARRY.get());
        QuarryBlockEntity quarry = tile(helper, new BlockPos(0, 1, 2), QuarryBlockEntity.class);
        helper.setBlock(new BlockPos(2, 0, 2), Blocks.BEDROCK);
        helper.setBlock(new BlockPos(2, 2, 2), Blocks.DIRT);
        helper.setBlock(new BlockPos(2, 3, 2), Blocks.COBBLESTONE);
        quarry.setArea(helper.absolutePos(new BlockPos(1, 1, 1)), helper.absolutePos(new BlockPos(3, 5, 3)));
        helper.onEachTick(() -> quarry.battery().set(quarry.battery().capacity()));
        List<BlockPos> corners = List.of(new BlockPos(1, 1, 1), new BlockPos(3, 1, 3), new BlockPos(1, 5, 3), new BlockPos(3, 5, 1),
                new BlockPos(2, 5, 1), new BlockPos(1, 3, 1));
        helper.succeedWhen(() -> {
            require(helper, quarry.isFrameComplete(), "the frame must be complete");
            for (BlockPos corner : corners) {
                require(helper, helper.getBlockState(corner).is(BCBuilders.FRAME.get()), "frame missing at " + corner);
            }
            require(helper, helper.getBlockState(new BlockPos(2, 2, 2)).isAir() && helper.getBlockState(new BlockPos(2, 3, 2)).isAir(),
                    "blocks inside the frame must be cleared");
            require(helper, helper.getBlockState(new BlockPos(2, 1, 2)).isAir(), "the quarry must dig inside the frame");
        });
    }

    // ---- markers -------------------------------------------------------------------------------

    static <T extends BlockEntity> T marker(GameTestHelper helper, BlockPos pos, MarkerBlock block, Class<T> type) {
        helper.setBlock(pos, block.defaultBlockState().setValue(MarkerBlock.FACING, Direction.UP));
        return tile(helper, pos, type);
    }

    static Set<BlockPos> absolute(GameTestHelper helper, BlockPos... positions) {
        Set<BlockPos> set = new HashSet<>();
        for (BlockPos p : positions) set.add(helper.absolutePos(p));
        return set;
    }

    /**
     * Using a land mark connects it to the nearest mark along each free axis and takes in marks on the new
     * box's corners; breaking marks shrinks the box, and redstone shows the signal lasers. The connection
     * survives a save and reaches clients through the marks' block entity data.
     */
    public static void landMarksConnectIntoBox(GameTestHelper helper) {
        BlockPos a = new BlockPos(5, 1, 5), b = new BlockPos(1, 1, 5), c = new BlockPos(5, 1, 1), d = new BlockPos(1, 1, 1);
        MarkerVolumeBlockEntity markA = marker(helper, a, BCCore.MARKER_VOLUME.get(), MarkerVolumeBlockEntity.class);
        MarkerVolumeBlockEntity markB = marker(helper, b, BCCore.MARKER_VOLUME.get(), MarkerVolumeBlockEntity.class);
        marker(helper, c, BCCore.MARKER_VOLUME.get(), MarkerVolumeBlockEntity.class);
        marker(helper, d, BCCore.MARKER_VOLUME.get(), MarkerVolumeBlockEntity.class);
        require(helper, markA.getCurrentConnection() == null, "marks must not connect until used");

        markA.onManualConnectionAttempt();
        VolumeConnection box = markA.getCurrentConnection();
        require(helper, box != null && new HashSet<>(box.getMarkerPositions()).equals(absolute(helper, a, b, c, d)),
                "using a mark must connect it along both free axes and take in the mark on the far corner, got " + (box == null ? null : box.getMarkerPositions()));
        require(helper, markB.getCurrentConnection() == box, "every mark must share the connection");
        require(helper, markA.min().equals(helper.absolutePos(d)) && markA.max().equals(helper.absolutePos(a)), "the area must span the marks");
        require(helper, box.getConnectedAxis().equals(EnumSet.of(Direction.Axis.X, Direction.Axis.Z)), "the box must use the X and Z axes");

        VolumeSubCache cache = MarkerCaches.VOLUME.getSubCache(helper.getLevel());
        VolumeSubCache saved = new VolumeSubCache(helper.getLevel());
        saved.load(cache.save(new CompoundTag()));
        VolumeConnection reloaded = saved.getConnection(helper.absolutePos(a));
        require(helper, reloaded != null && new HashSet<>(reloaded.getMarkerPositions()).equals(absolute(helper, a, b, c, d)), "the box must survive a save");
        require(helper, saved.hasLoadedOrUnloadedMarker(helper.absolutePos(b)) && saved.getMarker(helper.absolutePos(b)) == null,
                "saved marks must start unloaded");

        VolumeSubCache client = new VolumeSubCache(helper.getLevel());
        CompoundTag sync = markA.getUpdateTag();
        client.receiveConnection(helper.absolutePos(a), MarkerSubCache.readPositions(sync.getList("connection", Tag.TAG_COMPOUND)));
        require(helper, client.getConnection(helper.absolutePos(c)) != null && client.getConnection(helper.absolutePos(c)).getMarkerPositions().size() == 4,
                "the mark's update data must carry its connection");

        helper.setBlock(d, Blocks.AIR);
        require(helper, box.getMarkerPositions().size() == 3 && markA.getCurrentConnection() == box, "breaking a corner mark must leave three");
        client.receiveConnection(helper.absolutePos(a), MarkerSubCache.readPositions(markA.getUpdateTag().getList("connection", Tag.TAG_COMPOUND)));
        require(helper, client.getConnection(helper.absolutePos(d)) == null && client.getConnection(helper.absolutePos(a)).getMarkerPositions().size() == 3,
                "clients must follow the smaller box");
        helper.setBlock(c, Blocks.AIR);
        helper.setBlock(b, Blocks.AIR);
        require(helper, markA.getCurrentConnection() == null, "a lone mark has no connection");
        require(helper, !cache.hasLoadedOrUnloadedMarker(helper.absolutePos(b)), "broken marks must leave the cache");

        require(helper, !markA.isShowingSignals(), "an unpowered mark shows no signals");
        helper.setBlock(a.east(), Blocks.REDSTONE_BLOCK);
        require(helper, markA.isShowingSignals(), "a powered mark must show signals");
        helper.setBlock(a.east(), Blocks.AIR);
        require(helper, !markA.isShowingSignals(), "the signals must stop with the power");
        helper.succeed();
    }

    /**
     * Path marks join end to end into a path that can close into a loop and be reversed; breaking a middle
     * mark splits the path, and lone marks drop out.
     */
    public static void pathMarksFormPathsAndLoops(GameTestHelper helper) {
        BlockPos p1 = new BlockPos(1, 1, 1), p2 = new BlockPos(4, 1, 1), p3 = new BlockPos(4, 1, 4), p4 = new BlockPos(1, 1, 4);
        MarkerPathBlockEntity first = marker(helper, p1, BCCore.MARKER_PATH.get(), MarkerPathBlockEntity.class);
        for (BlockPos p : List.of(p2, p3, p4)) marker(helper, p, BCCore.MARKER_PATH.get(), MarkerPathBlockEntity.class);
        PathSubCache cache = MarkerCaches.PATH.getSubCache(helper.getLevel());
        BlockPos a1 = helper.absolutePos(p1), a2 = helper.absolutePos(p2), a3 = helper.absolutePos(p3), a4 = helper.absolutePos(p4);
        require(helper, cache.getValidConnections(a1).containsAll(List.of(a2, a3, a4)), "path marks connect to any mark in range");
        require(helper, cache.tryConnect(a1, a2) && cache.tryConnect(a2, a3) && cache.tryConnect(a3, a4), "the path must extend from its end");
        require(helper, first.getPath().equals(List.of(a1, a2, a3, a4)), "the path must keep its order, got " + first.getPath());
        require(helper, !cache.canConnect(a2, a4), "a middle mark cannot join");
        require(helper, cache.tryConnect(a4, a1), "joining the ends must close the loop");
        require(helper, first.getPath().equals(List.of(a1, a2, a3, a4, a1)), "a loop lists its start again at the end, got " + first.getPath());

        PathSubCache saved = new PathSubCache(helper.getLevel());
        saved.load(cache.save(new CompoundTag()));
        require(helper, saved.getConnection(a3) != null && saved.getConnection(a3).isLoop() && saved.getConnection(a3).getMarkerPositions().equals(first.getPath()),
                "the loop must survive a save");

        first.reverseDirection();
        require(helper, first.getPath().equals(List.of(a4, a3, a2, a1, a4)), "using a path mark must reverse the path, got " + first.getPath());
        helper.setBlock(p2, Blocks.AIR);
        require(helper, first.getPath().isEmpty() && cache.getConnection(a4) != null && cache.getConnection(a4).getMarkerPositions().equals(List.of(a4, a3)),
                "breaking a middle mark must split the path and drop the lone end, got " + cache.getConnection(a4));
        helper.succeed();
    }

    /** Using the marker connector while looking along the line between two marks connects them. */
    public static void markerConnectorJoinsMarks(GameTestHelper helper) {
        MarkerVolumeBlockEntity west = marker(helper, new BlockPos(1, 1, 4), BCCore.MARKER_VOLUME.get(), MarkerVolumeBlockEntity.class);
        marker(helper, new BlockPos(5, 1, 4), BCCore.MARKER_VOLUME.get(), MarkerVolumeBlockEntity.class);
        Player player = helper.makeMockPlayer();
        Vec3 eyes = Vec3.atCenterOf(helper.absolutePos(new BlockPos(3, 2, 2)));
        player.moveTo(eyes.x, eyes.y - player.getEyeHeight(), eyes.z, 0, 0);
        ItemStack connector = new ItemStack(BCCore.MARKER_CONNECTOR.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, connector);
        InteractionResult missed = connector.use(helper.getLevel(), player, InteractionHand.MAIN_HAND).getResult();
        require(helper, west.getCurrentConnection() == null && !missed.consumesAction(), "looking over the line must not connect the marks");
        player.moveTo(eyes.x, eyes.y - player.getEyeHeight() - 0.9, eyes.z + 0.5, 0, 0);
        InteractionResult connected = connector.use(helper.getLevel(), player, InteractionHand.MAIN_HAND).getResult();
        require(helper, west.getCurrentConnection() != null && west.getCurrentConnection().getMarkerPositions().size() == 2,
                "looking along the line must connect the marks");
        require(helper, connected.consumesAction(), "connecting marks must consume the click so it does not reach the other hand");
        helper.succeed();
    }

    /** Puts the mock player's eyes at the centre of {@code eyeBlock}, looking south along +Z. */
    static void lookSouthFrom(GameTestHelper helper, Player player, BlockPos eyeBlock) {
        Vec3 eyes = Vec3.atCenterOf(helper.absolutePos(eyeBlock));
        player.moveTo(eyes.x, eyes.y - player.getEyeHeight(), eyes.z, 0, 0);
    }

    static VolumeBox placeVolumeBox(GameTestHelper helper, Player player, BlockPos floor) {
        ItemStack stack = new ItemStack(BCCore.VOLUME_BOX.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos clicked = helper.absolutePos(floor);
        UseOnContext context = new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(clicked).add(0, 0.5, 0), Direction.UP, clicked, false));
        InteractionResult result = stack.useOn(context);
        VolumeBox volumeBox = VolumeBoxes.get(helper.getLevel()).getVolumeBoxAt(clicked.above());
        require(helper, result.consumesAction() && volumeBox != null, "the volume box item must make a box against the clicked face, got " + result);
        return volumeBox;
    }

    static boolean useConnector(GameTestHelper helper, Player player, boolean sneaking) {
        ItemStack connector = new ItemStack(BCCore.MARKER_CONNECTOR.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, connector);
        player.setShiftKeyDown(sneaking);
        return connector.use(helper.getLevel(), player, InteractionHand.MAIN_HAND).getResult().consumesAction();
    }

    static void requireBox(GameTestHelper helper, VolumeBox volumeBox, BlockPos min, BlockPos max, String message) {
        BlockPos absMin = helper.absolutePos(min), absMax = helper.absolutePos(max);
        require(helper, volumeBox.box.min().equals(absMin) && volumeBox.box.max().equals(absMax),
                message + ": expected " + absMin + " to " + absMax + ", got " + volumeBox.box.min() + " to " + volumeBox.box.max());
    }

    /** The volume box item makes a one-block box against the clicked face, once; boxes and their locks survive saving and syncing. */
    public static void volumeBoxItemMakesBoxes(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        VolumeBox volumeBox = placeVolumeBox(helper, player, new BlockPos(4, 1, 4));
        requireBox(helper, volumeBox, new BlockPos(4, 2, 4), new BlockPos(4, 2, 4), "the new box must be the block above the clicked floor");
        ItemStack stack = player.getMainHandItem();
        BlockPos clicked = helper.absolutePos(new BlockPos(4, 1, 4));
        InteractionResult again = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(clicked), Direction.UP, clicked, false)));
        require(helper, again == InteractionResult.FAIL, "a second box must not be made inside the first, got " + again);

        VolumeBoxes volumeBoxes = VolumeBoxes.get(helper.getLevel());
        BlockPos cause = helper.absolutePos(new BlockPos(1, 1, 1));
        volumeBox.box.extendToEncompass(helper.absolutePos(new BlockPos(6, 4, 7)));
        volumeBox.locks.add(new Lock(new Lock.CauseBlock(cause, Blocks.STONE), new Lock.Remove(), new Lock.Resize(),
                new Lock.UsedByMachine(Lock.MachineUse.STRIPES_WRITE)));
        VolumeBox loaded = VolumeBoxes.load(helper.getLevel(), volumeBoxes.save(new CompoundTag())).getVolumeBoxFromId(volumeBox.id);
        require(helper, loaded != null, "saving must keep the box");
        requireBox(helper, loaded, new BlockPos(4, 2, 4), new BlockPos(6, 4, 7), "saving must keep the box's extent");
        require(helper, loaded.isLockedFor(Lock.Resize.class) && loaded.isLockedFor(Lock.Remove.class)
                && loaded.locks.get(0).cause.equals(new Lock.CauseBlock(cause, Blocks.STONE)), "saving must keep the box's lock");
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        volumeBox.write(buffer);
        VolumeBox synced = VolumeBox.read(buffer);
        require(helper, synced.equals(volumeBox) && synced.box.min().equals(volumeBox.box.min()) && synced.box.max().equals(volumeBox.box.max())
                && synced.getLockTargetsStream().anyMatch(new Lock.UsedByMachine(Lock.MachineUse.STRIPES_WRITE)::equals),
                "syncing must send the box and the machine that holds it");
        volumeBoxes.volumeBoxes.remove(volumeBox);
        helper.succeed();
    }

    /**
     * Using the connector on a box's corner grabs it: the opposite corner stays and the grabbed one follows
     * the player's gaze until the next click confirms it. Sneaking cancels an edit, or removes the box.
     */
    public static void markerConnectorEditsVolumeBoxes(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        VolumeBoxes volumeBoxes = VolumeBoxes.get(helper.getLevel());
        Function<UUID, Player> players = id -> id.equals(player.getGameProfile().getId()) ? player : null;
        VolumeBox volumeBox = placeVolumeBox(helper, player, new BlockPos(4, 1, 4));
        lookSouthFrom(helper, player, new BlockPos(4, 2, -1));
        require(helper, !useConnector(helper, player, false) && !volumeBox.isEditing(), "a box beyond reach must not be grabbed");

        lookSouthFrom(helper, player, new BlockPos(4, 2, 2));
        require(helper, useConnector(helper, player, false) && volumeBox.isEditingBy(player), "looking at a corner within reach must grab it");
        require(helper, volumeBox.getHeld().equals(helper.absolutePos(new BlockPos(4, 2, 4))) && volumeBox.getDist() == 2,
                "a one-block box must hold its own block, at 2 blocks: the hit at 1.5 plus half a block, got " + volumeBox.getHeld() + " at " + volumeBox.getDist());
        lookSouthFrom(helper, player, new BlockPos(4, 2, 5));
        volumeBoxes.tick(players);
        requireBox(helper, volumeBox, new BlockPos(4, 2, 4), new BlockPos(4, 2, 7), "the grabbed corner must follow the player's gaze");
        require(helper, useConnector(helper, player, false) && !volumeBox.isEditing(), "the next click must confirm the edit");
        volumeBoxes.tick(players);
        requireBox(helper, volumeBox, new BlockPos(4, 2, 4), new BlockPos(4, 2, 7), "a confirmed box must keep its new extent");

        lookSouthFrom(helper, player, new BlockPos(4, 2, 2));
        require(helper, useConnector(helper, player, false) && volumeBox.getHeld().equals(helper.absolutePos(new BlockPos(4, 2, 7))),
                "grabbing the near corner must hold the far one, got " + volumeBox.getHeld());
        lookSouthFrom(helper, player, new BlockPos(4, 4, 1));
        volumeBoxes.tick(players);
        requireBox(helper, volumeBox, new BlockPos(4, 2, 3), new BlockPos(4, 4, 7), "the box must stretch to the gaze on every axis");
        volumeBoxes.tick(id -> null);
        require(helper, !volumeBox.isEditing() && volumeBox.isPausedEditingBy(player), "an edit must pause while its player is away");
        volumeBox.resumeEditing();
        require(helper, useConnector(helper, player, true) && !volumeBox.isEditing(), "sneaking must cancel the edit");
        requireBox(helper, volumeBox, new BlockPos(4, 2, 4), new BlockPos(4, 2, 7), "cancelling must restore the box");

        lookSouthFrom(helper, player, new BlockPos(4, 2, 2));
        require(helper, useConnector(helper, player, false) && volumeBox.isEditingBy(player), "the box must be grabbable again");
        volumeBoxes.tick(id -> null);
        Player other = helper.makeMockPlayer();
        lookSouthFrom(helper, other, new BlockPos(4, 2, 2));
        require(helper, useConnector(helper, other, false) && volumeBox.isEditingBy(other) && !volumeBox.isPausedEditingBy(player),
                "another player taking over a paused edit must end the absent player's claim");
        require(helper, useConnector(helper, other, false) && !volumeBox.isEditing(), "the new editor's click must confirm");

        lookSouthFrom(helper, player, new BlockPos(4, 2, 2));
        require(helper, useConnector(helper, player, true) && !volumeBoxes.volumeBoxes.contains(volumeBox), "sneaking at a box must remove it");
        helper.succeed();
    }

    /** A machine's lock keeps players from resizing or removing its box, until the machine's block is gone. */
    public static void lockedVolumeBoxesResistTheConnector(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        VolumeBoxes volumeBoxes = VolumeBoxes.get(helper.getLevel());
        VolumeBox volumeBox = placeVolumeBox(helper, player, new BlockPos(4, 1, 4));
        BlockPos cause = new BlockPos(7, 2, 7);
        helper.setBlock(cause, Blocks.GOLD_BLOCK);
        volumeBox.locks.add(new Lock(new Lock.CauseBlock(helper.absolutePos(cause), Blocks.GOLD_BLOCK), new Lock.Remove(), new Lock.Resize()));
        lookSouthFrom(helper, player, new BlockPos(4, 2, 2));
        require(helper, !useConnector(helper, player, false) && !volumeBox.isEditing(), "a locked box must not be grabbed");
        require(helper, !useConnector(helper, player, true) && volumeBoxes.volumeBoxes.contains(volumeBox), "a locked box must not be removed");
        volumeBoxes.tick();
        require(helper, !volumeBox.locks.isEmpty(), "the lock must hold while its block stays");
        helper.setBlock(cause, Blocks.AIR);
        volumeBoxes.tick();
        require(helper, volumeBox.locks.isEmpty(), "the lock must lapse once its block is gone");

        helper.setBlock(cause, Blocks.GOLD_BLOCK);
        volumeBox.locks.add(new Lock(new Lock.CauseBlock(helper.absolutePos(cause), Blocks.GOLD_BLOCK), new Lock.Remove()));
        require(helper, !useConnector(helper, player, true) && volumeBoxes.volumeBoxes.contains(volumeBox), "a removal lock alone must keep the box");
        helper.setBlock(cause, Blocks.AIR);
        volumeBoxes.tick();
        require(helper, useConnector(helper, player, true) && !volumeBoxes.volumeBoxes.contains(volumeBox), "an unlocked box must be removable");
        helper.succeed();
    }

    /** Sneaking with the wrench turns a mark to the next face it can stand on; path marks fall off without support, land marks stay. */
    public static void markersRotateAndNeedSupport(GameTestHelper helper) {
        BlockPos path = new BlockPos(4, 2, 4);
        marker(helper, path, BCCore.MARKER_PATH.get(), MarkerPathBlockEntity.class);
        helper.setBlock(path.east(), Blocks.STONE);
        BlockState state = helper.getBlockState(path);
        InteractionResult result = BCCore.MARKER_PATH.get().attemptRotation(helper.getLevel(), helper.absolutePos(path), state, Direction.UP);
        require(helper, result == InteractionResult.SUCCESS && helper.getBlockState(path).getValue(MarkerBlock.FACING) == Direction.WEST,
                "the wrench must turn the mark onto the stone beside it, got " + result + " and " + helper.getBlockState(path));
        helper.setBlock(path.east(), Blocks.AIR);
        require(helper, helper.getBlockState(path).isAir(), "a path mark must break without its support");

        BlockPos volume = new BlockPos(2, 3, 2);
        helper.setBlock(volume.below(), Blocks.STONE);
        marker(helper, volume, BCCore.MARKER_VOLUME.get(), MarkerVolumeBlockEntity.class);
        helper.setBlock(volume.below(), Blocks.AIR);
        require(helper, helper.getBlockState(volume).is(BCCore.MARKER_VOLUME.get()), "upstream land marks stay when their support goes");
        helper.succeed();
    }

    static void landMarkBox(GameTestHelper helper, BlockPos corner, BlockPos alongX, BlockPos alongZ) {
        marker(helper, corner, BCCore.MARKER_VOLUME.get(), MarkerVolumeBlockEntity.class);
        marker(helper, alongX, BCCore.MARKER_VOLUME.get(), MarkerVolumeBlockEntity.class);
        marker(helper, alongZ, BCCore.MARKER_VOLUME.get(), MarkerVolumeBlockEntity.class);
        VolumeSubCache cache = MarkerCaches.VOLUME.getSubCache(helper.getLevel());
        require(helper, cache.tryConnect(helper.absolutePos(corner), helper.absolutePos(alongX))
                && cache.tryConnect(helper.absolutePos(corner), helper.absolutePos(alongZ)), "the land marks must connect");
    }

    static QuarryBlockEntity placeQuarry(GameTestHelper helper, BlockPos pos, Direction facing) {
        BlockState state = BCBuilders.QUARRY.get().defaultBlockState().setValue(QuarryBlock.FACING, facing);
        helper.setBlock(pos, state);
        BCBuilders.QUARRY.get().setPlacedBy(helper.getLevel(), helper.absolutePos(pos), state, null, ItemStack.EMPTY);
        return tile(helper, pos, QuarryBlockEntity.class);
    }

    /** A quarry placed with its back against a land mark takes the marks' box as its area and removes the marks. */
    public static void quarryTakesLandMarkArea(GameTestHelper helper) {
        landMarkBox(helper, new BlockPos(2, 1, 1), new BlockPos(6, 1, 1), new BlockPos(2, 1, 5));
        QuarryBlockEntity quarry = placeQuarry(helper, new BlockPos(1, 1, 1), Direction.WEST);
        require(helper, quarry.frameBox.min().equals(helper.absolutePos(new BlockPos(2, 1, 1)))
                && quarry.frameBox.max().equals(helper.absolutePos(new BlockPos(6, 5, 5))),
                "the frame must cover the marks' box, raised to the minimum height, got " + quarry.frameBox.min() + " to " + quarry.frameBox.max());
        for (BlockPos p : List.of(new BlockPos(2, 1, 1), new BlockPos(6, 1, 1), new BlockPos(2, 1, 5))) {
            require(helper, helper.getBlockState(p).isAir(), "the quarry must remove the land mark at " + p);
        }
        require(helper, !helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(new BlockPos(8, 8, 8))),
                item -> item.getItem().is(BCCore.MARKER_VOLUME_ITEM.get())).isEmpty(), "removed land marks must drop");
        helper.succeed();
    }

    /** A quarry beside the bottom edge of a land mark box, facing into it, takes the box even away from the marks. */
    public static void quarryFindsLandMarkBoxEdge(GameTestHelper helper) {
        landMarkBox(helper, new BlockPos(2, 1, 1), new BlockPos(6, 1, 1), new BlockPos(2, 1, 5));
        QuarryBlockEntity quarry = placeQuarry(helper, new BlockPos(1, 1, 3), Direction.WEST);
        require(helper, quarry.frameBox.min().equals(helper.absolutePos(new BlockPos(2, 1, 1)))
                && quarry.frameBox.max().equals(helper.absolutePos(new BlockPos(6, 5, 5))),
                "the frame must cover the box the quarry faces, got " + quarry.frameBox.min() + " to " + quarry.frameBox.max());
        require(helper, helper.getBlockState(new BlockPos(6, 1, 1)).isAir(), "the quarry must remove the box's marks");
        helper.succeed();
    }

    // ---- oil and refining ----------------------------------------------------------------------

    /**
     * Wells are rolled from the world seed and chunk alone, at upstream's rates (0.04% large, 0.1% medium),
     * and the feature is added at the lakes step of overworld biomes on both loaders.
     */
    public static void oilWellsAreRareAndDeterministic(GameTestHelper helper) {
        long seed = 1234567L;
        int large = 0, medium = 0;
        for (int cx = -128; cx < 128; cx++) {
            for (int cz = -128; cz < 128; cz++) {
                OilWellFeature.Well well = OilWellFeature.well(seed, cx, cz, -64, pos -> false);
                OilWellFeature.Well again = OilWellFeature.well(seed, cx, cz, -64, pos -> false);
                require(helper, (well == null) == (again == null) && (well == null || well.centre().equals(again.centre())),
                        "the well in chunk " + cx + "," + cz + " must not depend on anything but the seed");
                if (well == null) continue;
                require(helper, well.centre().getX() >> 4 == cx && well.centre().getZ() >> 4 == cz, "a well must be centred in its own chunk");
                require(helper, well.centre().getY() >= 20 && well.centre().getY() < 30, "a well's sphere sits at y 20 to 29, got " + well.centre());
                if (well.type() == OilWellFeature.WellType.LARGE) {
                    large++;
                    require(helper, well.structures().size() == 5, "a large well has a lake, sphere, spout, tube and spring");
                } else {
                    medium++;
                    require(helper, well.structures().size() == 3, "a medium well has a lake, sphere and spout");
                }
            }
        }
        // 65536 chunks: about 26 large and 65 medium wells.
        require(helper, large >= 10 && large <= 50, "expected about 26 large wells, rolled " + large);
        require(helper, medium >= 35 && medium <= 100, "expected about 65 medium wells, rolled " + medium);

        ResourceKey<PlacedFeature> key = ResourceKey.create(Registry.PLACED_FEATURE_REGISTRY, BuildCraft.id("oil_well"));
        Holder<PlacedFeature> placed = helper.getLevel().registryAccess().registryOrThrow(Registry.PLACED_FEATURE_REGISTRY).getHolderOrThrow(key);
        Biome biome = helper.getLevel().getBiome(helper.absolutePos(BlockPos.ZERO)).value();
        List<HolderSet<PlacedFeature>> steps = biome.getGenerationSettings().features();
        int lakes = GenerationStep.Decoration.LAKES.ordinal();
        require(helper, steps.size() > lakes && steps.get(lakes).contains(placed), "the overworld biome must generate oil wells at the lakes step");
        helper.succeed();
    }

    /** Upstream's excessive biomes roll wells with thirty times the chance; other chunks roll the same wells as before. */
    public static void oilBiomesHaveThirtyTimesTheWells(GameTestHelper helper) {
        long seed = 1234567L;
        int large = 0, medium = 0, asked = 0, chunks = 0;
        for (int cx = -128; cx < 128; cx++) {
            for (int cz = -128; cz < 128; cz++) {
                chunks++;
                int[] queries = {0};
                int chunkX = cx, chunkZ = cz;
                OilWellFeature.Well rich = OilWellFeature.well(seed, cx, cz, -64, pos -> {
                    queries[0]++;
                    require(helper, pos.getX() >> 4 == chunkX && pos.getZ() >> 4 == chunkZ && pos.getY() == 62,
                            "the biome bonus must be read at the well's own column, got " + pos);
                    return true;
                });
                require(helper, queries[0] <= 1, "a chunk must read its biome at most once");
                asked += queries[0];
                OilWellFeature.Well plain = OilWellFeature.well(seed, cx, cz, -64, pos -> false);
                require(helper, plain == null || rich != null && rich.centre().getX() == plain.centre().getX()
                        && rich.centre().getZ() == plain.centre().getZ(), "an oil biome must keep every well that it would roll elsewhere");
                if (rich == null) continue;
                if (rich.type() == OilWellFeature.WellType.LARGE) large++;
                else medium++;
            }
        }
        // 65536 chunks: about 786 large wells (1.2%) and 1942 medium ones (3% of the rest).
        require(helper, large >= 700 && large <= 870, "expected about 786 large wells in oil biomes, rolled " + large);
        require(helper, medium >= 1800 && medium <= 2090, "expected about 1942 medium wells in oil biomes, rolled " + medium);
        require(helper, asked < chunks / 10, "the biome must only be read when a roll depends on it, read " + asked + " times");
        helper.succeed();
    }

    /** Upstream {@code GenLayerAddOilDesert} and {@code GenLayerAddOilOcean} over the overworld's multi-noise biomes. */
    public static void oilBiomesReplaceDesertsAndOceans(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        Registry<Biome> biomes = registries.registryOrThrow(Registry.BIOME_REGISTRY);
        MinecraftServer server = helper.getLevel().getServer();
        BiomeSource overworld = server.overworld().getChunkSource().getGenerator().getBiomeSource();
        BiomeSource nether = server.getLevel(Level.NETHER).getChunkSource().getGenerator().getBiomeSource();
        OilBiomes.State installed = OilBiomes.current();
        if (overworld instanceof MultiNoiseBiomeSource) {
            require(helper, installed != null && installed.seed() == helper.getLevel().getSeed() && installed.appliesTo(overworld)
                    && !installed.appliesTo(nether), "the server must install the oil biomes for its seed and overworld only");
        } else {
            require(helper, installed == null, "a world without a multi-noise overworld has no oil biomes");
        }
        Holder<Biome> oilDesert = biomes.getHolderOrThrow(OilBiomes.OIL_DESERT);
        Holder<Biome> oilOcean = biomes.getHolderOrThrow(OilBiomes.OIL_OCEAN);
        Biome desert = biomes.getOrThrow(Biomes.DESERT), ocean = biomes.getOrThrow(Biomes.OCEAN);
        require(helper, oilDesert.value().getBaseTemperature() == desert.getBaseTemperature() && oilDesert.value().getPrecipitation() == Biome.Precipitation.NONE
                && oilOcean.value().getBaseTemperature() == ocean.getBaseTemperature(), "the oil biomes must keep their parents' climates");
        require(helper, oilDesert.is(OilBiomes.EXCESSIVE_OIL) && oilOcean.is(OilBiomes.EXCESSIVE_OIL) && oilDesert.is(BiomeTags.IS_OVERWORLD)
                && oilOcean.is(BiomeTags.IS_OVERWORLD) && oilOcean.is(BiomeTags.IS_OCEAN), "the oil biomes must be tagged as overworld, ocean and excessive");
        require(helper, !oilDesert.is(BiomeTags.HAS_DESERT_PYRAMID) && !oilDesert.is(BiomeTags.HAS_VILLAGE_DESERT),
                "upstream's oil desert has no temples or villages");
        Holder<PlacedFeature> oilWell = registries.registryOrThrow(Registry.PLACED_FEATURE_REGISTRY)
                .getHolderOrThrow(ResourceKey.create(Registry.PLACED_FEATURE_REGISTRY, BuildCraft.id("oil_well")));
        for (Holder<Biome> oil : List.of(oilDesert, oilOcean)) {
            List<HolderSet<PlacedFeature>> steps = oil.value().getGenerationSettings().features();
            require(helper, steps.get(GenerationStep.Decoration.LAKES.ordinal()).contains(oilWell), "the oil biomes must generate oil wells");
        }
        require(helper, biomes.getHolderOrThrow(Biomes.DESERT).is(OilBiomes.DESERT_REPLACEABLE) && biomes.getHolderOrThrow(Biomes.DEEP_FROZEN_OCEAN)
                .is(OilBiomes.OCEAN_REPLACEABLE) && !biomes.getHolderOrThrow(Biomes.PLAINS).is(OilBiomes.DESERT_REPLACEABLE),
                "deserts and every ocean must be replaceable, plains not");

        require(helper, !nether.possibleBiomes().contains(oilDesert) && !nether.possibleBiomes().contains(oilOcean), "the nether has no oil biomes");

        // The test server's overworld is flat, so install the layers for a multi-noise overworld source for this check.
        MultiNoiseBiomeSource source = MultiNoiseBiomeSource.Preset.OVERWORLD.biomeSource(biomes);
        OilBiomes.State state = OilBiomes.State.create(registries, helper.getLevel().getSeed(), source);
        OilBiomes.install(state);
        try {
            checkOilBiomePlacement(helper, registries, state, source, oilDesert, oilOcean);
        } finally {
            OilBiomes.install(installed);
        }
        helper.succeed();
    }

    private static void checkOilBiomePlacement(GameTestHelper helper, RegistryAccess registries, OilBiomes.State state, MultiNoiseBiomeSource source,
            Holder<Biome> oilDesert, Holder<Biome> oilOcean) {
        Registry<Biome> biomes = registries.registryOrThrow(Registry.BIOME_REGISTRY);
        require(helper, source.possibleBiomes().contains(oilDesert) && source.possibleBiomes().contains(oilOcean),
                "the overworld must list the oil biomes as possible, so that they are decorated");
        MultiNoiseBiomeSource otherDimension = MultiNoiseBiomeSource.Preset.OVERWORLD.biomeSource(biomes);
        require(helper, !otherDimension.possibleBiomes().contains(oilDesert) && !otherDimension.possibleBiomes().contains(oilOcean),
                "a dimension reusing the overworld preset must keep its biomes");

        // Upstream GenLayerBiomeReplacer: offsets from the world seed, then noise over block coordinates.
        Random rand = new Random(state.seed());
        double xOffset = rand.nextInt(500000) - 250000, zOffset = rand.nextInt(500000) - 250000;
        Climate.Sampler sampler = RandomState.create(registries, NoiseGeneratorSettings.OVERWORLD, state.seed()).sampler();
        int[] found = new int[4];
        for (int x = -32768; x <= 32768; x += 256) {
            for (int z = -32768; z <= 32768; z += 256) {
                boolean oilyDesert = SimplexNoise.noise((x + xOffset) * 0.001, (z + zOffset) * 0.001) > 0.7;
                boolean oilyOcean = SimplexNoise.noise((x + xOffset) * 0.0005, (z + zOffset) * 0.0005) > 0.9;
                if (!oilyDesert && !oilyOcean && (x | z) % 1024 != 0) continue;
                int qx = QuartPos.fromBlock(x), qy = QuartPos.fromBlock(64), qz = QuartPos.fromBlock(z);
                Holder<Biome> raw = source.getNoiseBiome(sampler.sample(qx, qy, qz));
                Holder<Biome> placed = source.getNoiseBiome(qx, qy, qz, sampler);
                Holder<Biome> expected = raw.is(OilBiomes.OCEAN_REPLACEABLE) && oilyOcean ? oilOcean
                        : raw.is(OilBiomes.DESERT_REPLACEABLE) && oilyDesert ? oilDesert : raw;
                require(helper, placed == expected, "at " + x + ", " + z + " " + raw.unwrapKey().orElseThrow().location() + " must become "
                        + expected.unwrapKey().orElseThrow().location() + ", got " + placed.unwrapKey().orElseThrow().location());
                require(helper, otherDimension.getNoiseBiome(qx, qy, qz, sampler) == raw, "at " + x + ", " + z
                        + " a dimension reusing the overworld preset must keep " + raw.unwrapKey().orElseThrow().location());
                if (raw.is(OilBiomes.DESERT_REPLACEABLE)) found[oilyDesert ? 0 : 1]++;
                if (raw.is(OilBiomes.OCEAN_REPLACEABLE)) found[oilyOcean ? 2 : 3]++;
            }
        }
        require(helper, Arrays.stream(found).allMatch(n -> n > 0), "the scan must cover deserts and oceans inside and outside the oil fields, found "
                + Arrays.toString(found));

        // The oil biomes take their parents' surfaces: sand for the oil desert.
        NoiseBasedChunkGenerator generator = new NoiseBasedChunkGenerator(registries.registryOrThrow(Registry.STRUCTURE_SET_REGISTRY),
                registries.registryOrThrow(Registry.NOISE_REGISTRY), source,
                registries.registryOrThrow(Registry.NOISE_GENERATOR_SETTINGS_REGISTRY).getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD));
        RandomState random = RandomState.create(registries, NoiseGeneratorSettings.OVERWORLD, state.seed());
        SurfaceRules.RuleSource sand = SurfaceRules.ifTrue(SurfaceRules.isBiome(Biomes.DESERT), SurfaceRules.state(Blocks.SAND.defaultBlockState()));
        CarvingContext carving = new CarvingContext(generator, registries, helper.getLevel(), null, random, sand);
        for (Holder<Biome> biome : List.of(oilDesert, biomes.getHolderOrThrow(Biomes.DESERT), biomes.getHolderOrThrow(Biomes.PLAINS), oilOcean)) {
            boolean expected = biome == oilDesert || biome.is(Biomes.DESERT);
            Optional<BlockState> top = random.surfaceSystem().topMaterial(sand, carving, pos -> biome, null, null, new BlockPos(0, 64, 0), false);
            require(helper, top.isPresent() == expected, biome.unwrapKey().orElseThrow().location() + (expected ? " must" : " must not")
                    + " pass a surface rule for deserts");
        }
    }

    /** Light fuel is reachable from crude oil through the heat exchanger and the distiller, as upstream's chain. */
    public static void lightFuelChainFromOil(GameTestHelper helper) {
        Fluid oil = BCEnergy.OIL.heat(0).source();
        RefineryRecipes.HeatStep heat = RefineryRecipes.heatable(oil);
        RefineryRecipes.HeatStep lava = RefineryRecipes.coolable(Fluids.LAVA);
        require(helper, heat != null && heat.out() == BCEnergy.OIL.heat(1).source(), "the heat exchanger must heat oil to hot oil");
        require(helper, lava != null && lava.heatFrom() > heat.heatFrom(), "lava must be hot enough to heat oil");
        RefineryRecipes.Distillation hotOil = RefineryRecipes.distillation(heat.out());
        require(helper, hotOil != null && hotOil.outGas().fluid() == BCEnergy.FUEL_MIXED_LIGHT.heat(1).source()
                && hotOil.outLiquid().fluid() == BCEnergy.OIL_DENSE.heat(1).source(), "hot oil must distil into hot mixed light fuels and dense oil");
        RefineryRecipes.HeatStep cool = RefineryRecipes.coolable(hotOil.outGas().fluid());
        RefineryRecipes.HeatStep water = RefineryRecipes.heatable(Fluids.WATER);
        require(helper, cool != null && cool.out() == BCEnergy.FUEL_MIXED_LIGHT.heat(0).source(), "hot mixed light fuels must cool");
        require(helper, water != null && cool.heatFrom() > water.heatFrom(), "water must be cold enough to cool them");
        RefineryRecipes.Distillation mixed = RefineryRecipes.distillation(cool.out());
        require(helper, mixed != null && mixed.outLiquid().fluid() == BCEnergy.FUEL_LIGHT.heat(0).source()
                && mixed.outGas().fluid() == BCEnergy.FUEL_GASEOUS.heat(0).source(), "mixed light fuels must distil into gaseous and light fuel");
        require(helper, FuelRegistry.fuel(mixed.outLiquid().fluid()) != null && FuelRegistry.fuel(mixed.outGas().fluid()) != null,
                "both products must burn in a combustion engine");
        require(helper, RefineryRecipes.distillations().size() == 10, "upstream has 10 distillation recipes, got " + RefineryRecipes.distillations().size());
        helper.succeed();
    }

    /** Every upstream fuel fills the combustion engine's fuel tank; crude oil leaves residue as it burns. */
    public static void combustionEngineBurnsOilIntoResidue(GameTestHelper helper) {
        CombustionEngineBlockEntity engine = engine(helper, new BlockPos(1, 1, 1), BCEnergy.COMBUSTION_ENGINE.get(), Direction.EAST,
                CombustionEngineBlockEntity.class);
        FluidPort port = engine.fluidPort(Direction.NORTH);
        require(helper, FuelRegistry.fuels().size() == 9, "upstream registers 9 fuels, got " + FuelRegistry.fuels().size());
        for (FuelRegistry.Fuel fuel : FuelRegistry.fuels()) {
            require(helper, port.fill(fuel.fluid().get(), 1000, true) == 1000, Registry.FLUID.getKey(fuel.fluid().get()) + " must fill the fuel tank");
        }
        require(helper, port.fill(BCEnergy.FUEL_LIGHT.heat(1).source(), 1000, true) == 0, "only the cool stage of a fuel burns");
        require(helper, port.fill(BCEnergy.OIL.heat(0).source(), 1000, false) == 1000, "oil must fill the fuel tank");
        require(helper, port.fill(Fluids.WATER, 1000, false) == 1000, "water must fill the coolant tank");
        MiningWellBlockEntity well = idleMiningWell(helper, new BlockPos(2, 1, 1));
        helper.setBlock(new BlockPos(0, 1, 1), Blocks.REDSTONE_BLOCK);
        helper.succeedWhen(() -> {
            require(helper, well.battery().stored() > 0, "burning oil must power the mining well");
            require(helper, engine.residueTank().fluid() == BCEnergy.OIL_RESIDUE.heat(0).source() && engine.residueTank().amount() >= 1,
                    "burning oil must leave residue, has " + engine.residueTank().amount());
        });
    }

    /** Upstream {@code EnumSpring.WATER}: the creative water spring puts a water source above itself whenever that space is air. */
    public static void waterSpringRefills(GameTestHelper helper) {
        BlockPos spring = new BlockPos(1, 1, 1), covered = new BlockPos(5, 1, 1);
        helper.setBlock(spring, BCCore.WATER_SPRING.get());
        helper.setBlock(covered, BCCore.WATER_SPRING.get());
        helper.setBlock(covered.above(), Blocks.GLASS);
        require(helper, BCCore.WATER_SPRING.get().defaultBlockState().getDestroySpeed(helper.getLevel(), helper.absolutePos(spring)) < 0,
                "a spring is unbreakable");
        helper.runAfterDelay(6, () -> {
            requireWaterSource(helper, spring.above());
            helper.assertBlockPresent(Blocks.GLASS, covered.above());
            helper.setBlock(spring.above(), Blocks.AIR);
            helper.succeedWhen(() -> requireWaterSource(helper, spring.above()));
        });
    }

    private static void requireWaterSource(GameTestHelper helper, BlockPos pos) {
        FluidState fluid = helper.getLevel().getFluidState(helper.absolutePos(pos));
        require(helper, fluid.is(Fluids.WATER) && fluid.isSource(), "the spring must keep a water source at " + pos);
    }

    /** The distiller splits oil into gaseous fuel out of its top and heavy oil out of its bottom, using power. */
    public static void distillerSplitsOil(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, BCFactory.DISTILLER.get());
        DistillerBlockEntity distiller = tile(helper, pos, DistillerBlockEntity.class);
        Fluid oil = BCEnergy.OIL.heat(0).source();
        require(helper, distiller.fluidPort(Direction.NORTH).fill(Fluids.WATER, 1000, true) == 0, "water cannot be distilled");
        require(helper, distiller.fluidPort(Direction.NORTH).fill(oil, 1000, false) == 1000, "oil must fill the input from the side");
        require(helper, distiller.fluidPort(Direction.UP).fill(oil, 1000, true) == 0, "the top only drains");
        helper.onEachTick(() -> distiller.battery().set(distiller.battery().capacity()));
        helper.succeedWhen(() -> {
            Fluid gas = BCEnergy.FUEL_GASEOUS.heat(0).source(), heavy = BCEnergy.OIL_HEAVY.heat(0).source();
            require(helper, distiller.gasTank().fluid() == gas && distiller.gasTank().amount() >= 160,
                    "gaseous fuel must collect, has " + distiller.gasTank().amount());
            // 8 oil gives 16 gas and 3 heavy oil.
            require(helper, distiller.inputTank().amount() == 1000 - distiller.gasTank().amount() / 2
                    && distiller.liquidTank().amount() * 16 == distiller.gasTank().amount() * 3, "the products must follow the recipe");
            require(helper, distiller.fluidPort(Direction.UP).drain(null, 1000, true).fluid() == gas, "the top must drain the gas");
            require(helper, distiller.fluidPort(Direction.DOWN).drain(null, 1000, true).fluid() == heavy, "the bottom must drain the liquid");
            require(helper, distiller.fluidPort(Direction.NORTH).drain(null, 1000, true).isEmpty(), "the input cannot be drained");
        });
    }

    /** Power spent on a recipe whose output gets blocked returns to the battery, even after the battery refilled. */
    public static void distillerRefundsBlockedProgress(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, BCFactory.DISTILLER.get());
        DistillerBlockEntity distiller = tile(helper, pos, DistillerBlockEntity.class);
        distiller.fluidPort(Direction.NORTH).fill(BCEnergy.OIL.heat(0).source(), 1000, false);
        long capacity = distiller.battery().capacity();
        distiller.battery().set(capacity);
        // Crude oil needs 32 MJ at no more than 6 MJ/t, so three ticks leave a recipe part done.
        helper.runAfterDelay(3, () -> {
            require(helper, distiller.gasTank().isEmpty(), "no recipe may finish within three ticks");
            distiller.battery().set(capacity);
            distiller.gasTank().fill(Fluids.WATER, 1, false);
            helper.runAfterDelay(1, () -> {
                require(helper, distiller.battery().stored() > capacity,
                        "the blocked recipe's progress must return to the full battery, has " + distiller.battery().stored());
                helper.succeed();
            });
        });
    }

    /** Three exchangers facing west, from start at z 1 to end at z 3 (the start is clockwise of the facing). */
    private static HeatExchangerBlockEntity[] heatExchangerRow(GameTestHelper helper, int x, int length) {
        BlockState state = BCFactory.HEAT_EXCHANGER.get().defaultBlockState().setValue(HeatExchangerBlock.FACING, Direction.WEST);
        HeatExchangerBlockEntity[] row = new HeatExchangerBlockEntity[length];
        for (int i = 0; i < length; i++) {
            helper.setBlock(new BlockPos(x, 1, 1 + i), state);
            row[i] = tile(helper, new BlockPos(x, 1, 1 + i), HeatExchangerBlockEntity.class);
        }
        return row;
    }

    /**
     * A row of three or more forms and a wrench turns it around with its fluid. Breaking the start drops its fluid as
     * shards; the two left behind unform and drop theirs, as upstream's {@code removeSection}.
     */
    public static void heatExchangerFormsRows(GameTestHelper helper) {
        HeatExchangerBlockEntity[] row = heatExchangerRow(helper, 1, 3);
        HeatExchangerBlockEntity[] pair = heatExchangerRow(helper, 3, 2);
        helper.runAfterDelay(2, () -> {
            require(helper, row[0].part() == HeatExchangerBlock.Part.START && row[1].part() == HeatExchangerBlock.Part.MIDDLE
                    && row[2].part() == HeatExchangerBlock.Part.END, "three in a row must form start, middle and end");
            require(helper, row[0].middleCount() == 1 && row[0].end() == row[2], "the start must find its end");
            require(helper, pair[0].part() == HeatExchangerBlock.Part.MIDDLE && pair[1].part() == HeatExchangerBlock.Part.MIDDLE,
                    "two exchangers do not form");
            require(helper, helper.getBlockState(new BlockPos(1, 1, 2)).getValue(HeatExchangerBlock.CONNECTED_LEFT)
                    && helper.getBlockState(new BlockPos(1, 1, 2)).getValue(HeatExchangerBlock.CONNECTED_RIGHT), "the middle must connect both ways");
            Fluid oil = BCEnergy.OIL.heat(0).source();
            require(helper, row[0].fluidPort(Direction.DOWN).fill(oil, 1000, false) == 1000, "the start must take oil from below");
            require(helper, row[0].fluidPort(Direction.NORTH).fill(oil, 1000, true) == 0, "the start's outer side only drains");
            require(helper, row[2].fluidPort(Direction.SOUTH).fill(Fluids.LAVA, 1000, false) == 1000, "the end must take lava from its side");
            require(helper, row[1].fluidPort(Direction.DOWN).fill(oil, 1000, true) == 0, "middles hold no fluid");

            row[0].rotate();
            helper.runAfterDelay(2, () -> {
                HeatExchangerBlockEntity start = tile(helper, new BlockPos(1, 1, 3), HeatExchangerBlockEntity.class);
                HeatExchangerBlockEntity end = tile(helper, new BlockPos(1, 1, 1), HeatExchangerBlockEntity.class);
                require(helper, start.facing() == Direction.EAST && start.part() == HeatExchangerBlock.Part.START
                        && end.part() == HeatExchangerBlock.Part.END, "a wrenched row must turn around");
                require(helper, start.inputTank().fluid() == oil && end.inputTank().fluid() == Fluids.LAVA, "the tanks must move with the start and end");

                helper.destroyBlock(new BlockPos(1, 1, 3));
                requireShards(helper, new BlockPos(1, 1, 3), oil, 500, 500);
                helper.runAfterDelay(2, () -> {
                    for (int z : new int[] {1, 2}) {
                        HeatExchangerBlockEntity lone = tile(helper, new BlockPos(1, 1, z), HeatExchangerBlockEntity.class);
                        require(helper, lone.part() == HeatExchangerBlock.Part.MIDDLE && lone.inputTank().isEmpty(),
                                "a broken row must unform and empty its tanks, " + z + " is " + lone.part());
                    }
                    requireShards(helper, new BlockPos(1, 1, 1), Fluids.LAVA, 500, 500);
                    helper.succeed();
                });
            });
        });
    }

    /** Lava piped into the end heats oil piped into the start; hot oil leaves the top of the end. */
    public static void heatExchangerHeatsOilWithLava(GameTestHelper helper) {
        HeatExchangerBlockEntity[] row = heatExchangerRow(helper, 1, 3);
        HeatExchangerBlockEntity[] same = heatExchangerRow(helper, 4, 3);
        Fluid oil = BCEnergy.OIL.heat(0).source(), hotOil = BCEnergy.OIL.heat(1).source();
        Fluid hotFuel = BCEnergy.FUEL_LIGHT.heat(1).source();
        helper.runAfterDelay(2, () -> {
            row[0].fluidPort(Direction.DOWN).fill(oil, 1000, false);
            row[2].fluidPort(Direction.SOUTH).fill(Fluids.LAVA, 1000, false);
            // A coolant no hotter than the heatant exchanges nothing.
            same[0].fluidPort(Direction.DOWN).fill(hotFuel, 1000, false);
            same[2].fluidPort(Direction.SOUTH).fill(hotOil, 1000, false);
            helper.runAtTickTime(HeatExchangerBlockEntity.WARM_UP_TICKS / 2, () -> require(helper,
                    row[0].progressState() == HeatExchangerBlockEntity.ProgressState.PREPARING && row[2].outputTank().isEmpty(),
                    "the exchanger must warm up before exchanging, state " + row[0].progressState()));
            helper.succeedWhen(() -> {
                require(helper, row[0].progressState() == HeatExchangerBlockEntity.ProgressState.RUNNING, "the exchanger must be running");
                require(helper, row[2].outputTank().fluid() == hotOil && row[2].outputTank().amount() >= 100,
                        "hot oil must collect in the end, has " + row[2].outputTank().amount());
                int moved = row[2].outputTank().amount();
                require(helper, row[0].inputTank().amount() == 1000 - moved && row[2].inputTank().amount() == 1000 - moved,
                        "both sides must move the same amount");
                require(helper, row[0].outputTank().isEmpty(), "lava is used up rather than cooled into a fluid");
                require(helper, same[0].inputTank().amount() == 1000 && same[2].outputTank().isEmpty(), "equal heats must not exchange");
            });
        });
    }

    // ---- fragile fluid shards ------------------------------------------------------------------

    /** The fragile fluid shards lying within a block of {@code pos}. */
    static List<ItemStack> shardsAt(GameTestHelper helper, BlockPos pos) {
        AABB box = new AABB(helper.absolutePos(pos)).inflate(1);
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, box, entity -> entity.getItem().is(BCCore.FRAGILE_FLUID_SHARD.get()))
                .stream().map(ItemEntity::getItem).toList();
    }

    static void requireShards(GameTestHelper helper, BlockPos pos, Fluid fluid, int... amounts) {
        List<Integer> found = shardsAt(helper, pos).stream().map(FragileFluidShardItem::getFluid)
                .filter(held -> held.fluid() == fluid).map(held -> (int) held.amount()).sorted().toList();
        List<Integer> expected = Arrays.stream(amounts).sorted().boxed().toList();
        require(helper, found.equals(expected), "expected " + Registry.FLUID.getKey(fluid) + " shards of " + expected + " mB at " + pos + ", found " + found);
    }

    /** Upstream {@code addFluidDrops}: whole 500 mB shards and one for the rest, named after the fluid and its heat. */
    public static void fragileShardsSplitFluid(GameTestHelper helper) {
        Fluid oil = BCEnergy.OIL.heat(1).source();
        List<ItemStack> drops = new ArrayList<>();
        FragileFluidShardItem.addFluidDrops(drops, new FluidAmount(oil, 1250));
        require(helper, drops.size() == 3 && drops.stream().allMatch(stack -> stack.getMaxStackSize() == 1),
                "1250 mB must make three unstackable shards, made " + drops.size());
        require(helper, drops.stream().mapToLong(stack -> FragileFluidShardItem.getFluid(stack).amount()).sum() == 1250
                && FragileFluidShardItem.getFluid(drops.get(2)).amount() == 250, "the shards must hold 500, 500 and 250 mB");

        ItemStack shard = drops.get(2);
        require(helper, FragileFluidShardItem.drain(shard, Fluids.WATER, 100, false).isEmpty(), "a shard only gives up its own fluid");
        FluidAmount drained = FragileFluidShardItem.drain(shard, null, 100, false);
        require(helper, drained.fluid() == oil && drained.amount() == 100 && FragileFluidShardItem.getFluid(shard).amount() == 150,
                "draining 100 mB must leave 150 mB");
        FragileFluidShardItem.drain(shard, oil, 1000, false);
        require(helper, FragileFluidShardItem.getFluid(shard).isEmpty() && !shard.hasTag(), "a drained shard must hold nothing");

        if (!(drops.get(0).getHoverName().getContents() instanceof TranslatableContents name)) throw new GameTestAssertException("shard names must translate");
        require(helper, name.getKey().equals("buildcraft.fluid.heat_1") && name.getArgs()[0] instanceof Component inner
                && inner.getContents() instanceof TranslatableContents item && item.getKey().equals("item.buildcraft.fragile_fluid_shard")
                && item.getArgs()[0] instanceof Component bare && bare.getContents() instanceof TranslatableContents family
                && family.getKey().equals("fluid_family.buildcraft.oil"), "a hot oil shard must read \"Fragile Oil Shard (Hot)\", got " + name);
        helper.succeed();
    }

    /** A tank, distiller, combustion engine, pump and fluid pipe drop the fluid they hold as shards when broken. */
    public static void brokenMachinesDropFluidShards(GameTestHelper helper) {
        Fluid oil = BCEnergy.OIL.heat(0).source(), fuel = BCEnergy.FUEL_LIGHT.heat(0).source(), gas = BCEnergy.FUEL_GASEOUS.heat(0).source();
        BlockPos tankPos = new BlockPos(1, 1, 1), distillerPos = new BlockPos(4, 1, 1), enginePos = new BlockPos(7, 1, 1);
        BlockPos pumpPos = new BlockPos(1, 1, 5), pipePos = new BlockPos(5, 1, 5);
        helper.setBlock(tankPos, BCFactory.TANK.get());
        tile(helper, tankPos, TankBlockEntity.class).tank().fill(Fluids.WATER, 1250, false);
        helper.setBlock(distillerPos, BCFactory.DISTILLER.get());
        DistillerBlockEntity distiller = tile(helper, distillerPos, DistillerBlockEntity.class);
        distiller.inputTank().fill(oil, 700, false);
        distiller.gasTank().fill(gas, 300, false);
        CombustionEngineBlockEntity engine = engine(helper, enginePos, BCEnergy.COMBUSTION_ENGINE.get(), Direction.UP, CombustionEngineBlockEntity.class);
        engine.fuelTank().fill(fuel, 500, false);
        engine.coolantTank().fill(Fluids.WATER, 100, false);
        helper.setBlock(pumpPos, BCFactory.PUMP.get());
        tile(helper, pumpPos, PumpBlockEntity.class).tank().fill(Fluids.LAVA, 1000, false);
        helper.setBlock(pipePos.west(), BCFactory.TANK.get());
        PipeBlockEntity pipe = pipe(helper, pipePos, BCTransport.STONE_FLUID);
        helper.runAfterDelay(2, () -> {
            require(helper, pipe.fluidPort(Direction.WEST).fill(Fluids.WATER, 10, false) == 10, "the pipe must take water from its tank side");
            for (BlockPos pos : List.of(tankPos, distillerPos, enginePos, pumpPos, pipePos)) helper.destroyBlock(pos);
            requireShards(helper, tankPos, Fluids.WATER, 500, 500, 250);
            requireShards(helper, distillerPos, oil, 500, 200);
            requireShards(helper, distillerPos, gas, 300);
            requireShards(helper, enginePos, fuel, 500);
            requireShards(helper, enginePos, Fluids.WATER, 100);
            requireShards(helper, pumpPos, Fluids.LAVA, 500, 500);
            requireShards(helper, pipePos, Fluids.WATER, 10);
            helper.succeed();
        });
    }

    /** A shard empties into a tank through each loader's held-container interaction. */
    public static void shardEmptiesIntoTank(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, BCFactory.TANK.get());
        TankBlockEntity tank = tile(helper, pos, TankBlockEntity.class);
        Fluid oil = BCEnergy.OIL.heat(0).source();
        Player player = helper.makeMockPlayer();
        player.setItemInHand(InteractionHand.MAIN_HAND, FragileFluidShardItem.of(new FluidAmount(oil, 300)));
        require(helper, Platform.get().interactWithFluidContainer(player, InteractionHand.MAIN_HAND, helper.getLevel(), helper.absolutePos(pos), Direction.UP),
                "a shard must empty into the tank");
        require(helper, tank.tank().fluid() == oil && tank.tank().amount() == 300, "the tank must receive the shard's 300 mB, has " + tank.tank().amount());
        tank.tank().set(Fluids.WATER, 1000);
        player.setItemInHand(InteractionHand.MAIN_HAND, FragileFluidShardItem.of(new FluidAmount(oil, 300)));
        Platform.get().interactWithFluidContainer(player, InteractionHand.MAIN_HAND, helper.getLevel(), helper.absolutePos(pos), Direction.UP);
        require(helper, tank.tank().fluid() == Fluids.WATER && tank.tank().amount() == 1000, "a shard can never be filled from a tank");
        helper.succeed();
    }

    /**
     * Upstream {@code WidgetFluidTank}: a carried container clicked on a GUI tank empties into it or fills
     * from it, survival players trade one item for its new container and creative players keep theirs,
     * shift-clicked containers go to the distiller's input or the engine's tanks, and ice cools an engine.
     */
    public static void guiTanksTradeContainers(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 1, 1), BCFactory.DISTILLER.get());
        DistillerBlockEntity distiller = tile(helper, new BlockPos(1, 1, 1), DistillerBlockEntity.class);
        Fluid oil = BCEnergy.OIL.heat(0).source();
        Fluid gas = BCEnergy.FUEL_GASEOUS.heat(0).source();
        Player player = helper.makeMockPlayer();
        DistillerMenu menu = (DistillerMenu) distiller.createMenu(0, player.getInventory(), player);

        menu.setCarried(new ItemStack(oil.getBucket()));
        require(helper, menu.clickMenuButton(player, DistillerMenu.TANK_IN), "a GUI tank must accept a click");
        require(helper, distiller.inputTank().amount() == 1000 && menu.getCarried().is(Items.BUCKET),
                "an oil bucket must empty into the input and leave an empty bucket, input has " + distiller.inputTank().amount());
        menu.setCarried(new ItemStack(Items.WATER_BUCKET));
        menu.clickMenuButton(player, DistillerMenu.TANK_IN);
        require(helper, distiller.inputTank().amount() == 1000 && menu.getCarried().is(Items.WATER_BUCKET), "water cannot enter the input");

        distiller.gasTank().set(gas, 1500);
        menu.setCarried(new ItemStack(Items.BUCKET, 2));
        menu.clickMenuButton(player, DistillerMenu.TANK_GAS);
        require(helper, distiller.gasTank().amount() == 500 && menu.getCarried().is(Items.BUCKET) && menu.getCarried().getCount() == 1
                && player.getInventory().countItem(gas.getBucket()) == 1, "one of two buckets must fill with gas and go to the inventory");
        menu.clickMenuButton(player, DistillerMenu.TANK_GAS);
        require(helper, distiller.gasTank().amount() == 500 && menu.getCarried().getCount() == 1, "a bucket cannot take a partial 500 mB");

        player.getInventory().setItem(8, new ItemStack(oil.getBucket()));
        require(helper, menu.quickMoveStack(player, 35).isEmpty() && distiller.inputTank().amount() == 2000
                && player.getInventory().getItem(8).is(Items.BUCKET), "a shift-clicked oil bucket must empty into the input");

        player.getAbilities().instabuild = true;
        menu.setCarried(new ItemStack(oil.getBucket()));
        menu.clickMenuButton(player, DistillerMenu.TANK_IN);
        require(helper, distiller.inputTank().amount() == 3000 && menu.getCarried().is(oil.getBucket()), "a creative player keeps the full bucket");
        player.getAbilities().instabuild = false;

        menu.setCarried(FragileFluidShardItem.of(new FluidAmount(oil, 300)));
        menu.clickMenuButton(player, DistillerMenu.TANK_IN);
        require(helper, distiller.inputTank().amount() == 3300 && menu.getCarried().isEmpty(), "a shard must empty into the input and be used up");

        CombustionEngineBlockEntity engine = engine(helper, new BlockPos(4, 1, 1), BCEnergy.COMBUSTION_ENGINE.get(), Direction.UP, CombustionEngineBlockEntity.class);
        CombustionEngineMenu engineMenu = (CombustionEngineMenu) engine.createMenu(1, player.getInventory(), player);
        engineMenu.setCarried(new ItemStack(Items.ICE, 2));
        engineMenu.clickMenuButton(player, 1);
        require(helper, engine.coolantTank().fluid() == Fluids.WATER && engine.coolantTank().amount() == 1500 && engineMenu.getCarried().getCount() == 1,
                "ice clicked on the coolant gauge must melt into 1500 mB of water");
        Fluid fuel = BCEnergy.FUEL_LIGHT.heat(0).source();
        player.getInventory().setItem(7, new ItemStack(fuel.getBucket()));
        engineMenu.quickMoveStack(player, 34);
        require(helper, engine.fuelTank().fluid() == fuel && engine.fuelTank().amount() == 1000, "a shift-clicked fuel bucket must fill the fuel tank");
        helper.succeed();
    }

    // ---- transport -----------------------------------------------------------------------------

    /**
     * Items inserted into a void pipe are destroyed without dropping. Pipes, like upstream, only take
     * items on connected faces, so a chest supplies the connection and insertion waits for it.
     */
    public static void voidPipeDestroysItems(GameTestHelper helper) {
        chest(helper, new BlockPos(0, 1, 1));
        PipeBlockEntity pipe = pipe(helper, new BlockPos(1, 1, 1), BCTransport.VOID_ITEM);
        ItemFlow flow = (ItemFlow) pipe.flow();
        require(helper, !pipe.itemPort(Direction.EAST).insert(new ItemStack(Items.DIAMOND), true).isEmpty(), "unconnected faces take no items");
        helper.runAfterDelay(2, () -> {
            ItemStack left = pipe.itemPort(Direction.WEST).insert(new ItemStack(Items.DIAMOND, 3), false);
            require(helper, left.isEmpty(), "the void pipe must accept the stack on its connected face");
            require(helper, flow.itemCount() == 3, "the stack must be travelling, count " + flow.itemCount());
            helper.succeedWhen(() -> {
                require(helper, flow.itemCount() == 0, "the void pipe must destroy its items");
                helper.assertItemEntityNotPresent(Items.DIAMOND, new BlockPos(1, 1, 1), 3);
            });
        });
    }

    /** Fluid pipes move fluid from a connected face into a tank at the other end. */
    public static void fluidPipesFillTank(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 1, 1), BCFactory.TANK.get());
        PipeBlockEntity first = pipe(helper, new BlockPos(1, 1, 1), BCTransport.STONE_FLUID);
        pipe(helper, new BlockPos(2, 1, 1), BCTransport.STONE_FLUID);
        helper.setBlock(new BlockPos(3, 1, 1), BCFactory.TANK.get());
        TankBlockEntity tank = tile(helper, new BlockPos(3, 1, 1), TankBlockEntity.class);
        helper.onEachTick(() -> first.fluidPort(Direction.WEST).fill(Fluids.WATER, BCTransport.STONE_FLUID.fluidTransferPerTick, false));
        helper.succeedWhen(() -> {
            require(helper, tank.tank().fluid() == Fluids.WATER && tank.tank().amount() >= 500,
                    "the tank must fill through the pipes, has " + tank.tank().amount());
            require(helper, first.fluidPort(Direction.WEST).fill(Fluids.LAVA, 10, true) == 0, "a pipe holding water must refuse lava");
        });
    }

    /** Upstream's diamond fluid pipe transfers 8x the base rate and uses the diamond filter menu. */
    public static void diamondFluidPipeDefinition(GameTestHelper helper) {
        PipeDefinition definition = BCTransport.DIAMOND_FLUID;
        require(helper, definition.flow == PipeDefinition.Flow.FLUID, "the diamond fluid pipe must carry fluid");
        require(helper, definition.fluidTransferPerTick == 80, "the diamond fluid pipe must move 80 mB/t, moves "
                + definition.fluidTransferPerTick);
        require(helper, definition.fluidTransferDelay == 10, "the diamond fluid pipe must have no speed bonus, delay "
                + definition.fluidTransferDelay);
        require(helper, definition.hasMenu, "the diamond fluid pipe must open the diamond filter menu");
        PipeBlockEntity pipe = pipe(helper, new BlockPos(1, 1, 1), definition);
        require(helper, pipe.behaviour() instanceof DiamondFluidBehaviour, "the diamond fluid pipe must use the diamond fluid behaviour");
        helper.succeed();
    }

    /**
     * A filter face holding a container of the travelling fluid is preferred; a face whose filters hold
     * other fluids rejects it. Faces without fluid filters stay available.
     */
    public static void diamondFluidFiltersFaces(GameTestHelper helper) {
        PipeBlockEntity pipe = pipe(helper, new BlockPos(1, 1, 1), BCTransport.DIAMOND_FLUID);
        helper.setBlock(new BlockPos(1, 1, 0), BCFactory.TANK.get());
        helper.setBlock(new BlockPos(2, 1, 1), BCFactory.TANK.get());
        DiamondFluidBehaviour behaviour = (DiamondFluidBehaviour) pipe.behaviour();
        behaviour.filters.setItem(DiamondItemBehaviour.FILTERS_PER_SIDE * Direction.NORTH.ordinal(), new ItemStack(Items.WATER_BUCKET));
        behaviour.filters.setItem(DiamondItemBehaviour.FILTERS_PER_SIDE * Direction.EAST.ordinal(), new ItemStack(Items.LAVA_BUCKET));
        helper.succeedWhen(() -> {
            require(helper, pipe.isConnected(Direction.NORTH) && pipe.isConnected(Direction.EAST),
                    "the tanks must connect to the filtered faces");
            SideCheck water = new SideCheck(Fluids.WATER, null);
            water.disallowAllExcept(EnumSet.of(Direction.NORTH, Direction.EAST));
            behaviour.fluidSideCheck(water);
            require(helper, water.isAllowed(Direction.NORTH), "the water-filtered face must accept water");
            require(helper, !water.isAllowed(Direction.EAST), "the lava-filtered face must reject water");
            require(helper, water.firstOrder().equals(EnumSet.of(Direction.NORTH)), "water must prefer only the matching face");

            SideCheck lava = new SideCheck(Fluids.LAVA, null);
            lava.disallowAllExcept(EnumSet.of(Direction.NORTH, Direction.EAST));
            behaviour.fluidSideCheck(lava);
            require(helper, lava.isAllowed(Direction.EAST), "the lava-filtered face must accept lava");
            require(helper, !lava.isAllowed(Direction.NORTH), "the water-filtered face must reject lava");
            require(helper, lava.firstOrder().equals(EnumSet.of(Direction.EAST)), "lava must prefer only the matching face");

            SideCheck unfiltered = new SideCheck(Fluids.WATER, null);
            unfiltered.disallowAllExcept(EnumSet.of(Direction.SOUTH, Direction.WEST));
            behaviour.fluidSideCheck(unfiltered);
            require(helper, unfiltered.isAllowed(Direction.SOUTH) && unfiltered.isAllowed(Direction.WEST),
                    "faces without fluid filters must stay available");
        });
    }

    /** Water inserted into a diamond fluid pipe leaves through its matching filter face and not the other filtered face. */
    public static void diamondFluidPipeRoutesFilteredFluid(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 1, 1), BCFactory.TANK.get());
        PipeBlockEntity pipe = pipe(helper, new BlockPos(1, 1, 1), BCTransport.DIAMOND_FLUID);
        helper.setBlock(new BlockPos(1, 1, 0), BCFactory.TANK.get());
        helper.setBlock(new BlockPos(2, 1, 1), BCFactory.TANK.get());
        TankBlockEntity north = tile(helper, new BlockPos(1, 1, 0), TankBlockEntity.class);
        TankBlockEntity east = tile(helper, new BlockPos(2, 1, 1), TankBlockEntity.class);
        DiamondFluidBehaviour behaviour = (DiamondFluidBehaviour) pipe.behaviour();
        behaviour.filters.setItem(DiamondItemBehaviour.FILTERS_PER_SIDE * Direction.NORTH.ordinal(), new ItemStack(Items.WATER_BUCKET));
        behaviour.filters.setItem(DiamondItemBehaviour.FILTERS_PER_SIDE * Direction.EAST.ordinal(), new ItemStack(Items.LAVA_BUCKET));
        helper.onEachTick(() -> pipe.fluidPort(Direction.WEST).fill(Fluids.WATER, BCTransport.DIAMOND_FLUID.fluidTransferPerTick, false));
        helper.succeedWhen(() -> {
            require(helper, north.tank().fluid() == Fluids.WATER && north.tank().amount() > 0,
                    "water must reach the matching filtered tank, has " + north.tank().amount());
            require(helper, east.tank().amount() == 0, "water must not take the face filtered for lava, has " + east.tank().amount());
        });
    }

    /** Kinesis pipes carry a Stirling engine's output to a machine. */
    public static void kinesisPipesCarryEnginePower(GameTestHelper helper) {
        StirlingEngineBlockEntity engine = engine(helper, new BlockPos(1, 1, 1), BCEnergy.STIRLING_ENGINE.get(), Direction.EAST,
                StirlingEngineBlockEntity.class);
        engine.setItem(0, new ItemStack(Items.COAL, 2));
        pipe(helper, new BlockPos(2, 1, 1), BCTransport.WOOD_POWER);
        pipe(helper, new BlockPos(3, 1, 1), BCTransport.STONE_POWER);
        MiningWellBlockEntity well = idleMiningWell(helper, new BlockPos(4, 1, 1));
        helper.setBlock(new BlockPos(1, 2, 1), Blocks.REDSTONE_BLOCK);
        helper.succeedWhen(() -> require(helper, well.battery().stored() >= Power.MJ,
                "the mining well must receive power through the pipes, has " + well.battery().stored()));
    }

    /**
     * Upstream {@code BCTransportConfig}: iron kinesis pipes move 8 times and diamond ones 64 times the base 4 MJ/t,
     * neither takes power from machines, and upstream's {@code PipeBehaviourLimiter} shifts the rate right by 0 to 5
     * and stops transfer at 6. The limit is saved and drawn from the block state.
     */
    public static void kinesisLimiterPipeDefinitions(GameTestHelper helper) {
        long base = BCTransport.COBBLESTONE_POWER.powerTransferPerTick;
        require(helper, base == 4 * Power.MJ, "the base kinesis rate must be 4 MJ/t, is " + Power.formatMj(base));
        require(helper, BCTransport.IRON_POWER.powerTransferPerTick == 8 * base, "the iron kinesis pipe must move 32 MJ/t");
        require(helper, BCTransport.DIAMOND_POWER.powerTransferPerTick == 64 * base, "the diamond kinesis pipe must move 256 MJ/t");
        for (PipeDefinition definition : List.of(BCTransport.IRON_POWER, BCTransport.DIAMOND_POWER)) {
            require(helper, definition.flow == PipeDefinition.Flow.POWER && !definition.powerReceiver,
                    definition.registryName() + " must carry power without taking it from machines");
        }
        PipeBlockEntity iron = pipe(helper, new BlockPos(1, 1, 1), BCTransport.IRON_POWER);
        PipeBlockEntity diamond = pipe(helper, new BlockPos(3, 1, 3), BCTransport.DIAMOND_POWER);
        require(helper, iron.behaviour() instanceof LimiterBehaviour && diamond.behaviour() instanceof LimiterBehaviour,
                "the iron and diamond kinesis pipes must use the limiter behaviour");
        LimiterBehaviour limiter = (LimiterBehaviour) iron.behaviour();
        PowerFlow flow = (PowerFlow) iron.flow();
        long[] expected = {32, 16, 8, 4, 2, 1};
        for (int shift = 0; shift <= LimiterBehaviour.MAX_SHIFT; shift++) {
            limiter.setLimitShift(shift);
            require(helper, iron.getBlockState().getValue(LimiterPipeBlock.LIMIT_SHIFT) == shift,
                    "the block state must show limit shift " + shift);
            require(helper, flow.isTransferDisabled() == (shift == LimiterBehaviour.MAX_SHIFT), "only the last limit stops transfer");
            if (shift < LimiterBehaviour.MAX_SHIFT) {
                require(helper, flow.maxPower() == expected[shift] * Power.MJ,
                        "limit shift " + shift + " must allow " + expected[shift] + " MJ/t, allows " + Power.formatMj(flow.maxPower()));
            }
        }
        limiter.setLimitShift(3);
        CompoundTag saved = iron.saveWithoutMetadata();
        PipeBlockEntity loaded = pipe(helper, new BlockPos(1, 1, 3), BCTransport.IRON_POWER);
        loaded.load(saved);
        require(helper, ((LimiterBehaviour) loaded.behaviour()).limitShift() == 3, "the limit must survive saving");
        require(helper, ((PowerFlow) diamond.flow()).maxPower() == 256 * Power.MJ, "an unlimited diamond kinesis pipe must allow 256 MJ/t");
        helper.succeed();
    }

    /**
     * A wooden kinesis pipe fed far more than it can pass on drives a laser through an iron kinesis pipe: the laser
     * gets the iron pipe's selected limit each tick, a gate's power limit action lowers it, and the last limit stops
     * the flow. Power is never made or lost along the way.
     */
    public static void ironKinesisPipeCapsDeliveredPower(GameTestHelper helper) {
        PipeBlockEntity wood = pipe(helper, new BlockPos(1, 1, 1), BCTransport.WOOD_POWER);
        PipeBlockEntity iron = pipe(helper, new BlockPos(2, 1, 1), BCTransport.IRON_POWER);
        LaserBlockEntity laser = laser(helper, new BlockPos(3, 1, 1));
        LimiterBehaviour limiter = (LimiterBehaviour) iron.behaviour();
        limiter.setLimitShift(2);
        long[] injected = {0};
        long[] mark = new long[1];
        helper.onEachTick(() -> {
            injected[0] += Power.fromUnits(wood.energyPort(Direction.WEST).receive(Power.toUnits(64 * Power.MJ), false));
            requireConserved(helper, injected[0], laser, wood, iron);
        });
        helper.runAtTickTime(20, () -> mark[0] = laser.battery().stored());
        helper.runAtTickTime(60, () -> {
            requireDelivered(helper, laser.battery().stored() - mark[0], 8 * Power.MJ, 40, "the iron kinesis pipe at 8 MJ/t");
            limiter.onActionActivate(BCTransportStatements.ACTION_IRON_POWER_LIMIT[LimiterBehaviour.MAX_SHIFT - 3], new StatementParameter[0]);
            require(helper, limiter.limitShift() == 3, "the power limit action must set the iron pipe to 4 MJ/t");
        });
        helper.runAtTickTime(80, () -> mark[0] = laser.battery().stored());
        helper.runAtTickTime(120, () -> {
            requireDelivered(helper, laser.battery().stored() - mark[0], 4 * Power.MJ, 40, "the iron kinesis pipe at 4 MJ/t");
            limiter.onActionActivate(BCTransportStatements.ACTION_IRON_POWER_LIMIT[0], new StatementParameter[0]);
            require(helper, limiter.limitShift() == LimiterBehaviour.MAX_SHIFT, "the first power limit action must stop transfer");
        });
        helper.runAtTickTime(140, () -> mark[0] = laser.battery().stored());
        helper.runAtTickTime(180, () -> {
            require(helper, laser.battery().stored() == mark[0], "a stopped iron kinesis pipe must deliver nothing, delivered "
                    + Power.formatMj(laser.battery().stored() - mark[0]) + " MJ");
            require(helper, wood.energyPort(Direction.WEST).receive(1000, true) == 0, "the wooden pipe must not be asked for power");
            helper.succeed();
        });
    }

    /**
     * As upstream's {@code PipeBehaviourLimiter}, which has no facing, an iron kinesis pipe sends power to every face
     * that asks for it, each up to its limit; the wrench changes the limit instead of a direction.
     */
    public static void ironKinesisPipeFeedsEveryRequestingFace(GameTestHelper helper) {
        PipeBlockEntity wood = pipe(helper, new BlockPos(1, 1, 2), BCTransport.WOOD_POWER);
        PipeBlockEntity iron = pipe(helper, new BlockPos(2, 1, 2), BCTransport.IRON_POWER);
        LaserBlockEntity east = laser(helper, new BlockPos(3, 1, 2));
        LaserBlockEntity north = laser(helper, new BlockPos(2, 1, 1));
        ((LimiterBehaviour) iron.behaviour()).setLimitShift(3);
        long[] injected = {0};
        helper.onEachTick(() -> {
            injected[0] += Power.fromUnits(wood.energyPort(Direction.WEST).receive(Power.toUnits(64 * Power.MJ), false));
            requireConserved(helper, injected[0], east, north, wood, iron);
        });
        helper.runAtTickTime(60, () -> {
            require(helper, east.battery().stored() > 0 && north.battery().stored() > 0,
                    "both lasers must receive power, east " + Power.formatMj(east.battery().stored()) + " MJ, north "
                            + Power.formatMj(north.battery().stored()) + " MJ");
            long cap = 4 * Power.MJ * 62;
            require(helper, east.battery().stored() <= cap && north.battery().stored() <= cap, "each face must keep to the 4 MJ/t limit");
            helper.succeed();
        });
    }

    /**
     * Each kinesis pipe asks a laser for at most its upstream rate (cobblestone 4, gold 128, diamond 256 MJ/t), and a
     * wooden pipe passes on 16 MJ/t through a diamond pipe but only the base 4 MJ/t through a cobblestone one.
     */
    public static void diamondKinesisPipeCarriesMoreThanBase(GameTestHelper helper) {
        PipeBlockEntity cobble = pipe(helper, new BlockPos(0, 1, 0), BCTransport.COBBLESTONE_POWER);
        laser(helper, new BlockPos(0, 2, 0));
        PipeBlockEntity gold = pipe(helper, new BlockPos(2, 1, 0), BCTransport.GOLD_POWER);
        laser(helper, new BlockPos(2, 2, 0));
        PipeBlockEntity diamondAlone = pipe(helper, new BlockPos(4, 1, 0), BCTransport.DIAMOND_POWER);
        laser(helper, new BlockPos(4, 2, 0));

        PipeBlockEntity baseWood = pipe(helper, new BlockPos(0, 1, 2), BCTransport.WOOD_POWER);
        PipeBlockEntity basePipe = pipe(helper, new BlockPos(1, 1, 2), BCTransport.COBBLESTONE_POWER);
        LaserBlockEntity baseLaser = laser(helper, new BlockPos(2, 1, 2));
        PipeBlockEntity diamondWood = pipe(helper, new BlockPos(0, 1, 4), BCTransport.WOOD_POWER);
        PipeBlockEntity diamond = pipe(helper, new BlockPos(1, 1, 4), BCTransport.DIAMOND_POWER);
        LaserBlockEntity diamondLaser = laser(helper, new BlockPos(2, 1, 4));
        long[] injected = {0, 0};
        long[] base = new long[1];
        long[] fast = new long[1];
        helper.onEachTick(() -> {
            injected[0] += Power.fromUnits(baseWood.energyPort(Direction.WEST).receive(Power.toUnits(64 * Power.MJ), false));
            injected[1] += Power.fromUnits(diamondWood.energyPort(Direction.WEST).receive(Power.toUnits(64 * Power.MJ), false));
            requireConserved(helper, injected[0], baseLaser, baseWood, basePipe);
            requireConserved(helper, injected[1], diamondLaser, diamondWood, diamond);
        });
        helper.runAtTickTime(10, () -> {
            requireRequested(helper, cobble, 4 * Power.MJ);
            requireRequested(helper, gold, 128 * Power.MJ);
            requireRequested(helper, diamondAlone, 256 * Power.MJ);
        });
        helper.runAtTickTime(20, () -> {
            base[0] = baseLaser.battery().stored();
            fast[0] = diamondLaser.battery().stored();
        });
        helper.runAtTickTime(60, () -> {
            long viaBase = baseLaser.battery().stored() - base[0];
            long viaDiamond = diamondLaser.battery().stored() - fast[0];
            requireDelivered(helper, viaBase, 4 * Power.MJ, 40, "the cobblestone kinesis pipe");
            requireDelivered(helper, viaDiamond, 16 * Power.MJ, 40, "the diamond kinesis pipe behind a 16 MJ/t wooden pipe");
            require(helper, viaDiamond > viaBase, "the diamond kinesis pipe must carry more than the base pipe");
            helper.succeed();
        });
    }

    /**
     * A kinesis pipe's face takes for real exactly what it said it would take when simulated, also when a limiter
     * pipe behind it is stopped, and no power appears or disappears between the wooden pipe and the laser.
     */
    public static void kinesisSimulatedTransferMatchesReal(GameTestHelper helper) {
        PipeBlockEntity wood = pipe(helper, new BlockPos(1, 1, 1), BCTransport.WOOD_POWER);
        PipeBlockEntity diamond = pipe(helper, new BlockPos(2, 1, 1), BCTransport.DIAMOND_POWER);
        LaserBlockEntity laser = laser(helper, new BlockPos(3, 1, 1));
        LimiterBehaviour limiter = (LimiterBehaviour) diamond.behaviour();
        long[] injected = {0};
        boolean[] sawPower = {false};
        helper.onEachTick(() -> {
            EnergyPort port = wood.energyPort(Direction.WEST);
            long offer = Power.toUnits(7 * Power.MJ) + 3;
            long simulated = port.receive(offer, true);
            long storedBefore = port.stored();
            long real = port.receive(offer, false);
            require(helper, simulated == real, "a simulated receive offered " + simulated + " units, the real one took " + real);
            require(helper, port.stored() - storedBefore == real, "the wooden pipe must hold exactly what it took");
            sawPower[0] |= real > 0;
            injected[0] += Power.fromUnits(real);
            requireConserved(helper, injected[0], laser, wood, diamond);
        });
        helper.runAtTickTime(40, () -> {
            require(helper, sawPower[0] && laser.battery().stored() > 0, "power must reach the laser");
            limiter.setLimitShift(LimiterBehaviour.MAX_SHIFT);
        });
        helper.runAtTickTime(60, () -> {
            require(helper, wood.energyPort(Direction.WEST).receive(1000, true) == 0
                    && wood.energyPort(Direction.WEST).receive(1000, false) == 0, "a stopped diamond pipe's wooden feeder must take nothing");
            helper.succeed();
        });
    }

    private static LaserBlockEntity laser(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, BCSilicon.LASER.get());
        return tile(helper, pos, LaserBlockEntity.class);
    }

    /** Everything put into the wooden pipes is in a laser's battery or still in the pipes, to the micro-MJ. */
    private static void requireConserved(GameTestHelper helper, long injected, Object... holders) {
        long total = 0;
        for (Object holder : holders) {
            if (holder instanceof LaserBlockEntity laser) total += laser.battery().stored();
            else total += ((PowerFlow) ((PipeBlockEntity) holder).flow()).storedMicroJoules();
        }
        require(helper, total == injected, "kinesis pipes must neither make nor lose power: put in " + injected + " µMJ, hold " + total);
    }

    /** Over {@code ticks} ticks a steady chain delivers its rate each tick, give or take what a pipe holds for a tick or two. */
    private static void requireDelivered(GameTestHelper helper, long delivered, long perTick, int ticks, String what) {
        require(helper, delivered >= perTick * (ticks - 2) && delivered <= perTick * (ticks + 2), what + " must deliver "
                + Power.formatMj(perTick) + " MJ/t, delivered " + Power.formatMj(delivered) + " MJ in " + ticks + " ticks");
    }

    private static void requireRequested(GameTestHelper helper, PipeBlockEntity pipe, long expected) {
        long requested = ((PowerFlow) pipe.flow()).getPowerRequested(null);
        require(helper, requested == expected, pipe.definition().registryName() + " must ask an empty laser for "
                + Power.formatMj(expected) + " MJ/t, asks " + Power.formatMj(requested));
    }

    // ---- data ----------------------------------------------------------------------------------

    private static final List<String> RECIPES = List.of("gear_wood", "gear_stone", "gear_iron", "gear_gold", "gear_diamond", "wrench",
            "redstone_engine", "stirling_engine", "combustion_engine", "tank", "pump", "mining_well", "distiller", "heat_exchange", "quarry", "waterproof",
            "marker_volume", "marker_path", "marker_connector",
            "laser", "assembly_table", "advanced_crafting_table", "integration_table", "redstone_chipset", "iron_chipset", "gold_chipset",
            "quartz_chipset", "diamond_chipset", "plug_blocker", "plug_power_adaptor");

    /** Upstream recipes are loaded, accept both conventional and Forge tags, and gears and wrenches carry both tags. */
    public static void recipesAndTags(GameTestHelper helper) {
        var recipes = helper.getLevel().getRecipeManager();
        for (String id : RECIPES) requireRecipe(helper, recipes.byKey(BuildCraft.id(id)).orElse(null), id, id);
        for (PipeDefinition pipe : BCTransport.PIPES) {
            requireRecipe(helper, recipes.byKey(BuildCraft.id(pipe.registryName())).orElse(null), pipe.registryName(), pipe.registryName());
        }

        CraftingContainer grid = grid();
        for (int slot : new int[] {1, 3, 5, 7}) grid.setItem(slot, new ItemStack(Items.IRON_INGOT));
        grid.setItem(4, new ItemStack(BCCore.GEAR_STONE.get()));
        CraftingRecipe gear = (CraftingRecipe) recipes.byKey(BuildCraft.id("gear_iron")).orElseThrow();
        require(helper, gear.matches(grid, helper.getLevel()) && gear.assemble(grid).is(BCCore.GEAR_IRON.get()),
                "four iron ingots around a stone gear must craft an iron gear");

        for (String material : List.of("wood", "stone", "iron", "gold", "diamond")) {
            Item item = Registry.ITEM.get(BuildCraft.id("gear_" + material));
            require(helper, new ItemStack(item).is(BCCore.commonGearTag(material)) && new ItemStack(item).is(BCCore.forgeGearTag(material)),
                    material + " gear must be in c:" + material + "_gears and forge:gears/" + material);
        }
        ItemStack wrench = new ItemStack(BCCore.WRENCH.get());
        require(helper, wrench.is(Wrenches.COMMON_WRENCHES) && wrench.is(Wrenches.FORGE_WRENCHES), "wrench must carry both wrench tags");
        require(helper, new ItemStack(Items.IRON_INGOT).is(itemTag("c", "iron_ingots")) && new ItemStack(Items.IRON_INGOT).is(itemTag("forge", "ingots/iron")),
                "iron ingots must resolve under both namespaces");

        CraftingContainer upgrade = grid();
        upgrade.setItem(0, new ItemStack(BCTransport.STONE_ITEM.item()));
        upgrade.setItem(1, new ItemStack(BCTransport.WATERPROOF.get()));
        CraftingRecipe fluidPipe = (CraftingRecipe) recipes.byKey(BuildCraft.id(BCTransport.STONE_FLUID.registryName())).orElseThrow();
        require(helper, fluidPipe.matches(upgrade, helper.getLevel()), "a stone pipe and sealant must make a stone fluid pipe");

        CraftingContainer diamondUpgrade = grid();
        diamondUpgrade.setItem(0, new ItemStack(BCTransport.DIAMOND_ITEM.item()));
        diamondUpgrade.setItem(1, new ItemStack(BCTransport.WATERPROOF.get()));
        CraftingRecipe diamondFluid = (CraftingRecipe) recipes.byKey(BuildCraft.id(BCTransport.DIAMOND_FLUID.registryName())).orElseThrow();
        require(helper, diamondFluid.matches(diamondUpgrade, helper.getLevel()),
                "a diamond pipe and sealant must make a diamond fluid pipe");

        for (PipeDefinition[] pair : new PipeDefinition[][] {{BCTransport.IRON_ITEM, BCTransport.IRON_POWER},
                {BCTransport.DIAMOND_ITEM, BCTransport.DIAMOND_POWER}}) {
            CraftingContainer kinesis = grid();
            kinesis.setItem(0, new ItemStack(pair[0].item()));
            kinesis.setItem(1, new ItemStack(Items.REDSTONE));
            CraftingRecipe recipe = (CraftingRecipe) recipes.byKey(BuildCraft.id(pair[1].registryName())).orElseThrow();
            require(helper, recipe.matches(kinesis, helper.getLevel()) && recipe.assemble(kinesis).is(pair[1].item()),
                    "a " + pair[0].registryName() + " and redstone must make a " + pair[1].registryName());
        }

        requirePipeRow(helper, BCTransport.OBSIDIAN_ITEM, Items.OBSIDIAN, Items.OBSIDIAN, "obsidian, glass and obsidian");
        requirePipeRow(helper, BCTransport.LAPIS_ITEM, Items.LAPIS_BLOCK, Items.LAPIS_BLOCK, "two lapis blocks either side of glass");
        requirePipeRow(helper, BCTransport.DAIZULI_ITEM, Items.LAPIS_BLOCK, Items.DIAMOND, "a lapis block, glass and a diamond");
        helper.succeed();
    }

    /** {@code left}, glass and {@code right} in a row must craft eight of {@code pipe}, as upstream's pipe recipes do. */
    private static void requirePipeRow(GameTestHelper helper, PipeDefinition pipe, Item left, Item right, String description) {
        CraftingContainer row = grid();
        row.setItem(0, new ItemStack(left));
        row.setItem(1, new ItemStack(Items.GLASS));
        row.setItem(2, new ItemStack(right));
        CraftingRecipe recipe = (CraftingRecipe) helper.getLevel().getRecipeManager().byKey(BuildCraft.id(pipe.registryName())).orElseThrow();
        ItemStack made = recipe.matches(row, helper.getLevel()) ? recipe.assemble(row) : ItemStack.EMPTY;
        require(helper, made.is(pipe.item()) && made.getCount() == 8, description + " must make eight " + pipe.registryName() + ", made " + made);
    }

    private static void requireRecipe(GameTestHelper helper, Recipe<?> recipe, String id, String result) {
        require(helper, recipe != null, "missing recipe buildcraft:" + id);
        ResourceLocation output = Registry.ITEM.getKey(recipe.getResultItem().getItem());
        require(helper, output.equals(BuildCraft.id(result)), "recipe " + id + " must make buildcraft:" + result + ", makes " + output);
    }

    private static TagKey<Item> itemTag(String namespace, String path) {
        return TagKey.create(Registry.ITEM_REGISTRY, new ResourceLocation(namespace, path));
    }

    private static CraftingContainer grid() {
        AbstractContainerMenu menu = new AbstractContainerMenu(null, -1) {
            @Override public boolean stillValid(Player player) { return true; }
            @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
        };
        return new CraftingContainer(menu, 3, 3);
    }

    /** A mining well over bedrock, so it holds the power it receives instead of spending it on digging. */
    static MiningWellBlockEntity idleMiningWell(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos.below(), Blocks.BEDROCK);
        helper.setBlock(pos, BCFactory.MINING_WELL.get());
        return tile(helper, pos, MiningWellBlockEntity.class);
    }

    // Pipe pluggables and structure pipes.

    private static boolean connected(GameTestHelper helper, BlockPos pos, Direction face) {
        return helper.getBlockState(pos).getValue(PipeBlock.SIDES.get(face)).isConnected();
    }

    /** Right-clicks the middle of a pipe block's {@code face} holding {@code held}, as {@code use} would. */
    static InteractionResult useOn(GameTestHelper helper, Player player, BlockPos pos, Direction face, ItemStack held) {
        return useAt(helper, player, pos, face, 0.5, held);
    }

    /** Right-clicks a pipe block {@code offset} blocks from its middle towards {@code face}, as {@code use} would. */
    static InteractionResult useAt(GameTestHelper helper, Player player, BlockPos pos, Direction face, double offset, ItemStack held) {
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        BlockPos absolute = helper.absolutePos(pos);
        Vec3 location = Vec3.atCenterOf(absolute).add(face.getStepX() * offset, face.getStepY() * offset, face.getStepZ() * offset);
        BlockHitResult hit = new BlockHitResult(location, face, absolute, false);
        return helper.getBlockState(pos).use(helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
    }

    /**
     * Stands {@code player} three blocks east of {@code pos}, looking west at the middle of its block. Picks follow
     * the head, which a ticking player turns with its body; these players never tick.
     */
    private static void lookWestAt(GameTestHelper helper, Player player, BlockPos pos) {
        Vec3 centre = Vec3.atCenterOf(helper.absolutePos(pos));
        player.moveTo(centre.x + 3, centre.y - EYE_HEIGHT, centre.z, 90, 0);
        player.setYHeadRot(90);
    }

    /**
     * A pipe plug placed by right-clicking a connected face stops both pipes connecting through it, hides the
     * pipe's item port there and adds its box to the pipe's shape; clients get it and saving keeps it. Breaking
     * the plug while looking at it takes off only the plug, which drops, through the shared hook and through the
     * server's game mode; breaking the pipe itself in creative takes its plugs without drops. A broken pipe
     * drops its plugs.
     */
    public static void plugBlocksConnectionsAndBreaksOff(GameTestHelper helper) {
        BlockPos a = new BlockPos(2, 2, 2), b = new BlockPos(3, 2, 2), dropper = new BlockPos(5, 2, 6);
        PipeBlockEntity pipe = pipe(helper, a, BCTransport.STONE_ITEM);
        pipe(helper, b, BCTransport.STONE_ITEM);
        Player player = helper.makeMockPlayer();
        BlockPos absolute = helper.absolutePos(a);

        helper.runAfterDelay(2, () -> {
            require(helper, connected(helper, a, Direction.EAST) && connected(helper, b, Direction.WEST), "two stone pipes connect");
            require(helper, pipe.itemPort(Direction.EAST) != null, "an item pipe takes items on a connected face");
            ItemStack plugs = new ItemStack(BCTransport.PLUG_BLOCKER.get(), 2);
            require(helper, useOn(helper, player, a, Direction.EAST, plugs) == InteractionResult.CONSUME,
                    "right-clicking a face with a pipe plug places it");
            require(helper, plugs.getCount() == 1 && pipe.getPluggable(Direction.EAST) instanceof BlockerPluggable && pipe.isBlocked(Direction.EAST),
                    "placing a plug uses one item");
        });
        helper.runAfterDelay(4, () -> {
            require(helper, !connected(helper, a, Direction.EAST) && !connected(helper, b, Direction.WEST),
                    "a plug stops both pipes connecting through it");
            require(helper, pipe.itemPort(Direction.EAST) == null && pipe.itemPort(Direction.WEST) != null, "a plug hides the pipe's port on its face only");
            VoxelShape shape = helper.getBlockState(a).getShape(helper.getLevel(), absolute);
            require(helper, Math.abs(shape.max(Direction.Axis.X) - 14 / 16.0) < 1e-6 && Math.abs(shape.min(Direction.Axis.X) - 4 / 16.0) < 1e-6,
                    "the plug's box, 2 to 4 pixels in from the east face, joins the pipe's shape");
            require(helper, PipeBlock.pluggableAt(pipe, Vec3.atLowerCornerOf(absolute).add(13 / 16.0, 0.5, 0.5)) == Direction.EAST
                    && PipeBlock.pluggableAt(pipe, Vec3.atCenterOf(absolute)) == null, "a hit on the plug's box picks the plug, a hit on the pipe does not");
            CompoundTag update = pipe.getUpdateTag().getCompound("plugs");
            require(helper, update.getCompound("east").getString("id").equals("buildcraft:blocker") && update.size() == 1,
                    "clients get the plug by face, got " + update);
            PipeBlockEntity loaded = new PipeBlockEntity(absolute, helper.getBlockState(a));
            loaded.load(pipe.saveWithoutMetadata());
            require(helper, loaded.getPluggable(Direction.EAST) instanceof BlockerPluggable && loaded.isBlocked(Direction.EAST)
                    && loaded.getPluggable(Direction.WEST) == null, "saving keeps the plug");

            helper.setBlock(b, Blocks.AIR);
            lookWestAt(helper, player, a);
            require(helper, PipeBlock.onPlayerDestroy(helper.getLevel(), absolute, player), "breaking while looking at the plug hits the plug");
            require(helper, pipe.getPluggable(Direction.EAST) == null && helper.getBlockState(a).getBlock() instanceof PipeBlock
                    && BuilderTests.dropped(helper, a, new ItemStack(BCTransport.PLUG_BLOCKER.get())), "breaking the plug drops it and keeps the pipe");
            require(helper, !PipeBlock.onPlayerDestroy(helper.getLevel(), absolute, player), "without a plug in sight the pipe itself breaks");

            pipe.replacePluggable(Direction.EAST, BCTransport.BLOCKER.create(pipe, Direction.EAST));
            ServerPlayer serverPlayer = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "plugs"), null);
            lookWestAt(helper, serverPlayer, a);
            require(helper, !serverPlayer.gameMode.destroyBlock(absolute) && helper.getBlockState(a).getBlock() instanceof PipeBlock
                    && pipe.getPluggable(Direction.EAST) == null && BuilderTests.dropped(helper, a, new ItemStack(BCTransport.PLUG_BLOCKER.get(), 2)),
                    "the server's block breaking takes off only the plug");

            pipe.replacePluggable(Direction.NORTH, BCTransport.BLOCKER.create(pipe, Direction.NORTH));
            serverPlayer.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
            require(helper, serverPlayer.gameMode.destroyBlock(absolute) && helper.getBlockState(a).isAir(), "breaking the pipe itself in creative removes it");
            require(helper, BuilderTests.dropped(helper, a, new ItemStack(BCTransport.PLUG_BLOCKER.get(), 2)), "a creative break drops no plugs");

            PipeBlockEntity other = pipe(helper, dropper, BCTransport.STONE_ITEM);
            other.replacePluggable(Direction.UP, BCTransport.BLOCKER.create(other, Direction.UP));
            helper.setBlock(dropper, Blocks.AIR);
            require(helper, BuilderTests.dropped(helper, dropper, new ItemStack(BCTransport.PLUG_BLOCKER.get())), "a broken pipe drops its plugs");
            helper.succeed();
        });
    }

    /**
     * A plug on a wooden pipe's top keeps a redstone engine above from powering it. A power adapter in its place
     * blocks the face just the same but hands the engine's power to the pipe, which pulls cobblestone out of a
     * chest. The adapter only goes on faces where the pipe's behaviour takes redstone engine power.
     */
    public static void powerAdaptorDrivesWoodenPipeThroughBlockedFace(GameTestHelper helper) {
        BlockPos woodPos = new BlockPos(1, 2, 1), stonePos = new BlockPos(2, 2, 1);
        ChestBlockEntity source = chest(helper, new BlockPos(0, 2, 1), new ItemStack(Items.COBBLESTONE, 8));
        PipeBlockEntity wood = pipe(helper, woodPos, BCTransport.WOOD_ITEM);
        pipe(helper, stonePos, BCTransport.STONE_ITEM);
        pipe(helper, new BlockPos(3, 2, 1), BCTransport.GOLD_ITEM);
        ChestBlockEntity target = chest(helper, new BlockPos(4, 2, 1));
        wood.replacePluggable(Direction.UP, BCTransport.BLOCKER.create(wood, Direction.UP));
        EngineBlockEntity engine = engine(helper, woodPos.above(), BCEnergy.REDSTONE_ENGINE.get(), Direction.DOWN, EngineBlockEntity.class);
        helper.setBlock(woodPos.above(2), Blocks.REDSTONE_BLOCK);
        Player player = helper.makeMockPlayer();

        ItemStack adaptor = new ItemStack(BCTransport.PLUG_POWER_ADAPTOR.get());
        require(helper, useOn(helper, player, stonePos, Direction.UP, adaptor) == InteractionResult.PASS && adaptor.getCount() == 1,
                "a power adapter does not go on a stone pipe");

        helper.runAfterDelay(100, () -> {
            require(helper, wood.energyPort(Direction.UP) == null && count(source, Items.COBBLESTONE) == 8 && !engine.isPumping(),
                    "a plug keeps the engine from powering the pipe");
            wood.removePluggable(Direction.UP, new ArrayList<>());
            require(helper, useOn(helper, player, woodPos, Direction.UP, adaptor) == InteractionResult.CONSUME && adaptor.isEmpty(),
                    "a power adapter goes on a wooden pipe");
            EnergyPort port = wood.energyPort(Direction.UP);
            require(helper, wood.getPluggable(Direction.UP) instanceof PowerAdaptorPluggable && wood.isBlocked(Direction.UP)
                    && port != null && port.acceptsRedstoneEngine(), "the adapter hands the face's power to the wooden pipe");
            helper.succeedWhen(() -> {
                require(helper, !connected(helper, woodPos, Direction.UP), "the adapter blocks the face");
                require(helper, count(target, Items.COBBLESTONE) >= 1, "cobblestone must reach the far chest; engine pumping " + engine.isPumping());
                require(helper, count(source, Items.COBBLESTONE) + count(target, Items.COBBLESTONE) <= 8, "items must not duplicate");
            });
        });
    }

    /** Structure pipes connect to each other and to nothing else, and offer no ports. */
    public static void structurePipesConnectOnlyToEachOther(GameTestHelper helper) {
        BlockPos first = new BlockPos(2, 2, 2), second = new BlockPos(3, 2, 2), stone = new BlockPos(2, 2, 3);
        PipeBlockEntity structure = pipe(helper, first, BCTransport.STRUCTURE);
        pipe(helper, second, BCTransport.STRUCTURE);
        pipe(helper, stone, BCTransport.STONE_ITEM);
        chest(helper, new BlockPos(1, 2, 2));

        helper.runAfterDelay(2, () -> {
            require(helper, connected(helper, first, Direction.EAST) && connected(helper, second, Direction.WEST), "structure pipes connect to each other");
            require(helper, !connected(helper, first, Direction.SOUTH) && !connected(helper, stone, Direction.NORTH),
                    "a structure pipe and a stone pipe do not connect");
            require(helper, !connected(helper, first, Direction.WEST), "a structure pipe does not connect to a chest");
            for (Direction face : Direction.values()) {
                require(helper, structure.itemPort(face) == null && structure.fluidPort(face) == null && structure.energyPort(face) == null,
                        "a structure pipe has no ports on " + face);
            }
            helper.succeed();
        });
    }
}
