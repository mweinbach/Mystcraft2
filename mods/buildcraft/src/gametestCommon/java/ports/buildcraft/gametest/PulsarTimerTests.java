package ports.buildcraft.gametest;

import static ports.buildcraft.gametest.BCTests.chest;
import static ports.buildcraft.gametest.BCTests.count;
import static ports.buildcraft.gametest.BCTests.pipe;
import static ports.buildcraft.gametest.BCTests.require;
import static ports.buildcraft.gametest.BCTests.tile;
import static ports.buildcraft.gametest.BCTests.useAt;
import static ports.buildcraft.gametest.BCTests.useOn;

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
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.material.Fluids;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.core.statements.BCCoreStatements;
import ports.buildcraft.energy.BCEnergy;
import ports.buildcraft.factory.BCFactory;
import ports.buildcraft.factory.TankBlockEntity;
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
import ports.buildcraft.silicon.plug.PluggablePulsar;
import ports.buildcraft.silicon.plug.PluggableTimer;
import ports.buildcraft.silicon.statement.BCSiliconStatements;
import ports.buildcraft.silicon.statement.TriggerTimer;
import ports.buildcraft.statement.Action;
import ports.buildcraft.statement.ActionWrapper;
import ports.buildcraft.statement.StatementManager;
import ports.buildcraft.statement.StatementParameter;
import ports.buildcraft.statement.TriggerWrapper;
import ports.buildcraft.transport.BCTransport;
import ports.buildcraft.transport.PipeBlockEntity;
import ports.buildcraft.transport.behaviour.WoodBehaviour;

/** Loader-neutral GameTests of the pipe pulsar and the timer. */
public final class PulsarTimerTests {
    private static final GateVariant IRON = new GateVariant(EnumGateLogic.AND, EnumGateMaterial.IRON, EnumGateModifier.NO_MODIFIER);
    /** How far from a pipe's middle a click lands on a pulsar or timer, which sits 2 to 4 pixels in from its face. */
    private static final double ON_PLUG = 0.5 - 3 / 16.0;
    private static final StatementParameter[] NO_PARAMETERS = new StatementParameter[0];

    private PulsarTimerTests() {}

    /**
     * A pulsar goes only on a pipe that takes redstone engine power, and a timer on any pipe. Each blocks its face
     * and picks as its own item. Clicking a pulsar toggles it, and saving keeps the switch, the pulse and the queued
     * gate pulses. Clients hear whether it is pulsing. The assembly recipes are upstream's.
     */
    public static void pulsarAndTimerPlaceSaveAndSync(GameTestHelper helper) {
        BlockPos stonePos = new BlockPos(2, 3, 2);
        BlockPos powerPos = new BlockPos(4, 3, 2);
        BlockPos woodPos = new BlockPos(6, 3, 2);
        PipeBlockEntity stone = pipe(helper, stonePos, BCTransport.STONE_ITEM);
        pipe(helper, powerPos, BCTransport.WOOD_POWER);
        PipeBlockEntity wood = pipe(helper, woodPos, BCTransport.WOOD_ITEM);
        Player player = helper.makeMockPlayer();
        ItemStack pulsars = new ItemStack(BCSilicon.PULSAR_ITEM.get(), 3);

        require(helper, useOn(helper, player, stonePos, Direction.EAST, pulsars) == InteractionResult.PASS && pulsars.getCount() == 3,
                "a pulsar is refused on a stone pipe");
        require(helper, useOn(helper, player, powerPos, Direction.EAST, pulsars) == InteractionResult.PASS && pulsars.getCount() == 3,
                "a pulsar is refused on a wooden kinesis pipe");
        require(helper, useOn(helper, player, woodPos, Direction.EAST, pulsars) == InteractionResult.CONSUME && pulsars.getCount() == 2,
                "a pulsar placed on a wooden item pipe uses one item");
        require(helper, wood.getPluggable(Direction.EAST) instanceof PluggablePulsar && wood.isBlocked(Direction.EAST),
                "the placed pulsar blocks its face");
        PluggablePulsar pulsar = (PluggablePulsar) wood.getPluggable(Direction.EAST);
        require(helper, pulsar.getPickStack().is(BCSilicon.PULSAR_ITEM.get()), "a pulsar picks as its own item");

        ItemStack timers = new ItemStack(BCSilicon.TIMER_ITEM.get());
        require(helper, useOn(helper, player, stonePos, Direction.SOUTH, timers) == InteractionResult.CONSUME && timers.isEmpty(),
                "a timer placed on a stone pipe uses the item");
        require(helper, stone.getPluggable(Direction.SOUTH) instanceof PluggableTimer && stone.isBlocked(Direction.SOUTH)
                && stone.getPluggable(Direction.SOUTH).getPickStack().is(BCSilicon.TIMER_ITEM.get()),
                "a timer blocks its face and picks as its own item");

        require(helper, useAt(helper, player, woodPos, Direction.EAST, ON_PLUG, ItemStack.EMPTY) == InteractionResult.SUCCESS && pulsar.manuallyEnabled(),
                "clicking a pulsar switches it on");
        pulsar.enablePulsar();
        pulsar.addSinglePulse();
        pulsar.onTick();
        require(helper, pulsar.pulseStage() == 1 && pulsar.gateEnabledTicks() == 9 && pulsar.gateSinglePulses() == 1,
                "one tick advances the pulse and spends one tick of a gate hold");

        PipeBlockEntity loaded = new PipeBlockEntity(helper.absolutePos(woodPos), helper.getBlockState(woodPos));
        loaded.load(wood.saveWithoutMetadata());
        require(helper, loaded.getPluggable(Direction.EAST) instanceof PluggablePulsar, "saving keeps the pulsar");
        PluggablePulsar reloaded = (PluggablePulsar) loaded.getPluggable(Direction.EAST);
        require(helper, reloaded.manuallyEnabled() && reloaded.pulseStage() == 1 && reloaded.gateEnabledTicks() == 9 && reloaded.gateSinglePulses() == 1,
                "saving keeps the switch, the pulse stage and the queued gate pulses");

        CompoundTag update = wood.getUpdateTag().getCompound("plugs").getCompound("east");
        require(helper, update.getString("id").equals("buildcraft:pulsar"), "clients get the pulsar, got " + update);
        CompoundTag data = update.getCompound("data");
        require(helper, data.getBoolean("pulsing") && data.getBoolean("auto") && data.getBoolean("manual") && data.getByte("pulseStage") == 1,
                "clients get whether it is pulsing, by a gate and by hand, got " + data);

        PluggablePulsar stray = new PluggablePulsar(BCSilicon.PULSAR, stone, Direction.NORTH);
        stone.replacePluggable(Direction.NORTH, stray);
        stray.toggleManual();
        for (int i = 0; i < 25; i++) stray.onTick();
        require(helper, stray.pulseStage() == 5 && stray.isPulsing(),
                "a pulsar on a pipe that takes no redstone power still pulses, and does not crash");

        requireRecipes(helper);
        helper.succeed();
    }

    /**
     * Switched on, a pulsar pulls one item on the 20th tick and every 20 ticks after, and nothing once switched off.
     * A gate's constant action does the same while it stays on, and clearing it before a pulse finishes sends no power.
     */
    public static void pulsarPulsesEveryTwentyTicks(GameTestHelper helper) {
        BlockPos chestPos = new BlockPos(1, 3, 2);
        BlockPos pos = new BlockPos(2, 3, 2);
        ChestBlockEntity source = chest(helper, chestPos, new ItemStack(Items.COBBLESTONE, 16));
        PipeBlockEntity pipe = pipe(helper, pos, BCTransport.WOOD_ITEM);
        helper.runAfterDelay(2, () -> {
            require(helper, ((WoodBehaviour) pipe.behaviour()).currentDir() == Direction.WEST, "the wooden pipe faces the chest");
            PluggablePulsar pulsar = pulsar(pipe, Direction.EAST);
            pulsar.toggleManual();
            tick(null, pulsar, 19);
            require(helper, count(source, Items.COBBLESTONE) == 16, "the first nineteen ticks extract nothing");
            tick(null, pulsar, 1);
            require(helper, count(source, Items.COBBLESTONE) == 15, "the twentieth tick extracts one item");
            tick(null, pulsar, 19);
            require(helper, count(source, Items.COBBLESTONE) == 15, "the next nineteen ticks extract nothing");
            tick(null, pulsar, 1);
            require(helper, count(source, Items.COBBLESTONE) == 14, "the fortieth tick extracts a second item");
            pulsar.toggleManual();
            tick(null, pulsar, 40);
            require(helper, count(source, Items.COBBLESTONE) == 14 && !pulsar.isPulsing(), "switched off, further ticks extract nothing");

            PluggableGate gate = gate(pipe, Direction.NORTH);
            gate.logic().statements[0].trigger.set(TriggerWrapper.wrap(BCCoreStatements.TRIGGER_TRUE, null));
            gate.logic().statements[0].action.set(ActionWrapper.wrap(BCSiliconStatements.ACTION_PULSAR_CONSTANT, Direction.EAST));
            tick(gate, pulsar, 19);
            require(helper, count(source, Items.COBBLESTONE) == 14, "a gate holding the pulsar on extracts nothing before twenty ticks");
            tick(gate, pulsar, 1);
            require(helper, count(source, Items.COBBLESTONE) == 13, "the twentieth paired tick extracts one item");
            gate.logic().statements[0].action.set(null);
            tick(gate, pulsar, 30);
            require(helper, count(source, Items.COBBLESTONE) == 13 && pulsar.gateEnabledTicks() == 0,
                    "clearing the action before the next pulse finishes extracts nothing more");
            helper.succeed();
        });
    }

    /**
     * A single pulse waits while the chest is empty, takes one item on the next pulse once the chest has one, and
     * does not take another. On a fluid pipe the same pulse takes 1000 mB or waits while the tank holds less.
     */
    public static void singlePulseRetriesUntilThePipeCanTakeIt(GameTestHelper helper) {
        BlockPos chestPos = new BlockPos(1, 3, 4);
        BlockPos pos = new BlockPos(2, 3, 4);
        ChestBlockEntity source = chest(helper, chestPos);
        PipeBlockEntity pipe = pipe(helper, pos, BCTransport.WOOD_ITEM);
        BlockPos tankPos = new BlockPos(4, 3, 4);
        BlockPos fluidPos = new BlockPos(5, 3, 4);
        helper.setBlock(tankPos, BCFactory.TANK.get());
        TankBlockEntity tank = tile(helper, tankPos, TankBlockEntity.class);
        tank.tank().fill(Fluids.WATER, 500, false);
        PipeBlockEntity fluidPipe = pipe(helper, fluidPos, BCTransport.WOOD_FLUID);

        helper.runAfterDelay(2, () -> {
            require(helper, ((WoodBehaviour) pipe.behaviour()).currentDir() == Direction.WEST, "the wooden item pipe faces the chest");
            require(helper, ((WoodBehaviour) fluidPipe.behaviour()).currentDir() == Direction.WEST, "the wooden fluid pipe faces the tank");
            PluggablePulsar pulsar = armed(pipe, Direction.EAST, BCSiliconStatements.ACTION_PULSAR_SINGLE);
            tick(gateOf(pipe), pulsar, 20);
            require(helper, count(source, Items.COBBLESTONE) == 0 && pulsar.gateSinglePulses() == 1,
                    "a single pulse stays queued while the chest is empty");
            source.setItem(0, new ItemStack(Items.COBBLESTONE, 8));
            tick(gateOf(pipe), pulsar, 20);
            require(helper, count(source, Items.COBBLESTONE) == 7 && pulsar.gateSinglePulses() == 0,
                    "the next pulse takes one item and the single pulse is spent");
            tick(gateOf(pipe), pulsar, 40);
            require(helper, count(source, Items.COBBLESTONE) == 7, "a spent single pulse does not take a second item");

            PluggablePulsar fluidPulsar = armed(fluidPipe, Direction.EAST, BCSiliconStatements.ACTION_PULSAR_SINGLE);
            tick(gateOf(fluidPipe), fluidPulsar, 20);
            require(helper, tank.tank().amount() == 500 && fluidPulsar.gateSinglePulses() == 1,
                    "a single pulse stays queued while the tank holds less than 1000 mB");
            tank.tank().fill(Fluids.WATER, 1000, false);
            tick(gateOf(fluidPipe), fluidPulsar, 20);
            require(helper, tank.tank().amount() == 500 && fluidPulsar.gateSinglePulses() == 0,
                    "the next pulse takes 1000 mB and leaves the rest");
            tick(gateOf(fluidPipe), fluidPulsar, 40);
            require(helper, tank.tank().amount() == 500, "a spent single pulse does not take fluid again");
            helper.succeed();
        });
    }

    /**
     * Timer triggers are absent until a timer is on the pipe, then the 5, 10 and 15 second triggers are offered once
     * each, not per face. Each is active only on the tick the world time divides its period. Pulsar actions are
     * offered only on the pulsar's face.
     */
    public static void timerTriggersAndPulsarActions(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 3, 6);
        PipeBlockEntity pipe = pipe(helper, pos, BCTransport.STONE_ITEM);
        PluggableGate gate = gate(pipe, Direction.UP);
        require(helper, timerTriggers(gate.logic()).isEmpty(), "a pipe with no timer offers no timer triggers");
        pipe.replacePluggable(Direction.SOUTH, BCSilicon.TIMER.create(pipe, Direction.SOUTH));
        pipe.replacePluggable(Direction.NORTH, BCSilicon.TIMER.create(pipe, Direction.NORTH));
        List<TriggerWrapper> timers = timerTriggers(gate.logic());
        require(helper, timers.size() == 3 && timers.stream().allMatch(trigger -> trigger.sourcePart == null),
                "two timers offer the three timer triggers once, and not on a side, got " + timers.size());
        require(helper, described(BCSiliconStatements.TRIGGER_TIMER_SHORT.getDescription(), "gate.trigger.timer", "5")
                && described(BCSiliconStatements.TRIGGER_TIMER_MEDIUM.getDescription(), "gate.trigger.timer", "10")
                && described(BCSiliconStatements.TRIGGER_TIMER_LONG.getDescription(), "gate.trigger.timer", "15"),
                "timer triggers read as 5, 10 and 15 second timers");
        require(helper, BCSiliconStatements.TRIGGER_TIMER_SHORT.getSprite().getPath().endsWith("trigger_timer_short.png")
                && BCSiliconStatements.TRIGGER_TIMER_MEDIUM.getSprite().getPath().endsWith("trigger_timer_medium.png")
                && BCSiliconStatements.TRIGGER_TIMER_LONG.getSprite().getPath().endsWith("trigger_timer_long.png"),
                "timer triggers use upstream's icons");

        long time = helper.getLevel().getGameTime();
        for (int seconds : new int[] {5, 10, 15}) {
            int period = 20 * seconds;
            boolean expected = time % period == 0;
            boolean active = BCSiliconStatements.TRIGGER_TIMER[seconds / 5 - 1].isTriggerActive(gate.logic(), NO_PARAMETERS);
            require(helper, active == expected, seconds + "s timer active must be " + expected + " at time " + time);
        }
        int wait = (int) ((600 - time % 600 + 100) % 600);
        if (wait == 0) wait = 600;
        final int atHundred = wait;
        helper.runAfterDelay(atHundred, () -> {
            long now = helper.getLevel().getGameTime();
            require(helper, now % 600 == 100, "the world should be 100 ticks into a 600 tick cycle, at " + now);
            require(helper, BCSiliconStatements.TRIGGER_TIMER_SHORT.isTriggerActive(gate.logic(), NO_PARAMETERS),
                    "the 5 second timer is on when the time divides 100");
            require(helper, !BCSiliconStatements.TRIGGER_TIMER_MEDIUM.isTriggerActive(gate.logic(), NO_PARAMETERS)
                    && !BCSiliconStatements.TRIGGER_TIMER_LONG.isTriggerActive(gate.logic(), NO_PARAMETERS),
                    "the 10 and 15 second timers stay off 100 ticks into the cycle");
        });
        helper.runAfterDelay(atHundred + 1, () -> require(helper,
                !BCSiliconStatements.TRIGGER_TIMER_SHORT.isTriggerActive(gate.logic(), NO_PARAMETERS),
                "the 5 second timer lasts one tick"));
        helper.runAfterDelay(atHundred + 100, () -> require(helper,
                BCSiliconStatements.TRIGGER_TIMER_SHORT.isTriggerActive(gate.logic(), NO_PARAMETERS)
                        && BCSiliconStatements.TRIGGER_TIMER_MEDIUM.isTriggerActive(gate.logic(), NO_PARAMETERS)
                        && !BCSiliconStatements.TRIGGER_TIMER_LONG.isTriggerActive(gate.logic(), NO_PARAMETERS),
                "200 ticks into the cycle the 5 and 10 second timers are on"));
        helper.runAfterDelay(atHundred + 200, () -> {
            require(helper, BCSiliconStatements.TRIGGER_TIMER_SHORT.isTriggerActive(gate.logic(), NO_PARAMETERS)
                    && !BCSiliconStatements.TRIGGER_TIMER_MEDIUM.isTriggerActive(gate.logic(), NO_PARAMETERS)
                    && BCSiliconStatements.TRIGGER_TIMER_LONG.isTriggerActive(gate.logic(), NO_PARAMETERS),
                    "300 ticks into the cycle the 5 and 15 second timers are on");
            helper.succeed();
        });

        PluggablePulsar pulsar = pulsar(pipe, Direction.EAST);
        require(helper, offered(gate.logic(), BCSiliconStatements.ACTION_PULSAR_CONSTANT, Direction.EAST)
                && offered(gate.logic(), BCSiliconStatements.ACTION_PULSAR_SINGLE, Direction.EAST),
                "a pulsar offers both actions on its face");
        for (Direction face : Direction.values()) {
            if (face == Direction.EAST) continue;
            require(helper, !offered(gate.logic(), BCSiliconStatements.ACTION_PULSAR_CONSTANT, face)
                    && !offered(gate.logic(), BCSiliconStatements.ACTION_PULSAR_SINGLE, face),
                    "a pulsar offers no actions on its " + face + " face");
        }
        require(helper, described(BCSiliconStatements.ACTION_PULSAR_CONSTANT.getDescription(), "gate.action.pulsar.constant", null)
                && described(BCSiliconStatements.ACTION_PULSAR_SINGLE.getDescription(), "gate.action.pulsar.single", null),
                "pulsar actions read as Power Pulsar and Single Power Pulse");
        require(helper, StatementManager.getStatement("buildcraft:pulsar.constant") == BCSiliconStatements.ACTION_PULSAR_CONSTANT
                && StatementManager.getStatement("buildcraft.pulsar.constantconstant") == BCSiliconStatements.ACTION_PULSAR_CONSTANT
                && StatementManager.getStatement("buildcraft:pulsar.single") == BCSiliconStatements.ACTION_PULSAR_SINGLE
                && StatementManager.getStatement("buildcraft.pulsar.constantsingle") == BCSiliconStatements.ACTION_PULSAR_SINGLE,
                "pulsar actions keep upstream's tags, including the old names");
        require(helper, BCSiliconStatements.ACTION_PULSAR_CONSTANT.getSprite().getPath().endsWith("action_pulsar_on.png")
                && BCSiliconStatements.ACTION_PULSAR_SINGLE.getSprite().getPath().endsWith("action_pulsar_single.png"),
                "pulsar actions use upstream's icons");
    }

    private static void requireRecipes(GameTestHelper helper) {
        Recipe<?> pulsar = helper.getLevel().getRecipeManager().byKey(BuildCraft.id("assembly/plug_pulsar")).orElse(null);
        Recipe<?> timer = helper.getLevel().getRecipeManager().byKey(BuildCraft.id("assembly/plug_timer")).orElse(null);
        require(helper, pulsar instanceof AssemblyRecipe && timer instanceof AssemblyRecipe, "pulsar and timer assembly recipes must load");
        AssemblyRecipe pulsarRecipe = (AssemblyRecipe) pulsar;
        AssemblyRecipe timerRecipe = (AssemblyRecipe) timer;
        require(helper, pulsarRecipe.getResultItem().is(BCSilicon.PULSAR_ITEM.get())
                && pulsarRecipe.getRequiredMicroJoulesFor(ItemStack.EMPTY) == 1000 * Power.MJ, "a pulsar costs 1000 MJ");
        List<IngredientStack> pulsarInputs = pulsarRecipe.getInputsFor(ItemStack.EMPTY);
        require(helper, pulsarInputs.size() == 2 && pulsarInputs.get(0).count() == 1
                && pulsarInputs.get(0).ingredient().test(new ItemStack(BCEnergy.REDSTONE_ENGINE_ITEM.get()))
                && pulsarInputs.get(1).count() == 2 && pulsarInputs.get(1).ingredient().test(new ItemStack(Items.IRON_INGOT)),
                "a pulsar is a redstone engine and two iron ingots");
        require(helper, timerRecipe.getResultItem().is(BCSilicon.TIMER_ITEM.get())
                && timerRecipe.getRequiredMicroJoulesFor(ItemStack.EMPTY) == 500 * Power.MJ, "a timer costs 500 MJ");
        List<IngredientStack> timerInputs = timerRecipe.getInputsFor(ItemStack.EMPTY);
        require(helper, timerInputs.size() == 1 && timerInputs.get(0).count() == 1 && timerInputs.get(0).ingredient().test(new ItemStack(Items.CLOCK)),
                "a timer is a clock");
    }

    private static PluggablePulsar pulsar(PipeBlockEntity pipe, Direction side) {
        PluggablePulsar pulsar = new PluggablePulsar(BCSilicon.PULSAR, pipe, side);
        pipe.replacePluggable(side, pulsar);
        return pulsar;
    }

    private static PluggableGate gate(PipeBlockEntity pipe, Direction side) {
        PluggableGate gate = new PluggableGate(BCSilicon.GATE, pipe, side, IRON);
        pipe.replacePluggable(side, gate);
        return gate;
    }

    private static PluggableGate gateOf(PipeBlockEntity pipe) {
        return (PluggableGate) pipe.getPluggable(Direction.NORTH);
    }

    /** A gate on the north face with always-on and {@code action} aimed at the pulsar on {@code side}. */
    private static PluggablePulsar armed(PipeBlockEntity pipe, Direction side, Action action) {
        PluggableGate gate = gate(pipe, Direction.NORTH);
        gate.logic().statements[0].trigger.set(TriggerWrapper.wrap(BCCoreStatements.TRIGGER_TRUE, null));
        gate.logic().statements[0].action.set(ActionWrapper.wrap(action, side));
        return pulsar(pipe, side);
    }

    /** Gate first, then the pulsar, which is how a north gate and an east pulsar tick. */
    private static void tick(PluggableGate gate, PluggablePulsar pulsar, int times) {
        for (int i = 0; i < times; i++) {
            if (gate != null) gate.logic().onTick();
            pulsar.onTick();
        }
    }

    private static List<TriggerWrapper> timerTriggers(GateLogic logic) {
        return logic.getAllValidTriggers().stream()
                .filter(trigger -> trigger.delegate instanceof TriggerTimer)
                .toList();
    }

    private static boolean offered(GateLogic logic, Action action, Direction side) {
        for (ActionWrapper wrapper : logic.getAllValidActions()) {
            if (wrapper.delegate == action && wrapper.sourcePart == side) return true;
        }
        return false;
    }

    private static boolean described(Component component, String key, String argument) {
        if (!(component.getContents() instanceof TranslatableContents text) || !text.getKey().equals(key)) return false;
        if (argument == null) return text.getArgs().length == 0;
        return text.getArgs().length == 1 && argument.equals(String.valueOf(text.getArgs()[0]));
    }
}
