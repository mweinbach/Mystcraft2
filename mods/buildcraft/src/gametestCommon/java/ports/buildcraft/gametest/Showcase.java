package ports.buildcraft.gametest;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import ports.buildcraft.builders.BCBuilders;
import ports.buildcraft.builders.QuarryBlock;
import ports.buildcraft.builders.QuarryBlockEntity;
import ports.buildcraft.energy.BCEnergy;
import ports.buildcraft.energy.CombustionEngineBlockEntity;
import ports.buildcraft.energy.EngineBlock;
import ports.buildcraft.energy.OilBiomes;
import ports.buildcraft.energy.OilWellFeature;
import ports.buildcraft.energy.StirlingEngineBlockEntity;
import ports.buildcraft.factory.BCFactory;
import ports.buildcraft.factory.DistillerBlock;
import ports.buildcraft.factory.DistillerBlockEntity;
import ports.buildcraft.factory.HeatExchangerBlock;
import ports.buildcraft.factory.HeatExchangerBlockEntity;
import ports.buildcraft.factory.TankBlockEntity;
import ports.buildcraft.transport.BCTransport;
import ports.buildcraft.transport.PipeBlockEntity;
import ports.buildcraft.transport.PipeDefinition;
import ports.buildcraft.transport.behaviour.DiamondItemBehaviour;

/**
 * Development-only demonstration layout for screenshots: every implemented machine running on real
 * engine power, built on a stone slab. Never shipped in a production JAR.
 */
public final class Showcase {
    private static final int MIN_X = -4, MAX_X = 32, MIN_Z = -4, MAX_Z = 22, DEPTH = 10, HEIGHT = 12;

    private final ServerLevel level;
    private final BlockPos origin;
    /** Engines read redstone only when placed by a player or when a neighbour changes, so power goes in last. */
    private final List<BlockPos> redstone = new ArrayList<>();
    private final List<BlockPos> levers = new ArrayList<>();

    private Showcase(ServerLevel level, BlockPos origin) {
        this.level = level;
        this.origin = origin;
    }

    /** Builds the layout with its floor surface at {@code origin}'s y and its first engine at {@code origin}. */
    public static void build(ServerLevel level, BlockPos origin) {
        new Showcase(level, origin).build();
    }

    /**
     * Builds an engine close-up bench: one engine of each type, then combustion engines preset to each
     * heat stage, every engine driving a pump over an infinite water pool so that its piston keeps stroking.
     */
    public static void buildEngineBench(ServerLevel level, BlockPos origin) {
        new Showcase(level, origin).engineBench();
    }

    /**
     * Builds a running refinery: a combustion engine at full power distilling crude oil, a Stirling engine
     * distilling hot oil slowly, and heat exchanger rows of three and five. Exchangers form a row on their
     * first tick and drop fluid given before that, so the returned task fills them and must run a few ticks later.
     */
    public static Runnable buildRefinery(ServerLevel level, BlockPos origin) {
        return new Showcase(level, origin).refinery();
    }

    /** The oil well of {@code type}, or of any type when null, nearest to chunk {@code (cx, cz)} within {@code radius} chunks. */
    @Nullable
    public static OilWellFeature.Well nearestWell(ServerLevel level, int cx, int cz, int radius, @Nullable OilWellFeature.WellType type) {
        for (int r = 0; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    OilWellFeature.Well well = OilWellFeature.well(level.getSeed(), cx + dx, cz + dz, level.getMinBuildHeight(),
                            pos -> level.getBiome(pos).is(OilBiomes.EXCESSIVE_OIL));
                    if (well != null && (type == null || well.type() == type)) return well;
                }
            }
        }
        return null;
    }

    private Runnable refinery() {
        prepare(-3, 14, -3, 8);
        redstone(0, 0, 0);
        CombustionEngineBlockEntity combustion = engine(1, 0, 0, BCEnergy.COMBUSTION_ENGINE.get(), Direction.EAST);
        combustion.fluidPort(Direction.UP).fill(BCEnergy.FUEL_LIGHT.heat(0).source(), CombustionEngineBlockEntity.MAX_FLUID, false);
        combustion.fluidPort(Direction.UP).fill(Fluids.WATER, CombustionEngineBlockEntity.MAX_FLUID, false);
        distiller(2, 0, BCEnergy.OIL.heat(0).source());

        redstone(0, 0, 4);
        StirlingEngineBlockEntity stirling = engine(1, 0, 4, BCEnergy.STIRLING_ENGINE.get(), Direction.EAST);
        stirling.setItem(0, new ItemStack(Items.COAL_BLOCK, 64));
        distiller(2, 4, BCEnergy.OIL.heat(1).source());

        HeatExchangerBlockEntity[] small = heatExchangerRow(7, 0, 3);
        HeatExchangerBlockEntity[] large = heatExchangerRow(10, 0, 5);
        for (BlockPos pos : redstone) level.setBlock(pos, Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        return () -> {
            for (HeatExchangerBlockEntity[] row : List.of(small, large)) {
                row[0].fluidPort(Direction.DOWN).fill(BCEnergy.OIL.heat(0).source(), HeatExchangerBlockEntity.CAPACITY, false);
                row[row.length - 1].fluidPort(HeatExchangerBlock.toEnd(Direction.WEST)).fill(Fluids.LAVA, HeatExchangerBlockEntity.CAPACITY, false);
            }
        };
    }

    private void distiller(int x, int z, Fluid input) {
        set(x, 0, z, BCFactory.DISTILLER.get().defaultBlockState().setValue(DistillerBlock.FACING, Direction.WEST));
        if (level.getBlockEntity(at(x, 0, z)) instanceof DistillerBlockEntity distiller) {
            distiller.fluidPort(Direction.WEST).fill(input, DistillerBlockEntity.CAPACITY, false);
        }
    }

    /** Exchangers facing west, from the start at {@code z} southwards to the end. */
    private HeatExchangerBlockEntity[] heatExchangerRow(int x, int z, int length) {
        BlockState state = BCFactory.HEAT_EXCHANGER.get().defaultBlockState().setValue(HeatExchangerBlock.FACING, Direction.WEST);
        HeatExchangerBlockEntity[] row = new HeatExchangerBlockEntity[length];
        for (int i = 0; i < length; i++) {
            set(x, 0, z + i, state);
            row[i] = (HeatExchangerBlockEntity) level.getBlockEntity(at(x, 0, z + i));
        }
        return row;
    }

    private void engineBench() {
        prepare(-3, 21, -3, 7);
        lever(0, 0);
        engine(0, 0, 0, BCEnergy.REDSTONE_ENGINE.get(), Direction.EAST);
        pumpOverPool(1, 0);
        lever(4, 0);
        StirlingEngineBlockEntity stirling = engine(4, 0, 0, BCEnergy.STIRLING_ENGINE.get(), Direction.EAST);
        stirling.setItem(0, new ItemStack(Items.COAL_BLOCK, 64));
        pumpOverPool(5, 0);
        lever(8, 0);
        combustion(8, 0, 0, Direction.EAST);
        pumpOverPool(9, 0);

        // A kinesis pipe keeps the pump from pushing water into the engine, where it would cool the engine to ideal heat.
        double[] heats = {30, 100, 160, 195, 240};
        for (int i = 0; i < heats.length; i++) {
            int x = 4 * i;
            lever(x, 4);
            CombustionEngineBlockEntity engine = engine(x, 0, 4, BCEnergy.COMBUSTION_ENGINE.get(), Direction.EAST);
            engine.fluidPort(Direction.UP).fill(BCEnergy.FUEL_LIGHT.heat(0).source(), CombustionEngineBlockEntity.MAX_FLUID, false);
            BCTests.presetHeat(engine, heats[i]);
            pipe(x + 1, 0, 4, BCTransport.WOOD_POWER);
            pumpOverPool(x + 2, 4);
        }
        BlockState lever = Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR)
                .setValue(LeverBlock.FACING, Direction.EAST).setValue(LeverBlock.POWERED, true);
        for (BlockPos pos : levers) level.setBlock(pos, lever, Block.UPDATE_ALL);
    }

    /** A switched-on lever on the floor behind an engine's base plate. */
    private void lever(int x, int z) {
        levers.add(at(x - 1, 0, z));
    }

    /** A pump above a 2x2 water pool, which refills, and a void pipe behind it taking whatever it pumps. */
    private void pumpOverPool(int x, int z) {
        set(x, -1, z, Blocks.AIR);
        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = -1; dz <= 0; dz++) {
                set(x + dx, -2, z + dz, Blocks.WATER);
                set(x + dx, -3, z + dz, Blocks.WATER);
            }
        }
        set(x, 0, z, BCFactory.PUMP.get());
        pipe(x, 0, z - 1, BCTransport.VOID_FLUID);
    }

    private void build() {
        prepare(MIN_X, MAX_X, MIN_Z, MAX_Z);
        engines();
        itemPipes();
        fluidPipes();
        kinesisPipes();
        quarry();
        for (BlockPos pos : redstone) level.setBlock(pos, Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
    }

    private void prepare(int minX, int maxX, int minZ, int maxZ) {
        GameRules rules = level.getGameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, level.getServer());
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, level.getServer());
        level.setDayTime(6000);
        level.setWeatherParameters(6000, 0, false, false);

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = -DEPTH; y < HEIGHT; y++) {
                    Block block = y >= 0 ? Blocks.AIR : y == -1 ? Blocks.SMOOTH_STONE
                            : at(x, y, z).getY() <= level.getMinBuildHeight() ? Blocks.BEDROCK : Blocks.STONE;
                    set(x, y, z, block);
                }
            }
        }
        AABB area = new AABB(at(minX, -DEPTH, minZ), at(maxX, HEIGHT, maxZ)).inflate(48);
        level.getEntitiesOfClass(Mob.class, area).forEach(Entity::discard);
    }

    /** Redstone, Stirling and combustion engines, each driving a machine. */
    private void engines() {
        redstone(-1, 0, 0);
        engine(0, 0, 0, BCEnergy.REDSTONE_ENGINE.get(), Direction.EAST);
        set(1, -1, 0, Blocks.WATER);
        set(1, -2, 0, Blocks.WATER);
        set(1, 0, 0, BCFactory.PUMP.get());
        tank(2, 0, 0, null, 0);

        redstone(3, 0, 0);
        StirlingEngineBlockEntity stirling = engine(4, 0, 0, BCEnergy.STIRLING_ENGINE.get(), Direction.EAST);
        stirling.setItem(0, new ItemStack(Items.COAL, 64));
        set(5, 0, 0, BCFactory.MINING_WELL.get());
        chest(6, 0, 0);

        redstone(7, 0, 0);
        combustion(8, 0, 0, Direction.EAST);
        set(9, 0, 0, BCFactory.MINING_WELL.get());
        chest(10, 0, 0);
    }

    /** A redstone engine pulses a wooden pipe; a diamond pipe sorts cobblestone east and the rest south. */
    private void itemPipes() {
        chest(0, 0, 4, new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.IRON_ORE, 64), new ItemStack(Items.OAK_LOG, 64),
                new ItemStack(Items.REDSTONE, 64), new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.DIAMOND, 64));
        pipe(1, 0, 4, BCTransport.WOOD_ITEM);
        engine(1, 1, 4, BCEnergy.REDSTONE_ENGINE.get(), Direction.DOWN);
        redstone(1, 2, 4);
        pipe(2, 0, 4, BCTransport.COBBLESTONE_ITEM);
        pipe(3, 0, 4, BCTransport.GOLD_ITEM);
        PipeBlockEntity diamond = pipe(4, 0, 4, BCTransport.DIAMOND_ITEM);
        pipe(5, 0, 4, BCTransport.STONE_ITEM);
        pipe(6, 0, 4, BCTransport.GOLD_ITEM);
        pipe(7, 0, 4, BCTransport.QUARTZ_ITEM);
        chest(8, 0, 4);
        pipe(4, 0, 5, BCTransport.SANDSTONE_ITEM);
        pipe(4, 0, 6, BCTransport.CLAY_ITEM);
        chest(4, 0, 7);
        if (diamond.behaviour() instanceof DiamondItemBehaviour filters) {
            filters.filters.setItem(DiamondItemBehaviour.FILTERS_PER_SIDE * Direction.EAST.ordinal(), new ItemStack(Items.COBBLESTONE));
            filters.filters.setItem(DiamondItemBehaviour.FILTERS_PER_SIDE * Direction.SOUTH.ordinal(), new ItemStack(Items.IRON_ORE));
            filters.filters.setItem(DiamondItemBehaviour.FILTERS_PER_SIDE * Direction.SOUTH.ordinal() + 1, new ItemStack(Items.OAK_LOG));
            filters.filters.setItem(DiamondItemBehaviour.FILTERS_PER_SIDE * Direction.SOUTH.ordinal() + 2, new ItemStack(Items.REDSTONE));
            filters.filters.setItem(DiamondItemBehaviour.FILTERS_PER_SIDE * Direction.SOUTH.ordinal() + 3, new ItemStack(Items.DIAMOND));
        }
    }

    /** Wooden fluid pipes pull lava, water and oil out of tanks into tanks at the far end. */
    private void fluidPipes() {
        fluidLine(9, Fluids.LAVA, BCTransport.COBBLESTONE_FLUID, BCTransport.SANDSTONE_FLUID, BCTransport.GOLD_FLUID,
                BCTransport.GOLD_FLUID, BCTransport.SANDSTONE_FLUID);
        fluidLine(11, Fluids.WATER, BCTransport.STONE_FLUID, BCTransport.GOLD_FLUID, BCTransport.QUARTZ_FLUID,
                BCTransport.QUARTZ_FLUID, BCTransport.SANDSTONE_FLUID);
        fluidLine(13, BCEnergy.OIL.heat(0).source(), BCTransport.STONE_FLUID, BCTransport.SANDSTONE_FLUID, BCTransport.GOLD_FLUID,
                BCTransport.COBBLESTONE_FLUID, BCTransport.SANDSTONE_FLUID);
    }

    /** Cobblestone, stone and quartz pipes never connect to each other, so {@code pipes} keeps them apart. */
    private void fluidLine(int z, Fluid fluid, PipeDefinition... pipes) {
        tank(0, 0, z, fluid, TankBlockEntity.CAPACITY);
        tank(0, 1, z, fluid, TankBlockEntity.CAPACITY);
        pipe(1, 0, z, BCTransport.WOOD_FLUID);
        engine(1, 1, z, BCEnergy.REDSTONE_ENGINE.get(), Direction.DOWN);
        redstone(1, 2, z);
        for (int i = 0; i < pipes.length; i++) pipe(2 + i, 0, z, pipes[i]);
        tank(7, 0, z, null, 0);
        tank(7, 1, z, null, 0);
    }

    /** A Stirling engine's power runs through every kinesis pipe tier into a mining well. */
    private void kinesisPipes() {
        int z = 17;
        redstone(-1, 0, z);
        StirlingEngineBlockEntity stirling = engine(0, 0, z, BCEnergy.STIRLING_ENGINE.get(), Direction.EAST);
        stirling.setItem(0, new ItemStack(Items.COAL_BLOCK, 64));
        pipe(1, 0, z, BCTransport.WOOD_POWER);
        pipe(2, 0, z, BCTransport.COBBLESTONE_POWER);
        pipe(3, 0, z, BCTransport.SANDSTONE_POWER);
        pipe(4, 0, z, BCTransport.STONE_POWER);
        pipe(5, 0, z, BCTransport.GOLD_POWER);
        pipe(6, 0, z, BCTransport.QUARTZ_POWER);
        set(7, 0, z, BCFactory.MINING_WELL.get());
        chest(8, 0, z);
    }

    /** A quarry powered by two combustion engines, with its default 11x11 frame east of it. */
    private void quarry() {
        BlockPos pos = at(16, 0, 6);
        level.setBlock(pos, BCBuilders.QUARRY.get().defaultBlockState().setValue(QuarryBlock.FACING, Direction.WEST), Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof QuarryBlockEntity quarry) quarry.setArea(pos.offset(1, 0, -5), pos.offset(11, 4, 5));
        redstone(14, 0, 6);
        combustion(15, 0, 6, Direction.EAST);
        redstone(16, 2, 6);
        combustion(16, 1, 6, Direction.DOWN);
        chest(16, 0, 7);
        chest(16, 0, 8);
    }

    private void combustion(int x, int y, int z, Direction facing) {
        CombustionEngineBlockEntity engine = engine(x, y, z, BCEnergy.COMBUSTION_ENGINE.get(), facing);
        engine.fluidPort(Direction.UP).fill(BCEnergy.FUEL_LIGHT.heat(0).source(), CombustionEngineBlockEntity.MAX_FLUID, false);
        engine.fluidPort(Direction.UP).fill(Fluids.WATER, CombustionEngineBlockEntity.MAX_FLUID, false);
    }

    @SuppressWarnings("unchecked")
    private <T extends BlockEntity> T engine(int x, int y, int z, EngineBlock block, Direction facing) {
        set(x, y, z, block.defaultBlockState().setValue(EngineBlock.FACING, facing));
        return (T) level.getBlockEntity(at(x, y, z));
    }

    private PipeBlockEntity pipe(int x, int y, int z, PipeDefinition definition) {
        set(x, y, z, definition.block());
        return (PipeBlockEntity) level.getBlockEntity(at(x, y, z));
    }

    private void tank(int x, int y, int z, Fluid fluid, int amount) {
        set(x, y, z, BCFactory.TANK.get());
        if (fluid != null && level.getBlockEntity(at(x, y, z)) instanceof TankBlockEntity tank) {
            tank.fluidPort(Direction.UP).fill(fluid, amount, false);
        }
    }

    private void chest(int x, int y, int z, ItemStack... contents) {
        set(x, y, z, Blocks.CHEST);
        if (level.getBlockEntity(at(x, y, z)) instanceof ChestBlockEntity chest) {
            for (int i = 0; i < contents.length; i++) chest.setItem(i, contents[i]);
        }
    }

    private void redstone(int x, int y, int z) {
        redstone.add(at(x, y, z));
    }

    private void set(int x, int y, int z, Block block) {
        set(x, y, z, block.defaultBlockState());
    }

    private void set(int x, int y, int z, BlockState state) {
        level.setBlock(at(x, y, z), state, Block.UPDATE_ALL);
    }

    private BlockPos at(int x, int y, int z) {
        return origin.offset(x, y, z);
    }
}
