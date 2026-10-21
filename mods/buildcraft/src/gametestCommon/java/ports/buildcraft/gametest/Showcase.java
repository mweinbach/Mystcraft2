package ports.buildcraft.gametest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import ports.buildcraft.builders.BCBuilders;
import ports.buildcraft.builders.BuilderBlock;
import ports.buildcraft.builders.BuilderBlockEntity;
import ports.buildcraft.builders.ElectronicLibraryBlockEntity;
import ports.buildcraft.builders.FacingMachineBlock;
import ports.buildcraft.builders.QuarryBlock;
import ports.buildcraft.builders.QuarryBlockEntity;
import ports.buildcraft.builders.ReplacerBlockEntity;
import ports.buildcraft.builders.SchematicSingleItem;
import ports.buildcraft.builders.snapshot.EnumSnapshotType;
import ports.buildcraft.builders.snapshot.SchematicBlockContext;
import ports.buildcraft.builders.snapshot.SchematicBlockManager;
import ports.buildcraft.core.BCCore;
import ports.buildcraft.core.MarkerBlock;
import ports.buildcraft.core.marker.MarkerCaches;
import ports.buildcraft.core.statements.BCCoreStatements;
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
import ports.buildcraft.silicon.AdvancedCraftingTableBlockEntity;
import ports.buildcraft.silicon.AssemblyTableBlockEntity;
import ports.buildcraft.silicon.BCSilicon;
import ports.buildcraft.silicon.EnumAssemblyRecipeState;
import ports.buildcraft.silicon.IntegrationTableBlockEntity;
import ports.buildcraft.silicon.LaserBlock;
import ports.buildcraft.silicon.gate.EnumGateLogic;
import ports.buildcraft.silicon.gate.EnumGateMaterial;
import ports.buildcraft.silicon.gate.EnumGateModifier;
import ports.buildcraft.silicon.gate.GateLogic;
import ports.buildcraft.silicon.gate.GateVariant;
import ports.buildcraft.silicon.gate.PluggableGate;
import ports.buildcraft.silicon.plug.LensPluggable;
import ports.buildcraft.silicon.plug.LensVariant;
import ports.buildcraft.statement.ActionWrapper;
import ports.buildcraft.statement.StatementParameterItemStack;
import ports.buildcraft.statement.TriggerWrapper;
import ports.buildcraft.transport.BCTransport;
import ports.buildcraft.transport.PipeBlockEntity;
import ports.buildcraft.transport.PipeDefinition;
import ports.buildcraft.transport.behaviour.DiamondItemBehaviour;
import ports.buildcraft.transport.pluggable.PluggableDefinition;
import ports.buildcraft.transport.statements.BCTransportStatements;
import ports.buildcraft.transport.wire.WirePart;

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

    /**
     * Builds a builder bench: a hut recorded as a blueprint and as a template, one builder rebuilding each beside it,
     * and a third builder paving a cobblestone road along path marks. Every builder runs on two combustion engines.
     */
    public static void buildBuilders(ServerLevel level, BlockPos origin) {
        new Showcase(level, origin).builders();
    }

    /**
     * Builds a library bench: a hut and a tower recorded as blueprints and templates, an electronic library copying
     * the hut blueprint down with a blank template waiting in {@code upIn}, a replacer holding the hut blueprint and
     * a stone brick schematic, and a chest of the other recordings, a deepslate brick schematic and a lantern schematic.
     */
    public static void buildLibrary(ServerLevel level, BlockPos origin) {
        new Showcase(level, origin).library();
    }

    /**
     * Builds a silicon bench: three lasers on combustion engines aimed at an assembly table making every chipset into a
     * chest, an advanced crafting table crafting pistons, and an integration table, for which no recipes ship.
     */
    public static void buildSilicon(ServerLevel level, BlockPos origin) {
        new Showcase(level, origin).silicon();
    }

    /**
     * Builds a pluggable bench: a redstone engine driving a wooden pipe through a power adapter, a pipe plug keeping
     * cobblestone out of a branch line, an arch of structure pipes and a stone pipe capped with plugs on five faces.
     */
    public static void buildPlugs(ServerLevel level, BlockPos origin) {
        new Showcase(level, origin).plugs();
    }

    /**
     * Builds a gate bench: a row of stone pipes with gates of every material and modifier on their south faces, every
     * other one on; two levers driving gated pipes that light a lamp through a repeater, one lever on and one off;
     * and an iron pipe whose gold gate sends cobblestone into a chest until it has no room for more and then east.
     */
    public static void buildGates(ServerLevel level, BlockPos origin) {
        new Showcase(level, origin).gates();
    }

    /**
     * Builds a pipe wire bench: two lines of stone pipes carrying red and blue wire along their south edges, where a
     * lever-driven gate at the west end sends a red pipe signal that a gate at the east end turns into redstone for a
     * lamp, one lever on and one off; behind them a row of pipes each ringed on top with one of the sixteen colours.
     */
    public static void buildWires(ServerLevel level, BlockPos origin) {
        new Showcase(level, origin).wires();
    }

    /**
     * Builds a lens bench: redstone engines pull cobblestone from the west and dirt from the north through a red and a
     * blue lens into a junction, whose red filter sends the red items east and blue filter the blue ones south; behind
     * them a row of pipes, each with one of the 17 lenses on top and the filter of that colour on its south face.
     */
    public static void buildLenses(ServerLevel level, BlockPos origin) {
        new Showcase(level, origin).lenses();
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

    private void builders() {
        prepare(-3, 20, -3, 23);
        hut(12, 0);
        ItemStack blueprint = BuilderTests.record(level, EnumSnapshotType.BLUEPRINT, at(12, 0, 0), new BlockPos(5, 5, 5), Direction.NORTH, "Hut");
        ItemStack template = BuilderTests.record(level, EnumSnapshotType.TEMPLATE, at(12, 0, 0), new BlockPos(5, 5, 5), Direction.NORTH, "Hut");
        set(19, 0, 20, Blocks.COBBLESTONE);
        ItemStack road = BuilderTests.record(level, EnumSnapshotType.TEMPLATE, at(19, 0, 20), new BlockPos(1, 1, 1), Direction.WEST, "Road");
        set(19, 0, 20, Blocks.AIR);

        poweredBuilder(0, 0, Direction.NORTH, blueprint, new ItemStack(Items.STONE_BRICKS, 64), new ItemStack(Items.OAK_LOG, 64),
                new ItemStack(Items.OAK_PLANKS, 64), new ItemStack(Items.GLASS_PANE, 64), new ItemStack(Items.SPRUCE_SLAB, 64), new ItemStack(Items.LANTERN, 4));
        poweredBuilder(0, 8, Direction.NORTH, template, new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.COBBLESTONE, 64));

        BlockState mark = BCCore.MARKER_PATH.get().defaultBlockState().setValue(MarkerBlock.FACING, Direction.UP);
        List<BlockPos> marks = List.of(at(2, 0, 18), at(11, 0, 18), at(11, 0, 22));
        for (BlockPos pos : marks) level.setBlock(pos, mark, Block.UPDATE_ALL);
        for (int i = 1; i < marks.size(); i++) MarkerCaches.PATH.getSubCache(level).tryConnect(marks.get(i - 1), marks.get(i));
        BuilderBlockEntity paver = builder(1, 0, 18, Direction.WEST);
        paver.setItem(1, new ItemStack(Items.COBBLESTONE, 64));
        // A path builder skips steps it cannot power at once, as upstream, so it starts charged.
        paver.battery().set(paver.battery().capacity());
        paver.setItem(BuilderBlockEntity.SLOT_SNAPSHOT, road);
        redstone(1, 0, 16);
        combustion(1, 0, 17, Direction.SOUTH);
        redstone(1, 2, 18);
        combustion(1, 1, 18, Direction.DOWN);
        for (BlockPos pos : redstone) level.setBlock(pos, Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
    }

    private void library() {
        prepare(-3, 14, -3, 8);
        hut(6, 0);
        ItemStack blueprint = BuilderTests.record(level, EnumSnapshotType.BLUEPRINT, at(6, 0, 0), new BlockPos(5, 5, 5), Direction.NORTH, "Hut");
        ItemStack template = BuilderTests.record(level, EnumSnapshotType.TEMPLATE, at(6, 0, 0), new BlockPos(5, 5, 5), Direction.NORTH, "Hut outline");
        for (int y = 0; y < 6; y++) set(12, y, 1, y == 5 ? Blocks.LANTERN : y % 2 == 0 ? Blocks.DEEPSLATE_BRICKS : Blocks.POLISHED_ANDESITE);
        ItemStack tower = BuilderTests.record(level, EnumSnapshotType.BLUEPRINT, at(12, 0, 1), new BlockPos(1, 6, 1), Direction.NORTH, "Tower");
        ItemStack towerTemplate = BuilderTests.record(level, EnumSnapshotType.TEMPLATE, at(12, 0, 1), new BlockPos(1, 6, 1), Direction.NORTH, "Tower outline");

        set(0, 0, 0, BCBuilders.ELECTRONIC_LIBRARY.get().defaultBlockState().setValue(FacingMachineBlock.FACING, Direction.SOUTH));
        ElectronicLibraryBlockEntity library = (ElectronicLibraryBlockEntity) level.getBlockEntity(at(0, 0, 0));
        library.setItem(ElectronicLibraryBlockEntity.SLOT_DOWN_IN, blueprint.copy());
        library.setItem(ElectronicLibraryBlockEntity.SLOT_UP_IN, new ItemStack(BCBuilders.TEMPLATE_CLEAN.get()));

        BlockState replacerState = BCBuilders.REPLACER.get().defaultBlockState().setValue(FacingMachineBlock.FACING, Direction.SOUTH);
        set(3, 0, 0, replacerState);
        ReplacerBlockEntity replacer = (ReplacerBlockEntity) level.getBlockEntity(at(3, 0, 0));
        replacer.setItem(ReplacerBlockEntity.SLOT_SNAPSHOT, blueprint.copy());
        replacer.setItem(ReplacerBlockEntity.SLOT_FROM, singleSchematic(6, 0, 0));

        chest(0, 0, 3, template, tower, towerTemplate, singleSchematic(12, 0, 1), singleSchematic(12, 5, 1), blueprint.copy());
    }

    private void silicon() {
        prepare(-3, 10, -4, 8);
        for (int x = 0; x <= 6; x += 3) {
            set(x, 0, 0, BCSilicon.LASER.get().defaultBlockState().setValue(LaserBlock.FACING, Direction.SOUTH));
            redstone(x, 0, -2);
            combustion(x, 0, -1, Direction.SOUTH);
        }

        set(0, 0, 4, BCSilicon.ASSEMBLY_TABLE.get());
        AssemblyTableBlockEntity assembly = (AssemblyTableBlockEntity) level.getBlockEntity(at(0, 0, 4));
        ItemStack[] inputs = {new ItemStack(Items.REDSTONE, 64), new ItemStack(Items.IRON_INGOT, 32), new ItemStack(Items.GOLD_INGOT, 32),
                new ItemStack(Items.QUARTZ, 32), new ItemStack(Items.DIAMOND, 16)};
        for (int i = 0; i < inputs.length; i++) assembly.setItem(i, inputs[i]);
        CompoundTag tag = assembly.saveWithoutMetadata();
        ListTag saved = new ListTag();
        for (Item chipset : List.of(BCSilicon.REDSTONE_CHIPSET.get(), BCSilicon.IRON_CHIPSET.get(), BCSilicon.GOLD_CHIPSET.get(),
                BCSilicon.QUARTZ_CHIPSET.get(), BCSilicon.DIAMOND_CHIPSET.get())) {
            CompoundTag entry = new CompoundTag();
            entry.putString("recipe", Registry.ITEM.getKey(chipset).toString());
            entry.put("output", new ItemStack(chipset).save(new CompoundTag()));
            entry.putInt("state", EnumAssemblyRecipeState.SAVED.ordinal());
            saved.add(entry);
        }
        tag.put("recipes_states", saved);
        assembly.load(tag);
        chest(0, 0, 5);

        set(3, 0, 4, BCSilicon.ADVANCED_CRAFTING_TABLE.get());
        AdvancedCraftingTableBlockEntity crafting = (AdvancedCraftingTableBlockEntity) level.getBlockEntity(at(3, 0, 4));
        ItemStack[] piston = {new ItemStack(Items.OAK_PLANKS), new ItemStack(Items.OAK_PLANKS), new ItemStack(Items.OAK_PLANKS),
                new ItemStack(Items.COBBLESTONE), new ItemStack(Items.IRON_INGOT), new ItemStack(Items.COBBLESTONE),
                new ItemStack(Items.COBBLESTONE), new ItemStack(Items.REDSTONE), new ItemStack(Items.COBBLESTONE)};
        for (int i = 0; i < piston.length; i++) crafting.setItem(AdvancedCraftingTableBlockEntity.BLUEPRINT_START + i, piston[i]);
        ItemStack[] materials = {new ItemStack(Items.OAK_PLANKS, 64), new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.COBBLESTONE, 64),
                new ItemStack(Items.IRON_INGOT, 32), new ItemStack(Items.REDSTONE, 32)};
        for (int i = 0; i < materials.length; i++) crafting.setItem(AdvancedCraftingTableBlockEntity.MATERIALS_START + i, materials[i]);

        set(6, 0, 4, BCSilicon.INTEGRATION_TABLE.get());
        IntegrationTableBlockEntity integration = (IntegrationTableBlockEntity) level.getBlockEntity(at(6, 0, 4));
        integration.setItem(IntegrationTableBlockEntity.TARGET_SLOT, new ItemStack(BCSilicon.GOLD_CHIPSET.get(), 4));
        integration.setItem(IntegrationTableBlockEntity.TO_INTEGRATE_START, new ItemStack(Items.REDSTONE, 8));
        for (BlockPos pos : redstone) level.setBlock(pos, Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
    }

    private void plugs() {
        prepare(-3, 14, -3, 6);
        chest(0, 0, 0, new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.COBBLESTONE, 64));
        plug(pipe(1, 0, 0, BCTransport.WOOD_ITEM), Direction.UP, BCTransport.POWER_ADAPTOR);
        engine(1, 1, 0, BCEnergy.REDSTONE_ENGINE.get(), Direction.DOWN);
        redstone(1, 2, 0);
        pipe(2, 0, 0, BCTransport.STONE_ITEM);
        plug(pipe(3, 0, 0, BCTransport.STONE_ITEM), Direction.SOUTH, BCTransport.BLOCKER);
        pipe(3, 0, 1, BCTransport.STONE_ITEM);
        chest(3, 0, 2);
        pipe(4, 0, 0, BCTransport.GOLD_ITEM);
        pipe(5, 0, 0, BCTransport.STONE_ITEM);
        chest(6, 0, 0);

        int[][] arch = {{8, 0}, {8, 1}, {8, 2}, {9, 2}, {10, 2}, {10, 1}, {10, 0}};
        for (int[] xy : arch) pipe(xy[0], xy[1], 0, BCTransport.STRUCTURE);

        PipeBlockEntity capped = pipe(12, 0, 0, BCTransport.STONE_ITEM);
        for (Direction face : List.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP)) {
            plug(capped, face, BCTransport.BLOCKER);
        }
        for (BlockPos pos : redstone) level.setBlock(pos, Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
    }

    private void gates() {
        prepare(-3, 20, -3, 9);
        List<GateVariant> row = List.of(GateVariant.BASIC,
                new GateVariant(EnumGateLogic.AND, EnumGateMaterial.IRON, EnumGateModifier.NO_MODIFIER),
                new GateVariant(EnumGateLogic.OR, EnumGateMaterial.IRON, EnumGateModifier.LAPIS),
                new GateVariant(EnumGateLogic.AND, EnumGateMaterial.NETHER_BRICK, EnumGateModifier.QUARTZ),
                new GateVariant(EnumGateLogic.OR, EnumGateMaterial.NETHER_BRICK, EnumGateModifier.DIAMOND),
                new GateVariant(EnumGateLogic.AND, EnumGateMaterial.GOLD, EnumGateModifier.NO_MODIFIER),
                new GateVariant(EnumGateLogic.OR, EnumGateMaterial.GOLD, EnumGateModifier.LAPIS),
                new GateVariant(EnumGateLogic.AND, EnumGateMaterial.GOLD, EnumGateModifier.QUARTZ),
                new GateVariant(EnumGateLogic.OR, EnumGateMaterial.GOLD, EnumGateModifier.DIAMOND));
        for (int i = 0; i < row.size(); i++) {
            GateLogic logic = gate(pipe(2 * i, 0, 0, BCTransport.STONE_ITEM), Direction.SOUTH, row.get(i));
            if (i % 2 == 1) continue;
            logic.statements[0].trigger.set(TriggerWrapper.wrap(BCCoreStatements.TRIGGER_TRUE, null));
            logic.statements[0].action.set(ActionWrapper.wrap(BCCoreStatements.ACTION_REDSTONE, null));
        }

        redstoneGate(4, true);
        redstoneGate(6, false);

        chest(6, 0, 5, new ItemStack(Items.COBBLESTONE, 64));
        pipe(7, 0, 5, BCTransport.WOOD_ITEM);
        engine(7, 1, 5, BCEnergy.REDSTONE_ENGINE.get(), Direction.DOWN);
        redstone(7, 2, 5);
        PipeBlockEntity iron = pipe(8, 0, 5, BCTransport.IRON_ITEM);
        pipe(9, 0, 5, BCTransport.STONE_ITEM);
        chest(10, 0, 5);
        ItemStack[] nearlyFull = new ItemStack[27];
        Arrays.fill(nearlyFull, new ItemStack(Items.DIRT, 64));
        nearlyFull[26] = new ItemStack(Items.COBBLESTONE, 56);
        chest(8, 0, 4, nearlyFull);
        GateLogic sorter = gate(iron, Direction.SOUTH, new GateVariant(EnumGateLogic.AND, EnumGateMaterial.GOLD, EnumGateModifier.LAPIS));
        StatementParameterItemStack cobblestone = new StatementParameterItemStack(new ItemStack(Items.COBBLESTONE));
        sorter.statements[0].trigger.set(TriggerWrapper.wrap(BCCoreStatements.TRIGGER_INVENTORY_SPACE, Direction.NORTH));
        sorter.statements[0].trigger.set(0, cobblestone);
        sorter.statements[0].action.set(ActionWrapper.wrap(BCTransportStatements.ACTION_PIPE_DIRECTION[Direction.NORTH.ordinal()], null));
        sorter.statements[1].trigger.set(TriggerWrapper.wrap(BCCoreStatements.TRIGGER_INVENTORY_FULL, Direction.NORTH));
        sorter.statements[1].trigger.set(0, cobblestone);
        sorter.statements[1].action.set(ActionWrapper.wrap(BCTransportStatements.ACTION_PIPE_DIRECTION[Direction.EAST.ordinal()], null));
        for (BlockPos pos : redstone) level.setBlock(pos, Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
    }

    /**
     * A lever beside a stone pipe whose gate turns its input into a signal, which lights a lamp through a repeater.
     * Glass sits under the pipe because a pipe's output, like upstream's, powers a conductive block strongly enough
     * to feed back into its own redstone trigger.
     */
    private void redstoneGate(int z, boolean on) {
        GateLogic logic = gate(pipe(1, 0, z, BCTransport.STONE_ITEM), Direction.SOUTH,
                new GateVariant(EnumGateLogic.AND, EnumGateMaterial.IRON, EnumGateModifier.NO_MODIFIER));
        logic.statements[0].trigger.set(TriggerWrapper.wrap(BCCoreStatements.TRIGGER_REDSTONE_ACTIVE, null));
        logic.statements[0].action.set(ActionWrapper.wrap(BCCoreStatements.ACTION_REDSTONE, null));
        set(1, -1, z, Blocks.GLASS);
        set(2, 0, z, Blocks.REPEATER.defaultBlockState().setValue(RepeaterBlock.FACING, Direction.WEST));
        set(3, 0, z, Blocks.REDSTONE_LAMP);
        set(0, 0, z, Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR)
                .setValue(LeverBlock.FACING, Direction.EAST).setValue(LeverBlock.POWERED, on));
    }

    private void wires() {
        prepare(-3, 18, -6, 6);
        signalLine(0, true);
        signalLine(3, false);
        DyeColor[] colours = DyeColor.values();
        for (int i = 0; i < colours.length; i++) {
            PipeBlockEntity pipe = pipe(i, 0, -3, BCTransport.STONE_ITEM);
            for (boolean x : new boolean[] {false, true}) {
                for (boolean z : new boolean[] {false, true}) pipe.wires().addPart(WirePart.get(x, true, z), colours[i]);
            }
        }
    }

    private void lenses() {
        prepare(-3, 18, -4, 10);
        chest(0, 0, 2, new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.COBBLESTONE, 64));
        pipe(1, 0, 2, BCTransport.WOOD_ITEM);
        engine(1, 1, 2, BCEnergy.REDSTONE_ENGINE.get(), Direction.DOWN);
        redstone(1, 2, 2);
        lens(pipe(2, 0, 2, BCTransport.STONE_ITEM), Direction.EAST, new LensVariant(DyeColor.RED, false));
        chest(3, 0, -1, new ItemStack(Items.DIRT, 64), new ItemStack(Items.DIRT, 64));
        pipe(3, 0, 0, BCTransport.WOOD_ITEM);
        engine(3, 1, 0, BCEnergy.REDSTONE_ENGINE.get(), Direction.DOWN);
        redstone(3, 2, 0);
        lens(pipe(3, 0, 1, BCTransport.STONE_ITEM), Direction.SOUTH, new LensVariant(DyeColor.BLUE, false));
        PipeBlockEntity junction = pipe(3, 0, 2, BCTransport.STONE_ITEM);
        lens(junction, Direction.EAST, new LensVariant(DyeColor.RED, true));
        lens(junction, Direction.SOUTH, new LensVariant(DyeColor.BLUE, true));
        pipe(4, 0, 2, BCTransport.STONE_ITEM);
        pipe(5, 0, 2, BCTransport.STONE_ITEM);
        chest(6, 0, 2);
        pipe(3, 0, 3, BCTransport.STONE_ITEM);
        pipe(3, 0, 4, BCTransport.STONE_ITEM);
        chest(3, 0, 5);
        for (int i = 0; i < LensVariant.VALUES.size() / 2; i++) {
            LensVariant variant = LensVariant.VALUES.get(i);
            PipeBlockEntity pipe = pipe(i, 0, 8, BCTransport.STONE_ITEM);
            lens(pipe, Direction.UP, variant);
            lens(pipe, Direction.SOUTH, new LensVariant(variant.colour(), true));
        }
        for (BlockPos pos : redstone) level.setBlock(pos, Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
    }

    private static void lens(PipeBlockEntity pipe, Direction side, LensVariant variant) {
        pipe.replacePluggable(side, new LensPluggable(BCSilicon.LENS, pipe, side, variant));
    }

    /** A lever, a line of six stone pipes with red wire on top and blue below their south edges, and a lamp. */
    private void signalLine(int z, boolean on) {
        set(0, 0, z, Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR)
                .setValue(LeverBlock.FACING, Direction.EAST).setValue(LeverBlock.POWERED, on));
        PipeBlockEntity[] line = new PipeBlockEntity[6];
        for (int i = 0; i < line.length; i++) {
            line[i] = pipe(1 + i, 0, z, BCTransport.STONE_ITEM);
            for (boolean east : new boolean[] {false, true}) {
                if (east ? i == line.length - 1 : i == 0) continue;
                line[i].wires().addPart(WirePart.get(east, true, true), DyeColor.RED);
                line[i].wires().addPart(WirePart.get(east, false, true), DyeColor.BLUE);
            }
        }
        GateVariant iron = new GateVariant(EnumGateLogic.AND, EnumGateMaterial.IRON, EnumGateModifier.NO_MODIFIER);
        GateLogic sender = gate(line[0], Direction.NORTH, iron);
        sender.statements[0].trigger.set(TriggerWrapper.wrap(BCCoreStatements.TRIGGER_REDSTONE_ACTIVE, null));
        sender.statements[0].action.set(ActionWrapper.wrap(BCTransportStatements.ACTION_PIPE_SIGNAL[DyeColor.RED.getId()], null));
        GateLogic receiver = gate(line[line.length - 1], Direction.NORTH, iron);
        receiver.statements[0].trigger.set(TriggerWrapper.wrap(BCTransportStatements.TRIGGER_PIPE_SIGNAL[DyeColor.RED.getId() * 2], null));
        receiver.statements[0].action.set(ActionWrapper.wrap(BCCoreStatements.ACTION_REDSTONE, null));
        set(1 + line.length, 0, z, Blocks.REDSTONE_LAMP);
    }

    private static GateLogic gate(PipeBlockEntity pipe, Direction side, GateVariant variant) {
        PluggableGate gate = new PluggableGate(BCSilicon.GATE, pipe, side, variant);
        pipe.replacePluggable(side, gate);
        return gate.logic();
    }

    private static void plug(PipeBlockEntity pipe, Direction side, PluggableDefinition definition) {
        pipe.replacePluggable(side, definition.create(pipe, side));
    }

    private ItemStack singleSchematic(int x, int y, int z) {
        BlockPos pos = at(x, y, z);
        return SchematicSingleItem.getUsed(SchematicBlockManager.getSchematicBlock(new SchematicBlockContext(level, pos, pos)));
    }

    /** A five-block cube hut with log corners, windows on three sides, a doorway in the south wall and a lantern inside. */
    private void hut(int x0, int z0) {
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                set(x0 + x, 0, z0 + z, Blocks.STONE_BRICKS);
                set(x0 + x, 4, z0 + z, Blocks.SPRUCE_SLAB);
                boolean edgeX = x == 0 || x == 4, edgeZ = z == 0 || z == 4;
                if (!edgeX && !edgeZ) continue;
                for (int y = 1; y <= 3; y++) set(x0 + x, y, z0 + z, edgeX && edgeZ ? Blocks.OAK_LOG : Blocks.OAK_PLANKS);
            }
        }
        set(x0 + 2, 2, z0, Blocks.GLASS_PANE);
        set(x0, 2, z0 + 2, Blocks.GLASS_PANE);
        set(x0 + 4, 2, z0 + 2, Blocks.GLASS_PANE);
        set(x0 + 2, 1, z0 + 4, Blocks.AIR);
        set(x0 + 2, 2, z0 + 4, Blocks.AIR);
        set(x0 + 2, 1, z0 + 2, Blocks.LANTERN);
    }

    /** A builder with {@code snapshot} and {@code resources}, with combustion engines on its west side and on top. */
    private void poweredBuilder(int x, int z, Direction facing, ItemStack snapshot, ItemStack... resources) {
        BuilderBlockEntity builder = builder(x, 0, z, facing);
        for (int i = 0; i < resources.length; i++) builder.setItem(1 + i, resources[i]);
        builder.setItem(BuilderBlockEntity.SLOT_SNAPSHOT, snapshot);
        redstone(x - 2, 0, z);
        combustion(x - 1, 0, z, Direction.EAST);
        redstone(x, 2, z);
        combustion(x, 1, z, Direction.DOWN);
    }

    private BuilderBlockEntity builder(int x, int y, int z, Direction facing) {
        BlockState state = BCBuilders.BUILDER.get().defaultBlockState().setValue(BuilderBlock.FACING, facing);
        set(x, y, z, state);
        BCBuilders.BUILDER.get().setPlacedBy(level, at(x, y, z), state, null, ItemStack.EMPTY);
        return (BuilderBlockEntity) level.getBlockEntity(at(x, y, z));
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
