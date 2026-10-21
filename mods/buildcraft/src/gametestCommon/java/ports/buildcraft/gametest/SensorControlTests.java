package ports.buildcraft.gametest;

import static ports.buildcraft.gametest.BCTests.pipe;
import static ports.buildcraft.gametest.BCTests.require;
import static ports.buildcraft.gametest.BCTests.useOn;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.builders.BCBuilders;
import ports.buildcraft.builders.FillerBlockEntity;
import ports.buildcraft.builders.filler.FilledTemplate;
import ports.buildcraft.builders.filler.FillerPatterns;
import ports.buildcraft.builders.filler.Pattern;
import ports.buildcraft.builders.filler.PatternParameterAxis;
import ports.buildcraft.builders.filler.PatternParameterHollow;
import ports.buildcraft.builders.filler.PatternParameterRotation;
import ports.buildcraft.core.statements.ActionMachineControl;
import ports.buildcraft.core.statements.BCCoreStatements;
import ports.buildcraft.factory.BCFactory;
import ports.buildcraft.lib.Controllable;
import ports.buildcraft.lib.Power;
import ports.buildcraft.silicon.AssemblyRecipe;
import ports.buildcraft.silicon.BCSilicon;
import ports.buildcraft.silicon.IngredientStack;
import ports.buildcraft.silicon.gate.EnumGateLogic;
import ports.buildcraft.silicon.gate.EnumGateMaterial;
import ports.buildcraft.silicon.gate.EnumGateModifier;
import ports.buildcraft.silicon.gate.GateLogic;
import ports.buildcraft.silicon.gate.GateVariant;
import ports.buildcraft.silicon.gate.PluggableGate;
import ports.buildcraft.silicon.plug.PluggableLightSensor;
import ports.buildcraft.silicon.statement.BCSiliconStatements;
import ports.buildcraft.silicon.statement.TriggerLightSensor;
import ports.buildcraft.statement.Action;
import ports.buildcraft.statement.ActionWrapper;
import ports.buildcraft.statement.FullStatement;
import ports.buildcraft.statement.Statement;
import ports.buildcraft.statement.StatementManager;
import ports.buildcraft.statement.StatementParameter;
import ports.buildcraft.statement.TriggerWrapper;
import ports.buildcraft.transport.BCTransport;
import ports.buildcraft.transport.PipeBlockEntity;

/** Loader-neutral GameTests of the light sensor and of the gate actions that control a filler. */
public final class SensorControlTests {
    private static final GateVariant IRON = new GateVariant(EnumGateLogic.AND, EnumGateMaterial.IRON, EnumGateModifier.NO_MODIFIER);
    private static final GateVariant QUARTZ = new GateVariant(EnumGateLogic.AND, EnumGateMaterial.IRON, EnumGateModifier.QUARTZ);
    private static final GateVariant DIAMOND = new GateVariant(EnumGateLogic.AND, EnumGateMaterial.IRON, EnumGateModifier.DIAMOND);
    private static final StatementParameter[] NO_PARAMETERS = new StatementParameter[0];
    /** Enough for a block or two a tick, so a short on-window cannot finish a 3x3x3 fill. */
    private static final long BUILD_POWER = 6_000 * Power.MJ;

    private SensorControlTests() {}

    /**
     * A light sensor goes on any pipe, blocks its face and picks as its own item. Its bright and dark triggers are
     * offered only on that face. Air beside a glowstone is bright and the stone it faces is dark. Taking the sensor
     * off stops the offer; the trigger still reads the light, which is what upstream does. The gametest structure's
     * sky light is whatever terrain it replaced, so the bright case is block light rather than the sky.
     */
    public static void lightSensorPlacesAndReadsLight(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 3, 2);
        BlockPos stonePos = pos.relative(Direction.EAST);
        PipeBlockEntity pipe = pipe(helper, pos, BCTransport.STONE_ITEM);
        helper.setBlock(stonePos, Blocks.STONE);
        Player player = helper.makeMockPlayer();
        ItemStack sensors = new ItemStack(BCSilicon.LIGHT_SENSOR_ITEM.get(), 2);
        require(helper, useOn(helper, player, pos, Direction.SOUTH, sensors) == InteractionResult.CONSUME && sensors.getCount() == 1,
                "a light sensor placed on a stone pipe uses one item");
        require(helper, useOn(helper, player, pos, Direction.EAST, sensors) == InteractionResult.CONSUME && sensors.isEmpty(),
                "a second light sensor placed facing stone uses the item");
        require(helper, pipe.getPluggable(Direction.SOUTH) instanceof PluggableLightSensor && pipe.isBlocked(Direction.SOUTH)
                && pipe.getPluggable(Direction.SOUTH).getPickStack().is(BCSilicon.LIGHT_SENSOR_ITEM.get()),
                "a light sensor blocks its face and picks as its own item");
        helper.setBlock(pos.relative(Direction.SOUTH).relative(Direction.SOUTH), Blocks.GLOWSTONE);

        PluggableGate gate = new PluggableGate(BCSilicon.GATE, pipe, Direction.UP, IRON);
        require(helper, offered(gate.logic(), BCSiliconStatements.TRIGGER_LIGHT_LOW, Direction.SOUTH)
                && offered(gate.logic(), BCSiliconStatements.TRIGGER_LIGHT_HIGH, Direction.SOUTH)
                && offered(gate.logic(), BCSiliconStatements.TRIGGER_LIGHT_LOW, Direction.EAST)
                && offered(gate.logic(), BCSiliconStatements.TRIGGER_LIGHT_HIGH, Direction.EAST),
                "a light sensor offers bright and dark on its own face");
        for (Direction face : Direction.values()) {
            if (face == Direction.SOUTH || face == Direction.EAST) continue;
            require(helper, !offered(gate.logic(), BCSiliconStatements.TRIGGER_LIGHT_LOW, face)
                    && !offered(gate.logic(), BCSiliconStatements.TRIGGER_LIGHT_HIGH, face),
                    "a light sensor offers nothing on its " + face + " face");
        }

        helper.runAfterDelay(10, () -> {
            int air = lightAt(helper, pos.relative(Direction.SOUTH));
            int stone = lightAt(helper, stonePos);
            require(helper, air >= 8 && BCSiliconStatements.TRIGGER_LIGHT_HIGH.isTriggerActive(Direction.SOUTH, gate.logic(), NO_PARAMETERS)
                    && !BCSiliconStatements.TRIGGER_LIGHT_LOW.isTriggerActive(Direction.SOUTH, gate.logic(), NO_PARAMETERS),
                    "air beside glowstone is bright (" + air + "), so only the bright trigger is on");
            require(helper, stone < 8 && BCSiliconStatements.TRIGGER_LIGHT_LOW.isTriggerActive(Direction.EAST, gate.logic(), NO_PARAMETERS)
                    && !BCSiliconStatements.TRIGGER_LIGHT_HIGH.isTriggerActive(Direction.EAST, gate.logic(), NO_PARAMETERS),
                    "stone is dark (" + stone + "), so only the dark trigger is on");

            pipe.removePluggable(Direction.SOUTH, new ArrayList<>());
            require(helper, !offered(gate.logic(), BCSiliconStatements.TRIGGER_LIGHT_HIGH, Direction.SOUTH)
                    && offered(gate.logic(), BCSiliconStatements.TRIGGER_LIGHT_LOW, Direction.EAST),
                    "removing a sensor stops its offer and leaves the other sensor's offer");
            require(helper, BCSiliconStatements.TRIGGER_LIGHT_HIGH.isTriggerActive(Direction.SOUTH, gate.logic(), NO_PARAMETERS),
                    "a removed sensor's trigger still reads the light");
            helper.succeed();
        });

        CompoundTag update = pipe.getUpdateTag().getCompound("plugs").getCompound("east");
        require(helper, update.getString("id").equals("buildcraft:daylight_sensor"), "clients get the light sensor, got " + update);
        PipeBlockEntity loaded = new PipeBlockEntity(helper.absolutePos(pos), helper.getBlockState(pos));
        loaded.load(pipe.saveWithoutMetadata());
        require(helper, loaded.getPluggable(Direction.EAST) instanceof PluggableLightSensor
                && loaded.getPluggable(Direction.SOUTH) instanceof PluggableLightSensor,
                "saving keeps both light sensors");

        require(helper, described(BCSiliconStatements.TRIGGER_LIGHT_HIGH.getDescription(), "gate.trigger.light.bright")
                && described(BCSiliconStatements.TRIGGER_LIGHT_LOW.getDescription(), "gate.trigger.light.dark"),
                "light triggers read as Bright and Dark");
        require(helper, StatementManager.getStatement("buildcraft:light_bright") == BCSiliconStatements.TRIGGER_LIGHT_HIGH
                && StatementManager.getStatement("buildcraft:light_dark") == BCSiliconStatements.TRIGGER_LIGHT_LOW,
                "light triggers keep upstream's tags");
        require(helper, BCSiliconStatements.TRIGGER_LIGHT_HIGH.getSprite().getPath().endsWith("trigger_light_bright.png")
                && BCSiliconStatements.TRIGGER_LIGHT_LOW.getSprite().getPath().endsWith("trigger_light_dark.png"),
                "light triggers use upstream's icons");
        requireRecipe(helper);
    }

    /**
     * Machine control and the filler's patterns are offered on the side that faces a filler, and only when the gate
     * has enough parameter slots. No other ported machine offers machine control.
     */
    public static void machineControlOfferedOnlyForTheFiller(GameTestHelper helper) {
        FillerBlockEntity filler = FillerTests.fillerOnVolumeBox(helper);
        PipeBlockEntity pipe = pipe(helper, new BlockPos(2, 2, 4), BCTransport.STONE_ITEM);
        GateLogic iron = new PluggableGate(BCSilicon.GATE, pipe, Direction.NORTH, IRON).logic();
        GateLogic quartz = new PluggableGate(BCSilicon.GATE, pipe, Direction.UP, QUARTZ).logic();
        GateLogic diamond = new PluggableGate(BCSilicon.GATE, pipe, Direction.SOUTH, DIAMOND).logic();

        for (ActionMachineControl action : BCCoreStatements.ACTION_MACHINE_CONTROL) {
            require(helper, offered(iron, action, Direction.EAST) && offered(quartz, action, Direction.EAST) && offered(diamond, action, Direction.EAST),
                    "every gate facing the filler offers " + action.mode);
            for (Direction face : Direction.values()) {
                if (face == Direction.EAST) continue;
                require(helper, !offered(iron, action, face), action.mode + " is not offered on the " + face + " side");
            }
        }
        for (Pattern pattern : new Pattern[] {FillerPatterns.NONE, FillerPatterns.CLEAR, FillerPatterns.FILL, FillerPatterns.BOX, FillerPatterns.FRAME}) {
            require(helper, offered(iron, pattern, Direction.EAST), "an iron gate offers " + pattern);
        }
        require(helper, !offered(iron, FillerPatterns.CIRCLE, Direction.EAST) && !offered(iron, FillerPatterns.PYRAMID, Direction.EAST)
                && !offered(iron, FillerPatterns.SPHERE, Direction.EAST),
                "an iron gate has no action parameters, so it offers no shaped pattern");
        require(helper, offered(quartz, FillerPatterns.SPHERE, Direction.EAST) && !offered(quartz, FillerPatterns.PYRAMID, Direction.EAST)
                && !offered(quartz, FillerPatterns.CIRCLE, Direction.EAST),
                "a quartz gate has one action parameter, so it offers a sphere and not a pyramid or a circle");
        require(helper, offered(diamond, FillerPatterns.CIRCLE, Direction.EAST) && offered(diamond, FillerPatterns.PYRAMID, Direction.EAST)
                && offered(diamond, FillerPatterns.SPHERE, Direction.EAST),
                "a diamond gate offers the circle, the pyramid and the sphere");

        for (Direction side : Direction.values()) {
            List<Statement> external = new ArrayList<>();
            for (Action.External action : StatementManager.getExternalActions(side, filler)) external.add(action);
            require(helper, external.contains(BCCoreStatements.ACTION_MACHINE_CONTROL_OFF) && external.contains(FillerPatterns.FILL),
                    "the filler accepts control and patterns from its " + side + " side");
        }

        require(helper, described(BCCoreStatements.ACTION_MACHINE_CONTROL_ON.getDescription(), "gate.action.machine.on")
                && described(BCCoreStatements.ACTION_MACHINE_CONTROL_OFF.getDescription(), "gate.action.machine.off")
                && described(BCCoreStatements.ACTION_MACHINE_CONTROL_LOOP.getDescription(), "gate.action.machine.loop"),
                "machine control reads as On, Off and Loop");
        require(helper, StatementManager.getStatement("buildcraft:machine.off") == BCCoreStatements.ACTION_MACHINE_CONTROL_OFF
                && StatementManager.getStatement("buildcraft.machine.off") == BCCoreStatements.ACTION_MACHINE_CONTROL_OFF
                && StatementManager.getStatement("buildcraft:machine.on") == BCCoreStatements.ACTION_MACHINE_CONTROL_ON
                && StatementManager.getStatement("buildcraft.machine.on") == BCCoreStatements.ACTION_MACHINE_CONTROL_ON
                && StatementManager.getStatement("buildcraft:machine.loop") == BCCoreStatements.ACTION_MACHINE_CONTROL_LOOP
                && StatementManager.getStatement("buildcraft.machine.loop") == BCCoreStatements.ACTION_MACHINE_CONTROL_LOOP,
                "machine control keeps upstream's tags, including the old names");
        require(helper, BCCoreStatements.ACTION_MACHINE_CONTROL_OFF.getSprite().getPath().endsWith("action_machinecontrol_off.png")
                && BCCoreStatements.ACTION_MACHINE_CONTROL_ON.getSprite().getPath().endsWith("action_machinecontrol_on.png")
                && BCCoreStatements.ACTION_MACHINE_CONTROL_LOOP.getSprite().getPath().endsWith("action_machinecontrol_loop.png"),
                "machine control uses upstream's icons");

        PipeBlockEntity other = pipe(helper, new BlockPos(2, 2, 2), BCTransport.STONE_ITEM);
        place(helper, new BlockPos(3, 2, 2), BCBuilders.QUARRY.get());
        place(helper, new BlockPos(1, 2, 2), BCBuilders.BUILDER.get());
        place(helper, new BlockPos(2, 2, 3), BCFactory.MINING_WELL.get());
        place(helper, new BlockPos(2, 2, 1), BCFactory.PUMP.get());
        place(helper, new BlockPos(2, 3, 2), BCSilicon.LASER.get());
        place(helper, new BlockPos(2, 1, 2), BCFactory.HEAT_EXCHANGER.get());
        place(helper, new BlockPos(7, 2, 2), BCFactory.DISTILLER.get());
        PipeBlockEntity distillerPipe = pipe(helper, new BlockPos(6, 2, 2), BCTransport.STONE_ITEM);
        GateLogic beside = new PluggableGate(BCSilicon.GATE, other, Direction.NORTH, DIAMOND).logic();
        GateLogic besideDistiller = new PluggableGate(BCSilicon.GATE, distillerPipe, Direction.NORTH, DIAMOND).logic();
        for (ActionMachineControl action : BCCoreStatements.ACTION_MACHINE_CONTROL) {
            for (Direction face : Direction.values()) {
                require(helper, !offered(beside, action, face) && !offered(besideDistiller, action, face),
                        "no other machine offers " + action.mode + " on " + face);
            }
        }
        FillerTests.removeVolumeBoxes(helper);
        helper.succeed();
    }

    /**
     * A diamond gate's circle action reaches the filler. Upstream's parameter copy skips the first slot, so the axis
     * stays at its default. The same action again keeps the template; a later slot changing replaces it, and the
     * pattern stays locked while the gate holds it.
     */
    public static void gateSetsFillerPatternAndSkipsFirstParameter(GameTestHelper helper) {
        FillerBlockEntity filler = FillerTests.fillerOnVolumeBox(helper);
        PipeBlockEntity pipe = pipe(helper, new BlockPos(2, 2, 4), BCTransport.STONE_ITEM);
        PluggableGate gate = new PluggableGate(BCSilicon.GATE, pipe, Direction.NORTH, DIAMOND);
        pipe.replacePluggable(Direction.NORTH, gate);
        FullStatement<ActionWrapper> action = gate.logic().statements[0].action;
        action.set(ActionWrapper.wrap(FillerPatterns.CIRCLE, Direction.EAST));
        action.set(0, PatternParameterAxis.X);
        action.set(1, PatternParameterHollow.FILLED_OUTER);
        action.set(2, PatternParameterRotation.QUARTER);
        gate.logic().statements[0].trigger.set(TriggerWrapper.wrap(BCCoreStatements.TRIGGER_TRUE, null));

        gate.logic().onTick();
        require(helper, filler.patternStatement.get() == FillerPatterns.CIRCLE, "the gate sets the circle");
        require(helper, filler.patternStatement.get(0) == PatternParameterAxis.Y
                && filler.patternStatement.get(1) == PatternParameterHollow.FILLED_OUTER
                && filler.patternStatement.get(2) == PatternParameterRotation.QUARTER,
                "the first parameter stays the default axis and the later ones are copied");
        require(helper, filler.isLocked() && !filler.handleButton(FillerBlockEntity.patternButton(FillerPatterns.indexOf(FillerPatterns.FILL))),
                "a gate's pattern locks the filler, so its buttons do nothing");
        FilledTemplate template = filler.template();
        require(helper, template != null, "the circle makes a template");

        gate.logic().onTick();
        require(helper, filler.template() == template && filler.isLocked(), "the same action again keeps the template and the lock");
        action.set(2, PatternParameterRotation.HALF);
        gate.logic().onTick();
        require(helper, filler.template() != template && filler.patternStatement.get(2) == PatternParameterRotation.HALF
                && filler.patternStatement.get(0) == PatternParameterAxis.Y,
                "changing a later parameter rebuilds the template and still skips the axis");
        FillerTests.removeVolumeBoxes(helper);
        helper.succeed();
    }

    /**
     * A gate turns the filler off, on and into a loop. Off builds nothing, on places blocks, off freezes the count
     * and on continues an unfinished fill.
     */
    public static void gateTurnsTheFillerOffAndOn(GameTestHelper helper) {
        FillerBlockEntity filler = FillerTests.fillerOnVolumeBox(helper);
        FillerTests.setPattern(filler, FillerPatterns.FILL);
        filler.setItem(0, new ItemStack(Items.STONE, 64));
        PipeBlockEntity pipe = pipe(helper, new BlockPos(2, 2, 4), BCTransport.STONE_ITEM);
        PluggableGate gate = new PluggableGate(BCSilicon.GATE, pipe, Direction.NORTH, IRON);
        pipe.replacePluggable(Direction.NORTH, gate);
        GateLogic logic = gate.logic();
        logic.statements[0].trigger.set(TriggerWrapper.wrap(BCCoreStatements.TRIGGER_TRUE, null));
        setMode(logic, BCCoreStatements.ACTION_MACHINE_CONTROL_OFF);
        require(helper, filler.getControlMode() == Controllable.Mode.OFF, "the off action turns the filler off");
        setMode(logic, BCCoreStatements.ACTION_MACHINE_CONTROL_LOOP);
        require(helper, filler.getControlMode() == Controllable.Mode.LOOP, "the loop action turns the filler to loop");
        setMode(logic, BCCoreStatements.ACTION_MACHINE_CONTROL_ON);
        require(helper, filler.getControlMode() == Controllable.Mode.ON, "the on action turns the filler on");
        setMode(logic, BCCoreStatements.ACTION_MACHINE_CONTROL_OFF);

        helper.onEachTick(() -> filler.battery().set(BUILD_POWER));
        int[] frozen = {64};
        boolean[] unfinished = {false};
        helper.startSequence()
                .thenExecuteAfter(5, () -> {
                    require(helper, filler.getItem(0).getCount() == 64 && filler.getControlMode() == Controllable.Mode.OFF,
                            "off for several ticks places nothing, " + filler.getItem(0).getCount() + " stone left");
                    setMode(logic, BCCoreStatements.ACTION_MACHINE_CONTROL_ON);
                })
                .thenExecuteAfter(4, () -> {
                    int left = filler.getItem(0).getCount();
                    require(helper, left < 64 && filler.getControlMode() == Controllable.Mode.ON,
                            "on places stone, " + left + " left");
                    frozen[0] = left;
                    unfinished[0] = left > 64 - 27;
                    setMode(logic, BCCoreStatements.ACTION_MACHINE_CONTROL_OFF);
                })
                .thenExecuteAfter(5, () -> {
                    require(helper, filler.getItem(0).getCount() == frozen[0] && filler.getControlMode() == Controllable.Mode.OFF,
                            "off freezes the fill at " + frozen[0] + ", now " + filler.getItem(0).getCount());
                    setMode(logic, BCCoreStatements.ACTION_MACHINE_CONTROL_ON);
                })
                .thenExecuteAfter(4, () -> {
                    int left = filler.getItem(0).getCount();
                    require(helper, unfinished[0] ? left < frozen[0] : left == frozen[0],
                            "on resumes an unfinished fill, " + frozen[0] + " then " + left);
                    FillerTests.removeVolumeBoxes(helper);
                })
                .thenSucceed();
    }

    /** The mode is saved by name, missing means on, and loop is never finished even when the finished flag is set. */
    public static void fillerControlModeSurvivesSaving(GameTestHelper helper) {
        FillerBlockEntity filler = FillerTests.placeFiller(helper, new BlockPos(3, 2, 2), Direction.WEST);
        require(helper, filler.getControlMode() == Controllable.Mode.ON && !filler.isFinished(), "a new filler is on and not finished");
        CompoundTag saved = filler.saveWithoutMetadata();
        saved.putBoolean("finished", true);
        saved.putString("mode", "LOOP");
        FillerBlockEntity loop = reload(filler, saved);
        require(helper, loop.getControlMode() == Controllable.Mode.LOOP && !loop.isFinished(), "loop stays unfinished");
        saved.putString("mode", "ON");
        FillerBlockEntity on = reload(filler, saved);
        require(helper, on.getControlMode() == Controllable.Mode.ON && on.isFinished(), "on with the finished flag is finished");
        saved.remove("mode");
        require(helper, reload(filler, saved).getControlMode() == Controllable.Mode.ON, "a save without a mode loads as on");
        filler.setControlMode(Controllable.Mode.OFF);
        require(helper, filler.getUpdateTag().getString("mode").equals("OFF"), "clients receive the mode");
        filler.setControlMode(Controllable.Mode.ON);
        require(helper, filler.getControlMode() == Controllable.Mode.ON, "leaving off clears nothing else and returns to on");
        helper.succeed();
    }

    private static void setMode(GateLogic logic, ActionMachineControl action) {
        logic.statements[0].action.set(ActionWrapper.wrap(action, Direction.EAST));
        logic.onTick();
    }

    private static FillerBlockEntity reload(FillerBlockEntity filler, CompoundTag saved) {
        FillerBlockEntity loaded = new FillerBlockEntity(filler.getBlockPos(), filler.getBlockState());
        loaded.load(saved);
        return loaded;
    }

    private static void place(GameTestHelper helper, BlockPos pos, Block block) {
        helper.setBlock(pos, block);
        BlockEntity tile = helper.getBlockEntity(pos);
        require(helper, tile != null && !(tile instanceof Controllable), block + " at " + pos + " is a machine and not controllable, got " + tile);
    }

    private static int lightAt(GameTestHelper helper, BlockPos pos) {
        return helper.getLevel().getMaxLocalRawBrightness(helper.absolutePos(pos));
    }

    private static void requireRecipe(GameTestHelper helper) {
        Recipe<?> recipe = helper.getLevel().getRecipeManager().byKey(BuildCraft.id("assembly/plug_light_sensor")).orElse(null);
        require(helper, recipe instanceof AssemblyRecipe, "the light sensor assembly recipe must load");
        AssemblyRecipe assembly = (AssemblyRecipe) recipe;
        require(helper, assembly.getResultItem().is(BCSilicon.LIGHT_SENSOR_ITEM.get())
                && assembly.getRequiredMicroJoulesFor(ItemStack.EMPTY) == 500 * Power.MJ, "a light sensor costs 500 MJ");
        List<IngredientStack> inputs = assembly.getInputsFor(ItemStack.EMPTY);
        require(helper, inputs.size() == 1 && inputs.get(0).count() == 1 && inputs.get(0).ingredient().test(new ItemStack(Items.DAYLIGHT_DETECTOR)),
                "a light sensor is a daylight detector");
    }

    private static boolean offered(GateLogic logic, Statement statement, Direction side) {
        if (statement instanceof TriggerLightSensor) {
            for (TriggerWrapper wrapper : logic.getAllValidTriggers()) {
                if (wrapper.delegate == statement && wrapper.sourcePart == side) return true;
            }
            return false;
        }
        for (ActionWrapper wrapper : logic.getAllValidActions()) {
            if (wrapper.delegate == statement && wrapper.sourcePart == side) return true;
        }
        return false;
    }

    private static boolean described(Component component, String key) {
        return component.getContents() instanceof TranslatableContents text && text.getKey().equals(key) && text.getArgs().length == 0;
    }
}
