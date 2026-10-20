package ports.buildcraft.gametest;

import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.builders.BCBuilders;
import ports.buildcraft.builders.QuarryBlockEntity;
import ports.buildcraft.core.BCCore;
import ports.buildcraft.core.Wrenches;
import ports.buildcraft.energy.BCEnergy;
import ports.buildcraft.energy.CombustionEngineBlockEntity;
import ports.buildcraft.energy.EngineBlock;
import ports.buildcraft.energy.EngineBlockEntity;
import ports.buildcraft.energy.PowerStage;
import ports.buildcraft.energy.StirlingEngineBlockEntity;
import ports.buildcraft.factory.BCFactory;
import ports.buildcraft.factory.MinerBlockEntity;
import ports.buildcraft.factory.MiningWellBlockEntity;
import ports.buildcraft.factory.PumpBlockEntity;
import ports.buildcraft.factory.TankBlockEntity;
import ports.buildcraft.lib.Power;
import ports.buildcraft.platform.FluidPort;
import ports.buildcraft.platform.Platform;
import ports.buildcraft.transport.BCTransport;
import ports.buildcraft.transport.PipeBlockEntity;
import ports.buildcraft.transport.PipeDefinition;
import ports.buildcraft.transport.behaviour.DirectionalBehaviour;
import ports.buildcraft.transport.flow.ItemFlow;

/**
 * Loader-neutral GameTest bodies. Forge and Fabric each wrap these in their own test classes so
 * that the same gameplay assertions run against both loader adapters.
 */
public final class BCTests {
    /** The shared 9x8x9 template: a stone floor at y = 0 under open air. */
    public static final String TEMPLATE = "arena";

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
        requireViscosity(helper, BCEnergy.OIL.source(), 2000);
        requireViscosity(helper, BCEnergy.FUEL_LIGHT.source(), 600);
        helper.succeed();
    }

    private static void requireViscosity(GameTestHelper helper, Fluid fluid, int expected) {
        int actual = Platform.get().viscosity(fluid);
        require(helper, actual == expected, Registry.FLUID.getKey(fluid) + " viscosity must be " + expected + ", got " + actual);
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
        require(helper, port.fill(BCEnergy.FUEL_LIGHT.source(), 1000, false) == 1000, "light fuel must fill the fuel tank");
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
                helper.assertEntityNotPresent(EntityType.ITEM);
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

    // ---- data ----------------------------------------------------------------------------------

    private static final List<String> RECIPES = List.of("gear_wood", "gear_stone", "gear_iron", "gear_gold", "gear_diamond", "wrench",
            "redstone_engine", "stirling_engine", "combustion_engine", "tank", "pump", "mining_well", "quarry", "waterproof");

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
        helper.succeed();
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
}
