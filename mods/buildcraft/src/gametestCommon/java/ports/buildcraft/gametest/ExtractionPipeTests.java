package ports.buildcraft.gametest;

import static ports.buildcraft.gametest.BCTests.chest;
import static ports.buildcraft.gametest.BCTests.count;
import static ports.buildcraft.gametest.BCTests.pipe;
import static ports.buildcraft.gametest.BCTests.require;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import ports.buildcraft.core.statements.BCCoreStatements;
import ports.buildcraft.lib.Power;
import ports.buildcraft.silicon.BCSilicon;
import ports.buildcraft.silicon.gate.EnumGateLogic;
import ports.buildcraft.silicon.gate.EnumGateMaterial;
import ports.buildcraft.silicon.gate.EnumGateModifier;
import ports.buildcraft.silicon.gate.GateLogic;
import ports.buildcraft.silicon.gate.GateVariant;
import ports.buildcraft.silicon.gate.PluggableGate;
import ports.buildcraft.statement.Action;
import ports.buildcraft.statement.ActionWrapper;
import ports.buildcraft.statement.TriggerWrapper;
import ports.buildcraft.transport.BCTransport;
import ports.buildcraft.transport.PipeBlockEntity;
import ports.buildcraft.transport.behaviour.EmzuliBehaviour;
import ports.buildcraft.transport.behaviour.EmzuliBehaviour.SlotIndex;
import ports.buildcraft.transport.behaviour.WoodDiamondBehaviour;
import ports.buildcraft.transport.behaviour.WoodDiamondBehaviour.FilterMode;
import ports.buildcraft.transport.statements.BCTransportStatements;

/** Loader-neutral GameTests of the diamond-wood and emzuli extraction pipes. */
public final class ExtractionPipeTests {
    private static final GateVariant IRON_AND = new GateVariant(EnumGateLogic.AND, EnumGateMaterial.IRON, EnumGateModifier.NO_MODIFIER);
    /** Far more than one item's worth (1 MJ), so any limit on a pull comes from the pipe. */
    private static final long PULSE = Power.toUnits(64 * Power.MJ);

    private ExtractionPipeTests() {}

    private static ItemStack[] mixedStock() {
        return new ItemStack[] {new ItemStack(Items.COBBLESTONE, 4), new ItemStack(Items.DIRT, 4), new ItemStack(Items.SAND, 4)};
    }

    /** One power pulse into {@code pipe}, as an engine on its side would send. */
    private static void pulse(PipeBlockEntity pipe) {
        pipe.energyPort(Direction.UP).receive(PULSE, false);
    }

    private static String stock(ChestBlockEntity chest) {
        return "cobblestone " + count(chest, Items.COBBLESTONE) + ", dirt " + count(chest, Items.DIRT) + ", sand " + count(chest, Items.SAND);
    }

    private static void requireStock(GameTestHelper helper, ChestBlockEntity chest, int cobblestone, int dirt, int sand, String what) {
        require(helper, count(chest, Items.COBBLESTONE) == cobblestone && count(chest, Items.DIRT) == dirt && count(chest, Items.SAND) == sand,
                what + ": the source must hold cobblestone " + cobblestone + ", dirt " + dirt + ", sand " + sand + ", holds " + stock(chest));
    }

    /**
     * Upstream PipeBehaviourWoodDiamond pulls one item a pulse. A whitelist pulls only the filter items, a blacklist
     * everything else, and round robin each filter in turn, waiting on a filter whose item is not there. With no
     * filters a whitelist pulls anything. Saving keeps the filters, mode and current filter.
     */
    public static void diamondWoodPipeExtractsByFilterMode(GameTestHelper helper) {
        ChestBlockEntity whiteSource = chest(helper, new BlockPos(0, 2, 0), mixedStock());
        PipeBlockEntity white = pipe(helper, new BlockPos(1, 2, 0), BCTransport.DIAMOND_WOOD_ITEM);
        pipe(helper, new BlockPos(2, 2, 0), BCTransport.STONE_ITEM);
        ChestBlockEntity whiteTarget = chest(helper, new BlockPos(3, 2, 0));

        ChestBlockEntity blackSource = chest(helper, new BlockPos(0, 2, 2), mixedStock());
        PipeBlockEntity black = pipe(helper, new BlockPos(1, 2, 2), BCTransport.DIAMOND_WOOD_ITEM);
        pipe(helper, new BlockPos(2, 2, 2), BCTransport.STONE_ITEM);
        ChestBlockEntity blackTarget = chest(helper, new BlockPos(3, 2, 2));

        ChestBlockEntity robinSource = chest(helper, new BlockPos(0, 2, 4), mixedStock());
        PipeBlockEntity robin = pipe(helper, new BlockPos(1, 2, 4), BCTransport.DIAMOND_WOOD_ITEM);
        pipe(helper, new BlockPos(2, 2, 4), BCTransport.STONE_ITEM);
        ChestBlockEntity robinTarget = chest(helper, new BlockPos(3, 2, 4));

        WoodDiamondBehaviour whiteList = (WoodDiamondBehaviour) white.behaviour();
        WoodDiamondBehaviour blackList = (WoodDiamondBehaviour) black.behaviour();
        WoodDiamondBehaviour roundRobin = (WoodDiamondBehaviour) robin.behaviour();

        helper.runAfterDelay(2, () -> {
            require(helper, whiteList.filterMode() == FilterMode.WHITE_LIST && whiteList.allowsRoundRobin(),
                    "a new diamond-wood item pipe whitelists and offers round robin");
            whiteList.filters.setItem(4, new ItemStack(Items.COBBLESTONE));
            for (int i = 0; i < 6; i++) pulse(white);
            requireStock(helper, whiteSource, 0, 4, 4, "a cobblestone whitelist after six pulses");

            blackList.setFilterMode(FilterMode.BLACK_LIST);
            blackList.filters.setItem(0, new ItemStack(Items.COBBLESTONE));
            pulse(black);
            require(helper, count(blackSource, Items.COBBLESTONE) == 4 && count(blackSource, Items.DIRT) + count(blackSource, Items.SAND) == 7,
                    "a blacklist pulse pulls one item that is not cobblestone, source holds " + stock(blackSource));
            for (int i = 0; i < 10; i++) pulse(black);
            requireStock(helper, blackSource, 4, 0, 0, "a cobblestone blacklist after eleven pulses");

            roundRobin.setFilterMode(FilterMode.ROUND_ROBIN);
            roundRobin.filters.setItem(1, new ItemStack(Items.DIRT));
            roundRobin.filters.setItem(5, new ItemStack(Items.SAND));
            require(helper, roundRobin.currentFilter() == 1 && roundRobin.filterValid(), "round robin starts at the first filter");
            pulse(robin);
            requireStock(helper, robinSource, 4, 3, 4, "round robin's first pulse");
            require(helper, roundRobin.currentFilter() == 5, "round robin moves on to the next filter, at " + roundRobin.currentFilter());
            pulse(robin);
            requireStock(helper, robinSource, 4, 3, 3, "round robin's second pulse");
            pulse(robin);
            requireStock(helper, robinSource, 4, 2, 3, "round robin's third pulse");

            CompoundTag saved = new CompoundTag();
            roundRobin.save(saved);
            WoodDiamondBehaviour loaded = new WoodDiamondBehaviour(robin);
            loaded.load(saved);
            require(helper, loaded.filterMode() == FilterMode.ROUND_ROBIN && loaded.currentFilter() == 5
                    && loaded.filters.getItem(1).is(Items.DIRT) && loaded.filters.getItem(5).is(Items.SAND),
                    "saving keeps the round robin mode, current filter and filters");

            // A filter whose item is missing holds the turn: round robin waits instead of skipping past it.
            roundRobin.filters.setItem(5, new ItemStack(Items.GRAVEL));
            pulse(robin);
            requireStock(helper, robinSource, 4, 2, 3, "round robin on a missing filter item");

            whiteList.filters.setItem(4, ItemStack.EMPTY);
            pulse(white);
            requireStock(helper, whiteSource, 0, 3, 4, "a whitelist with no filters pulls anything");
        });
        helper.succeedWhen(() -> {
            require(helper, count(whiteTarget, Items.COBBLESTONE) == 4 && count(whiteTarget, Items.DIRT) == 1,
                    "the whitelist pipe delivers its four cobblestone and the unfiltered dirt, target holds " + stock(whiteTarget));
            require(helper, count(blackTarget, Items.COBBLESTONE) == 0 && count(blackTarget, Items.DIRT) == 4 && count(blackTarget, Items.SAND) == 4,
                    "the blacklist pipe delivers everything but cobblestone, target holds " + stock(blackTarget));
            require(helper, count(robinTarget, Items.DIRT) == 2 && count(robinTarget, Items.SAND) == 1 && count(robinTarget, Items.COBBLESTONE) == 0,
                    "the round robin pipe delivers what it pulled, target holds " + stock(robinTarget));
        });
    }

    /** The diamond-wood fluid pipe has no round robin; upstream's GUI hides the button. */
    public static void diamondWoodFluidPipeHasNoRoundRobin(GameTestHelper helper) {
        PipeBlockEntity fluid = pipe(helper, new BlockPos(1, 2, 1), BCTransport.DIAMOND_WOOD_FLUID);
        WoodDiamondBehaviour behaviour = (WoodDiamondBehaviour) fluid.behaviour();
        require(helper, !behaviour.allowsRoundRobin(), "the fluid pipe must not offer round robin");
        behaviour.setFilterMode(FilterMode.ROUND_ROBIN);
        require(helper, behaviour.filterMode() == FilterMode.WHITE_LIST, "the fluid pipe must refuse round robin");
        behaviour.setFilterMode(FilterMode.BLACK_LIST);
        require(helper, behaviour.filterMode() == FilterMode.BLACK_LIST, "the fluid pipe takes a blacklist");
        helper.succeed();
    }

    /** The items travelling in {@code pipe} with their colours, as the pipe saves them. */
    private static Map<Item, Set<DyeColor>> travelling(PipeBlockEntity pipe, Map<Item, Set<DyeColor>> into) {
        CompoundTag saved = new CompoundTag();
        pipe.flow().save(saved);
        for (Tag entry : saved.getList("items", Tag.TAG_COMPOUND)) {
            CompoundTag item = (CompoundTag) entry;
            int stored = item.getByte("colour");
            into.computeIfAbsent(ItemStack.of(item.getCompound("stack")).getItem(), i -> new HashSet<>())
                    .add(stored <= 0 ? null : DyeColor.byId(stored - 1));
        }
        return into;
    }

    private static void presetAction(GateLogic logic, SlotIndex index) {
        logic.statements[0].trigger.set(TriggerWrapper.wrap(BCCoreStatements.TRIGGER_TRUE, null));
        logic.statements[0].action.set(ActionWrapper.wrap(BCTransportStatements.ACTION_EXTRACTION_PRESET[index.ordinal()], null));
    }

    /**
     * Upstream PipeBehaviourEmzuli: nothing is pulled until a gate's extraction preset action selects a slot; then
     * only that slot's filter item is pulled, painted the slot's colour (or not at all for a slot without one).
     * Switching the gate to another preset switches the filter and paint.
     */
    public static void emzuliPipeExtractsPerPresetAndPaints(GameTestHelper helper) {
        ChestBlockEntity source = chest(helper, new BlockPos(0, 2, 1), mixedStock());
        PipeBlockEntity emzuli = pipe(helper, new BlockPos(1, 2, 1), BCTransport.EMZULI_ITEM);
        PipeBlockEntity next = pipe(helper, new BlockPos(2, 2, 1), BCTransport.STONE_ITEM);
        ChestBlockEntity target = chest(helper, new BlockPos(3, 2, 1));
        EmzuliBehaviour behaviour = (EmzuliBehaviour) emzuli.behaviour();
        Map<Item, Set<DyeColor>> seen = new HashMap<>();
        PluggableGate[] gate = new PluggableGate[1];
        boolean[] powered = new boolean[1];

        helper.onEachTick(() -> {
            if (powered[0]) pulse(emzuli);
            travelling(next, seen);
        });
        helper.startSequence()
                .thenExecuteAfter(2, () -> {
                    List<Action.Internal> actions = new ArrayList<>();
                    behaviour.addInternalActions(actions);
                    require(helper, actions.containsAll(List.of(BCTransportStatements.ACTION_EXTRACTION_PRESET)),
                            "the emzuli pipe offers the four extraction preset actions, offers " + actions);
                    require(helper, SlotIndex.SQUARE.colour == DyeColor.RED && SlotIndex.CIRCLE.colour == DyeColor.GREEN
                            && SlotIndex.TRIANGLE.colour == DyeColor.BLUE && SlotIndex.CROSS.colour == DyeColor.YELLOW,
                            "the presets are red, green, blue and yellow, as upstream's slots");

                    behaviour.filters.setItem(SlotIndex.SQUARE.ordinal(), new ItemStack(Items.COBBLESTONE));
                    behaviour.filters.setItem(SlotIndex.CIRCLE.ordinal(), new ItemStack(Items.DIRT));
                    behaviour.filters.setItem(SlotIndex.TRIANGLE.ordinal(), new ItemStack(Items.SAND));
                    behaviour.setSlotColour(SlotIndex.SQUARE, DyeColor.ORANGE);
                    behaviour.setSlotColour(SlotIndex.CIRCLE, DyeColor.LIME);

                    pulse(emzuli);
                    requireStock(helper, source, 4, 4, 4, "an emzuli pipe without an active preset");
                    require(helper, behaviour.currentSlot() == null, "no preset is active without a gate");

                    CompoundTag saved = new CompoundTag();
                    behaviour.save(saved);
                    EmzuliBehaviour loaded = new EmzuliBehaviour(emzuli);
                    loaded.load(saved);
                    require(helper, loaded.slotColour(SlotIndex.SQUARE) == DyeColor.ORANGE && loaded.slotColour(SlotIndex.TRIANGLE) == null
                            && loaded.filters.getItem(SlotIndex.CIRCLE.ordinal()).is(Items.DIRT), "saving keeps the filters and slot colours");

                    gate[0] = new PluggableGate(BCSilicon.GATE, emzuli, Direction.NORTH, IRON_AND);
                    emzuli.replacePluggable(Direction.NORTH, gate[0]);
                    presetAction(gate[0].logic(), SlotIndex.SQUARE);
                    powered[0] = true;
                })
                .thenWaitUntil(() -> require(helper, count(target, Items.COBBLESTONE) == 4,
                        "the square preset delivers the four cobblestone, target holds " + stock(target)))
                .thenExecute(() -> {
                    require(helper, behaviour.activeSlots().equals(Set.of(SlotIndex.SQUARE)), "the gate keeps the square preset active");
                    requireStock(helper, source, 0, 4, 4, "the square preset");
                    require(helper, Set.of(DyeColor.ORANGE).equals(seen.get(Items.COBBLESTONE)),
                            "the square preset paints cobblestone orange, saw " + seen);
                    presetAction(gate[0].logic(), SlotIndex.CIRCLE);
                })
                .thenWaitUntil(() -> require(helper, count(target, Items.DIRT) == 4, "the circle preset delivers the four dirt, target holds " + stock(target)))
                .thenExecute(() -> {
                    require(helper, Set.of(DyeColor.LIME).equals(seen.get(Items.DIRT)), "the circle preset paints dirt lime, saw " + seen);
                    presetAction(gate[0].logic(), SlotIndex.TRIANGLE);
                })
                .thenWaitUntil(() -> require(helper, count(target, Items.SAND) == 4, "the triangle preset delivers the four sand, target holds " + stock(target)))
                .thenExecute(() -> {
                    Set<DyeColor> unpainted = new HashSet<>();
                    unpainted.add(null);
                    require(helper, unpainted.equals(seen.get(Items.SAND)), "a preset without a colour leaves items unpainted, saw " + seen);
                    require(helper, behaviour.activeSlots().equals(Set.of(SlotIndex.TRIANGLE)),
                            "presets the gate no longer sends lapse, active " + behaviour.activeSlots());
                })
                .thenSucceed();
    }
}
