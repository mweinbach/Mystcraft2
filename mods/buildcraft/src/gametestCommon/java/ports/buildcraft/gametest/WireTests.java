package ports.buildcraft.gametest;

import static ports.buildcraft.gametest.BCTests.pipe;
import static ports.buildcraft.gametest.BCTests.require;

import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import ports.buildcraft.core.statements.BCCoreStatements;
import ports.buildcraft.silicon.BCSilicon;
import ports.buildcraft.silicon.gate.EnumGateLogic;
import ports.buildcraft.silicon.gate.EnumGateMaterial;
import ports.buildcraft.silicon.gate.EnumGateModifier;
import ports.buildcraft.silicon.gate.GateLogic;
import ports.buildcraft.silicon.gate.GateVariant;
import ports.buildcraft.silicon.gate.PluggableGate;
import ports.buildcraft.statement.ActionWrapper;
import ports.buildcraft.statement.Statement;
import ports.buildcraft.statement.TriggerWrapper;
import ports.buildcraft.transport.BCTransport;
import ports.buildcraft.transport.PipeBlock;
import ports.buildcraft.transport.PipeBlockEntity;
import ports.buildcraft.transport.statements.ActionParameterSignal;
import ports.buildcraft.transport.statements.BCTransportStatements;
import ports.buildcraft.transport.statements.TriggerParameterSignal;
import ports.buildcraft.transport.wire.WireBetween;
import ports.buildcraft.transport.wire.WirePart;
import ports.buildcraft.transport.wire.WireSystem;
import ports.buildcraft.transport.wire.WireSystems;

/** Loader-neutral GameTests of pipe wire, wire systems and the pipe signal triggers and actions. */
public final class WireTests {
    /** Four pairs with three trigger and three action parameters each. */
    private static final GateVariant GOLD_DIAMOND = new GateVariant(EnumGateLogic.AND, EnumGateMaterial.GOLD, EnumGateModifier.DIAMOND);
    /** The middle of a positive corner's box, 12 to 13 pixels along each axis. */
    private static final double HIGH = 12.5 / 16;
    /** The middle of a negative corner's box, 3 to 4 pixels along each axis. */
    private static final double LOW = 3.5 / 16;
    private static final WirePart UP_SOUTH_EAST = WirePart.get(true, true, true);
    private static final WirePart UP_SOUTH_WEST = WirePart.get(false, true, true);
    private static final WirePart UP_NORTH_EAST = WirePart.get(true, true, false);
    private static final WirePart DOWN_SOUTH_EAST = WirePart.get(true, false, true);
    private static final WirePart DOWN_SOUTH_WEST = WirePart.get(false, false, true);

    private WireTests() {}

    /** Right-clicks a pipe at {@code (x, y, z)} within its block, on {@code face}, as {@code use} would. */
    private static InteractionResult useAtPoint(GameTestHelper helper, Player player, BlockPos pos, Direction face, double x, double y, double z,
            ItemStack held) {
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        BlockPos absolute = helper.absolutePos(pos);
        BlockHitResult hit = new BlockHitResult(Vec3.atLowerCornerOf(absolute).add(x, y, z), face, absolute, false);
        return helper.getBlockState(pos).use(helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
    }

    /** Stands {@code player} above {@code (x, z)} of {@code pos}'s block, looking straight down. */
    private static void lookDownAt(GameTestHelper helper, Player player, BlockPos pos, double x, double z) {
        Vec3 corner = Vec3.atLowerCornerOf(helper.absolutePos(pos));
        player.moveTo(corner.x + x, corner.y + 3 - player.getEyeHeight(), corner.z + z, 0, 90);
        player.setYHeadRot(0);
    }

    private static ItemStack wire(DyeColor colour, int count) {
        return new ItemStack(BCTransport.wireItem(colour), count);
    }

    private static int dropped(GameTestHelper helper, DyeColor colour) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                new AABB(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(new BlockPos(8, 8, 8))),
                item -> item.getItem().is(BCTransport.wireItem(colour))).stream().mapToInt(item -> item.getItem().getCount()).sum();
    }

    private static String key(Component component) {
        return component.getContents() instanceof TranslatableContents text ? text.getKey() : component.getString();
    }

    private static boolean shapeContains(GameTestHelper helper, BlockPos pos, double x, double y, double z) {
        return helper.getBlockState(pos).getShape(helper.getLevel(), helper.absolutePos(pos)).toAabbs().stream()
                .anyMatch(box -> box.inflate(1e-6).contains(x, y, z));
    }

    private static GateLogic gate(PipeBlockEntity pipe, Direction side) {
        PluggableGate gate = new PluggableGate(BCSilicon.GATE, pipe, side, GOLD_DIAMOND);
        pipe.replacePluggable(side, gate);
        return gate.logic();
    }

    private static TriggerWrapper trigger(Statement statement) {
        return TriggerWrapper.wrap(statement, null);
    }

    private static ActionWrapper action(Statement statement) {
        return ActionWrapper.wrap(statement, null);
    }

    @Nullable
    private static DyeColor colour(PipeBlockEntity pipe, WirePart part) {
        return pipe.wires().getColourOfPart(part);
    }

    /**
     * Right-clicking a pipe with wire puts it on the corner nearest the click and uses one item; clicking a wire puts
     * the next one on the corner across the clicked face, in the next pipe if the face leads there. Corners of one
     * colour join with lengths along the pipe and across to connected pipes, which join the pipe's shape; other
     * colours and plugged faces do not join. Saving keeps the wires and clients get them, with which are powered.
     */
    public static void wirePlacesJoinsAndSaves(GameTestHelper helper) {
        BlockPos a = new BlockPos(2, 2, 2), b = new BlockPos(3, 2, 2), plugged = new BlockPos(2, 2, 5), beyond = new BlockPos(3, 2, 5);
        PipeBlockEntity first = pipe(helper, a, BCTransport.STONE_ITEM);
        PipeBlockEntity second = pipe(helper, b, BCTransport.STONE_ITEM);
        PipeBlockEntity blocked = pipe(helper, plugged, BCTransport.STONE_ITEM);
        PipeBlockEntity past = pipe(helper, beyond, BCTransport.STONE_ITEM);
        blocked.replacePluggable(Direction.EAST, BCTransport.BLOCKER.create(blocked, Direction.EAST));
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "wires"), null);

        helper.runAfterDelay(2, () -> {
            ItemStack red = wire(DyeColor.RED, 4);
            require(helper, key(red.getHoverName()).equals("item.buildcraft.wire"), "wire is named after its colour");
            require(helper, useAtPoint(helper, player, a, Direction.UP, 0.7, 0.75, 0.7, red) == InteractionResult.CONSUME && red.getCount() == 3,
                    "right-clicking a pipe with wire places it and uses one item");
            require(helper, colour(first, UP_SOUTH_EAST) == DyeColor.RED && first.wires().parts().size() == 1,
                    "the wire goes on the corner nearest the click");
            require(helper, useAtPoint(helper, player, a, Direction.UP, 0.7, 0.75, 0.7, red) != InteractionResult.CONSUME && red.getCount() == 3,
                    "a corner takes one wire");

            useAtPoint(helper, player, a, Direction.EAST, 13 / 16.0, HIGH, HIGH, red);
            require(helper, colour(second, UP_SOUTH_WEST) == DyeColor.RED && red.getCount() == 2,
                    "clicking a wire's east face puts the next wire on the facing corner of the pipe to the east");
            require(helper, first.wires().betweens().get(WireBetween.EAST_UP_SOUTH) == DyeColor.RED
                    && second.wires().betweens().get(WireBetween.WEST_UP_SOUTH) == DyeColor.RED, "wire joins across connected pipes");
            useAtPoint(helper, player, a, Direction.NORTH, HIGH, HIGH, 12 / 16.0, red);
            require(helper, colour(first, UP_NORTH_EAST) == DyeColor.RED && first.wires().betweens().get(WireBetween.Z_UP_EAST) == DyeColor.RED,
                    "clicking a wire's north face puts the next wire on the corner north of it, joined along the pipe");
            useAtPoint(helper, player, a, Direction.DOWN, 0.7, 0.3, 0.7, wire(DyeColor.BLUE, 1));
            require(helper, colour(first, DOWN_SOUTH_EAST) == DyeColor.BLUE && !first.wires().betweens().containsKey(WireBetween.Y_SOUTH_EAST),
                    "wires of different colours do not join");
            require(helper, shapeContains(helper, a, HIGH, HIGH, 0.5) && shapeContains(helper, a, 15 / 16.0, HIGH, HIGH)
                    && shapeContains(helper, b, 1 / 16.0, HIGH, HIGH), "the wires and their lengths join the pipes' shapes");

            blocked.wires().addPart(UP_SOUTH_EAST, DyeColor.RED);
            past.wires().addPart(UP_SOUTH_WEST, DyeColor.RED);
            require(helper, blocked.wires().betweens().isEmpty() && past.wires().betweens().isEmpty(), "wire does not cross a plugged face");
            WireSystems systems = WireSystems.get(helper.getLevel());
            require(helper, systems.getWireSystemsWithElement(WireSystem.Element.wire(blocked.getBlockPos(), UP_SOUTH_EAST)).size() == 1
                    && !systems.getWireSystemsWithElement(WireSystem.Element.wire(blocked.getBlockPos(), UP_SOUTH_EAST)).get(0).elements
                    .contains(WireSystem.Element.wire(past.getBlockPos(), UP_SOUTH_WEST)), "a plugged face splits the wire system");
            List<WireSystem> joined = systems.getWireSystemsWithElement(WireSystem.Element.wire(first.getBlockPos(), UP_SOUTH_EAST));
            require(helper, joined.size() == 1 && joined.get(0).colour == DyeColor.RED
                    && joined.get(0).elements.contains(WireSystem.Element.wire(second.getBlockPos(), UP_SOUTH_WEST))
                    && joined.get(0).elements.contains(WireSystem.Element.wire(first.getBlockPos(), UP_NORTH_EAST))
                    && !joined.get(0).elements.contains(WireSystem.Element.wire(first.getBlockPos(), DOWN_SOUTH_EAST)),
                    "the joined red wires share one system, which the blue wire is not in");

            PipeBlockEntity loaded = new PipeBlockEntity(first.getBlockPos(), helper.getBlockState(a));
            loaded.load(first.saveWithoutMetadata());
            require(helper, loaded.wires().parts().equals(first.wires().parts()), "saving keeps the wires");
            CompoundTag update = first.getUpdateTag().getCompound("wires");
            require(helper, update.getIntArray("parts").length == 6 && update.getInt("powered") == 0, "clients get the wires and that none are powered, got " + update);
            helper.succeed();
        });
    }

    /**
     * Breaking a wire while looking at it takes off only that wire: a corner drops its wire, a length along the pipe
     * takes off and drops the corners at both ends, and a length to the next pipe takes off this pipe's corner. A
     * broken pipe drops one wire per corner.
     */
    public static void wireBreaksOffAndDrops(GameTestHelper helper) {
        BlockPos a = new BlockPos(2, 2, 2), b = new BlockPos(3, 2, 2), lone = new BlockPos(5, 2, 5);
        PipeBlockEntity first = pipe(helper, a, BCTransport.STONE_ITEM);
        PipeBlockEntity second = pipe(helper, b, BCTransport.STONE_ITEM);
        Player player = helper.makeMockPlayer();
        BlockPos absolute = helper.absolutePos(a);

        helper.runAfterDelay(2, () -> {
            first.wires().addPart(UP_SOUTH_EAST, DyeColor.RED);
            first.wires().addPart(UP_SOUTH_WEST, DyeColor.RED);
            second.wires().addPart(UP_SOUTH_WEST, DyeColor.RED);

            lookDownAt(helper, player, a, 14.5 / 16, HIGH);
            require(helper, PipeBlock.onPlayerDestroy(helper.getLevel(), absolute, player), "breaking while looking at a wire hits the wire");
            require(helper, colour(first, UP_SOUTH_EAST) == null && colour(first, UP_SOUTH_WEST) == DyeColor.RED
                    && colour(second, UP_SOUTH_WEST) == DyeColor.RED && dropped(helper, DyeColor.RED) == 1,
                    "breaking a length to the next pipe takes off and drops this pipe's corner");
            require(helper, second.wires().betweens().isEmpty(), "the next pipe stops drawing the length");

            first.wires().addPart(UP_SOUTH_EAST, DyeColor.RED);
            lookDownAt(helper, player, a, 0.5, HIGH);
            require(helper, PipeBlock.onPlayerDestroy(helper.getLevel(), absolute, player) && first.wires().parts().isEmpty()
                    && dropped(helper, DyeColor.RED) == 3, "breaking a length along the pipe takes off and drops both corners");

            lookDownAt(helper, player, b, LOW, HIGH);
            require(helper, PipeBlock.onPlayerDestroy(helper.getLevel(), helper.absolutePos(b), player) && second.wires().parts().isEmpty()
                    && dropped(helper, DyeColor.RED) == 4, "breaking a corner drops its wire");
            require(helper, !PipeBlock.onPlayerDestroy(helper.getLevel(), helper.absolutePos(b), player) && helper.getBlockState(b).getBlock() instanceof PipeBlock,
                    "without a wire in sight the pipe itself breaks");

            PipeBlockEntity broken = pipe(helper, lone, BCTransport.STONE_ITEM);
            broken.wires().addPart(DOWN_SOUTH_WEST, DyeColor.LIME);
            broken.wires().addPart(DOWN_SOUTH_EAST, DyeColor.LIME);
            helper.setBlock(lone, Blocks.AIR);
            require(helper, dropped(helper, DyeColor.LIME) == 2, "a broken pipe drops one wire per corner");
            require(helper, WireSystems.get(helper.getLevel()).getWireSystemsWithElement(WireSystem.Element.wire(helper.absolutePos(lone), DOWN_SOUTH_WEST)).isEmpty(),
                    "a broken pipe's wires leave their systems");
            helper.succeed();
        });
    }

    /**
     * A gate's "Pipe Signal" action powers the wire of its colour on its pipe and every wire joined to it, and its
     * parameters power further colours. Another gate on that wire offers the colour's "Pipe Signal On" and "Off"
     * triggers, whose parameters require other colours on or off; here one turns on a redstone signal. Saving keeps
     * the signal statements and parameters, and clients learn which wires are powered.
     */
    public static void pipeSignalCarriesAGateSignal(GameTestHelper helper) {
        BlockPos a = new BlockPos(1, 2, 2), b = new BlockPos(2, 2, 2), c = new BlockPos(3, 2, 2);
        PipeBlockEntity first = pipe(helper, a, BCTransport.STONE_ITEM);
        PipeBlockEntity middle = pipe(helper, b, BCTransport.STONE_ITEM);
        PipeBlockEntity last = pipe(helper, c, BCTransport.STONE_ITEM);
        GateLogic sender = gate(first, Direction.NORTH);
        GateLogic receiver = gate(last, Direction.NORTH);

        helper.runAfterDelay(2, () -> {
            for (PipeBlockEntity pipe : List.of(first, middle, last)) {
                if (pipe != first) {
                    pipe.wires().addPart(UP_SOUTH_WEST, DyeColor.RED);
                    pipe.wires().addPart(DOWN_SOUTH_WEST, DyeColor.BLUE);
                }
                if (pipe != last) {
                    pipe.wires().addPart(UP_SOUTH_EAST, DyeColor.RED);
                    pipe.wires().addPart(DOWN_SOUTH_EAST, DyeColor.BLUE);
                }
            }
            ActionWrapper redSignal = action(BCTransportStatements.ACTION_PIPE_SIGNAL[DyeColor.RED.getId()]);
            TriggerWrapper redOn = trigger(BCTransportStatements.TRIGGER_PIPE_SIGNAL[DyeColor.RED.getId() * 2]);
            TriggerWrapper redOff = trigger(BCTransportStatements.TRIGGER_PIPE_SIGNAL[DyeColor.RED.getId() * 2 + 1]);
            require(helper, sender.getAllValidActions().contains(redSignal)
                    && sender.getAllValidActions().contains(action(BCTransportStatements.ACTION_PIPE_SIGNAL[DyeColor.BLUE.getId()]))
                    && !sender.getAllValidActions().contains(action(BCTransportStatements.ACTION_PIPE_SIGNAL[DyeColor.LIME.getId()])),
                    "a gate offers signals in the colours of its pipe's wires");
            require(helper, receiver.getAllValidTriggers().containsAll(List.of(redOn, redOff))
                    && !receiver.getAllValidTriggers().contains(trigger(BCTransportStatements.TRIGGER_PIPE_SIGNAL[DyeColor.LIME.getId() * 2])),
                    "a gate offers signal triggers in the colours of its pipe's wires");
            require(helper, key(redOn.getDescription()).equals("gate.trigger.pipe.wire.active") && key(redSignal.getDescription()).equals("gate.action.pipe.wire"),
                    "the signal statements are named after their colour");

            sender.statements[0].trigger.set(trigger(BCCoreStatements.TRIGGER_TRUE));
            sender.statements[0].action.set(redSignal);
            receiver.statements[0].trigger.set(redOn);
            receiver.statements[0].action.set(action(BCCoreStatements.ACTION_REDSTONE));
            receiver.statements[1].trigger.set(redOff);
        });
        helper.runAfterDelay(6, () -> {
            for (PipeBlockEntity pipe : List.of(first, middle, last)) {
                for (Map.Entry<WirePart, DyeColor> entry : pipe.wires().parts().entrySet()) {
                    require(helper, pipe.wires().isPowered(entry.getKey()) == (entry.getValue() == DyeColor.RED),
                            "only the red wire is powered, at " + pipe.getBlockPos() + " " + entry.getKey());
                }
            }
            require(helper, receiver.triggerOn[0] && !receiver.triggerOn[1] && receiver.isOn, "the far gate sees the red signal on");
            require(helper, last.getRedstoneOutput(Direction.EAST) == 15, "the far gate turns the signal into redstone");
            require(helper, Integer.bitCount(last.getUpdateTag().getCompound("wires").getInt("powered")) == 1,
                    "clients learn which of the far pipe's wires are powered");

            receiver.statements[0].trigger.set(0, TriggerParameterSignal.get(true, DyeColor.BLUE));
            receiver.statements[1].trigger.set(0, TriggerParameterSignal.get(false, DyeColor.BLUE));
        });
        helper.runAfterDelay(10, () -> {
            require(helper, !receiver.triggerOn[0] && last.getRedstoneOutput(Direction.EAST) == 0, "a trigger parameter needing blue on holds the trigger off");
            sender.statements[0].action.set(0, ActionParameterSignal.get(DyeColor.BLUE));
        });
        helper.runAfterDelay(14, () -> {
            require(helper, middle.wires().isPowered(DOWN_SOUTH_EAST) && receiver.triggerOn[0] && last.getRedstoneOutput(Direction.EAST) == 15,
                    "an action parameter powers the blue wire as well, which satisfies the trigger's parameter");

            PipeBlockEntity loaded = new PipeBlockEntity(last.getBlockPos(), helper.getBlockState(c));
            loaded.load(last.saveWithoutMetadata());
            GateLogic reloaded = ((PluggableGate) loaded.getPluggable(Direction.NORTH)).logic();
            require(helper, reloaded.statements[0].trigger.get().equals(receiver.statements[0].trigger.get())
                    && reloaded.statements[0].trigger.get(0) == TriggerParameterSignal.get(true, DyeColor.BLUE)
                    && reloaded.statements[1].trigger.get(0) == TriggerParameterSignal.get(false, DyeColor.BLUE),
                    "saving keeps the signal trigger and its parameters");
            PipeBlockEntity loadedSender = new PipeBlockEntity(first.getBlockPos(), helper.getBlockState(a));
            loadedSender.load(first.saveWithoutMetadata());
            require(helper, ((PluggableGate) loadedSender.getPluggable(Direction.NORTH)).logic().statements[0].action.get(0) == ActionParameterSignal.get(DyeColor.BLUE),
                    "saving keeps the signal action's parameter");

            sender.statements[0].trigger.set(null);
        });
        helper.runAfterDelay(18, () -> {
            require(helper, !middle.wires().isPowered(UP_SOUTH_EAST) && !middle.wires().isPowered(DOWN_SOUTH_EAST), "without the action the wires turn off");
            require(helper, !receiver.triggerOn[0] && receiver.triggerOn[1] && last.getRedstoneOutput(Direction.EAST) == 0,
                    "the far gate sees the signal off and stops its redstone");
            last.replacePluggable(Direction.NORTH, null);
            first.replacePluggable(Direction.NORTH, null);
            require(helper, WireSystems.get(helper.getLevel()).getWireSystemsWithElement(WireSystem.Element.emitter(first.getBlockPos(), Direction.NORTH)).isEmpty(),
                    "a removed gate leaves the wire systems");
            helper.succeed();
        });
    }
}
