package ports.buildcraft.gametest;

import static ports.buildcraft.gametest.BCTests.chest;
import static ports.buildcraft.gametest.BCTests.pipe;
import static ports.buildcraft.gametest.BCTests.require;
import static ports.buildcraft.gametest.BCTests.tile;
import static ports.buildcraft.gametest.BCTests.useAt;
import static ports.buildcraft.gametest.BCTests.useOn;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import ports.buildcraft.core.statements.BCCoreStatements;
import ports.buildcraft.core.statements.StatementParamGateSideOnly;
import ports.buildcraft.energy.BCEnergy;
import ports.buildcraft.factory.BCFactory;
import ports.buildcraft.factory.DistillerBlockEntity;
import ports.buildcraft.silicon.BCSilicon;
import ports.buildcraft.silicon.gate.EnumGateLogic;
import ports.buildcraft.silicon.gate.EnumGateMaterial;
import ports.buildcraft.silicon.gate.EnumGateModifier;
import ports.buildcraft.silicon.gate.GateCopierItem;
import ports.buildcraft.silicon.gate.GateLogic;
import ports.buildcraft.silicon.gate.GateMenu;
import ports.buildcraft.silicon.gate.GateVariant;
import ports.buildcraft.silicon.gate.PluggableGate;
import ports.buildcraft.statement.ActionWrapper;
import ports.buildcraft.statement.Statement;
import ports.buildcraft.statement.StatementWrapper;
import ports.buildcraft.statement.TriggerWrapper;
import ports.buildcraft.transport.BCTransport;
import ports.buildcraft.transport.PipeBlock;
import ports.buildcraft.transport.PipeBlockEntity;
import ports.buildcraft.transport.behaviour.DirectionalBehaviour;
import ports.buildcraft.transport.statements.BCTransportStatements;

/** Loader-neutral GameTests of gates on pipes, the gate copier and the gate GUI's server side. */
public final class GateTests {
    private static final GateVariant IRON_AND = new GateVariant(EnumGateLogic.AND, EnumGateMaterial.IRON, EnumGateModifier.NO_MODIFIER);
    private static final GateVariant IRON_OR = new GateVariant(EnumGateLogic.OR, EnumGateMaterial.IRON, EnumGateModifier.NO_MODIFIER);
    /** Eight pairs in two columns of four. */
    private static final GateVariant GOLD_AND = new GateVariant(EnumGateLogic.AND, EnumGateMaterial.GOLD, EnumGateModifier.NO_MODIFIER);
    /** Two pairs with one trigger and one action parameter each. */
    private static final GateVariant NETHER_QUARTZ = new GateVariant(EnumGateLogic.AND, EnumGateMaterial.NETHER_BRICK, EnumGateModifier.QUARTZ);
    /** How far from a pipe's middle a click lands on a gate, which sits 2 to 4 pixels in from its face. */
    private static final double ON_GATE = 0.5 - 3 / 16.0;

    private GateTests() {}

    /** A connection that keeps what the server sends instead of writing it to a channel. */
    private static final class CapturingConnection extends Connection {
        final List<Packet<?>> sent = new ArrayList<>();

        CapturingConnection() {
            super(PacketFlow.SERVERBOUND);
        }

        @Override
        public void send(Packet<?> packet, @Nullable PacketSendListener listener) {
            sent.add(packet);
        }

        @Nullable
        String lastActionBarKey() {
            for (int i = sent.size() - 1; i >= 0; i--) {
                if (sent.get(i) instanceof ClientboundSystemChatPacket chat && chat.overlay() && chat.content().getContents() instanceof TranslatableContents text) {
                    return text.getKey();
                }
            }
            return null;
        }
    }

    /** A server player beside {@code pos} whose packets go to {@code connection}. */
    private static ServerPlayer player(GameTestHelper helper, BlockPos pos, CapturingConnection connection) {
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "gates"), null);
        new ServerGamePacketListenerImpl(helper.getLevel().getServer(), connection, player);
        BlockPos absolute = helper.absolutePos(pos);
        player.moveTo(absolute.getX() + 0.5, absolute.getY() - 1, absolute.getZ() + 2.5, 0, 0);
        return player;
    }

    private static PluggableGate gate(PipeBlockEntity pipe, Direction side, GateVariant variant) {
        PluggableGate gate = new PluggableGate(BCSilicon.GATE, pipe, side, variant);
        pipe.replacePluggable(side, gate);
        return gate;
    }

    private static TriggerWrapper trigger(Statement statement, @Nullable Direction side) {
        return TriggerWrapper.wrap(statement, side);
    }

    private static ActionWrapper action(Statement statement) {
        return ActionWrapper.wrap(statement, null);
    }

    private static FriendlyByteBuf buffer(Consumer<FriendlyByteBuf> writer) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        writer.accept(buffer);
        return buffer;
    }

    private static String key(Component component) {
        return component.getContents() instanceof TranslatableContents text ? text.getKey() : component.getString();
    }

    /**
     * Right-clicking a pipe face with a gate puts the gate there and uses the item; the gate blocks the face, lets
     * redstone connect to the pipe and picks as its own item, which is named after its material and logic. Saving
     * keeps the variant, statements, parameters and grouping, and clients get whether the gate is on.
     */
    public static void gatePlacesAndKeepsItsConfiguration(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 3, 2);
        PipeBlockEntity pipe = pipe(helper, pos, BCTransport.STONE_ITEM);
        Player player = helper.makeMockPlayer();
        ItemStack gates = new ItemStack(BCSilicon.gateItem(NETHER_QUARTZ), 2);

        require(helper, useOn(helper, player, pos, Direction.EAST, gates) == InteractionResult.CONSUME && gates.getCount() == 1,
                "right-clicking a face with a gate places it and uses one item");
        require(helper, pipe.getPluggable(Direction.EAST) instanceof PluggableGate placed && placed.logic().variant.equals(NETHER_QUARTZ),
                "the placed gate is a nether brick AND gate with a quartz modifier");
        PluggableGate gate = (PluggableGate) pipe.getPluggable(Direction.EAST);
        require(helper, pipe.isBlocked(Direction.EAST), "a gate blocks its face");
        require(helper, gate.getPickStack().getItem() == BCSilicon.gateItem(NETHER_QUARTZ), "a gate picks as its own item");
        require(helper, key(gates.getHoverName()).equals("gate.name") && key(new ItemStack(BCSilicon.gateItem(GateVariant.BASIC)).getHoverName()).equals("gate.name.basic"),
                "gates are named after their material and logic, and the clay brick gate is the basic gate");
        require(helper, gate.logic().statements.length == 2 && gate.logic().connections.length == 1 && !gate.logic().isSplitInTwo()
                && gate.logic().statements[0].trigger.getParamCount() == 1 && gate.logic().statements[0].action.getParamCount() == 1,
                "a quartz modifier trades half of a nether brick gate's four pairs for a parameter each");

        GateLogic logic = gate.logic();
        logic.statements[0].trigger.set(trigger(BCCoreStatements.TRIGGER_TRUE, null));
        logic.statements[0].action.set(action(BCCoreStatements.ACTION_REDSTONE));
        logic.statements[0].action.set(0, StatementParamGateSideOnly.SPECIFIC);
        logic.statements[1].trigger.set(trigger(BCCoreStatements.TRIGGER_TRUE, null));
        logic.connections[0] = true;

        helper.runAfterDelay(2, () -> {
            require(helper, helper.getBlockState(pos).getValue(PipeBlock.REDSTONE), "a pipe with a gate lets redstone connect to it");
            require(helper, logic.isOn && logic.triggerOn[0] && logic.triggerOn[1] && logic.actionOn[0], "grouped always-on triggers turn the action and the gate on");
            CompoundTag update = pipe.getUpdateTag().getCompound("plugs").getCompound("east");
            require(helper, update.getString("id").equals("buildcraft:gate") && update.getCompound("data").getBoolean("on"),
                    "clients get the gate and that it is on, got " + update);
            PluggableGate client = new PluggableGate(BCSilicon.GATE, pipe, Direction.EAST);
            client.readClient(update.getCompound("data"));
            require(helper, client.logic().isOn && client.logic().variant.equals(NETHER_QUARTZ), "a client's gate takes the variant and on state");

            PipeBlockEntity loaded = new PipeBlockEntity(helper.absolutePos(pos), helper.getBlockState(pos));
            loaded.load(pipe.saveWithoutMetadata());
            require(helper, loaded.getPluggable(Direction.EAST) instanceof PluggableGate, "saving keeps the gate");
            GateLogic reloaded = ((PluggableGate) loaded.getPluggable(Direction.EAST)).logic();
            require(helper, reloaded.variant.equals(NETHER_QUARTZ) && reloaded.connections[0], "saving keeps the variant and grouping");
            require(helper, trigger(BCCoreStatements.TRIGGER_TRUE, null).equals(reloaded.statements[0].trigger.get())
                    && action(BCCoreStatements.ACTION_REDSTONE).equals(reloaded.statements[0].action.get())
                    && reloaded.statements[0].action.get(0) == StatementParamGateSideOnly.SPECIFIC
                    && trigger(BCCoreStatements.TRIGGER_TRUE, null).equals(reloaded.statements[1].trigger.get())
                    && reloaded.statements[1].action.get() == null, "saving keeps the statements and parameters");
            helper.succeed();
        });
    }

    /**
     * A gate with "Redstone Signal On" and "Redstone Signal" makes the pipe emit a full signal from every face while
     * a redstone block powers it, which extends a piston beside it; with "Gate Side Only" only the gate's face emits.
     * Without power the signal stops. Nothing conductive touches the pipe, since a pipe's output, like upstream's,
     * powers conductive neighbours strongly and they would feed it back into the trigger.
     */
    public static void gateTurnsRedstoneInputIntoSignal(GameTestHelper helper) {
        BlockPos pos = new BlockPos(4, 3, 4), source = pos.west(), piston = pos.east();
        PipeBlockEntity pipe = pipe(helper, pos, BCTransport.STONE_ITEM);
        GateLogic logic = gate(pipe, Direction.NORTH, NETHER_QUARTZ).logic();
        logic.statements[0].trigger.set(trigger(BCCoreStatements.TRIGGER_REDSTONE_ACTIVE, null));
        logic.statements[0].action.set(action(BCCoreStatements.ACTION_REDSTONE));
        helper.setBlock(piston, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST));

        helper.runAfterDelay(4, () -> {
            require(helper, !logic.isOn && pipe.getRedstoneOutput(Direction.EAST) == 0, "an unpowered pipe's redstone trigger is off");
            helper.setBlock(source, Blocks.REDSTONE_BLOCK);
        });
        helper.runAfterDelay(12, () -> {
            require(helper, logic.triggerOn[0] && logic.isOn, "a powered pipe turns the redstone trigger on");
            for (Direction face : Direction.values()) {
                require(helper, pipe.getRedstoneOutput(face) == 15, "the pipe emits a full signal from its " + face + " face");
            }
            require(helper, helper.getBlockState(piston).getValue(PistonBaseBlock.EXTENDED), "the pipe's signal extends the piston beside it");
            logic.statements[0].action.set(0, StatementParamGateSideOnly.SPECIFIC);
        });
        helper.runAfterDelay(20, () -> {
            require(helper, pipe.getRedstoneOutput(Direction.NORTH) == 15 && pipe.getRedstoneOutput(Direction.EAST) == 0,
                    "with Gate Side Only only the gate's face emits");
            require(helper, !helper.getBlockState(piston).getValue(PistonBaseBlock.EXTENDED), "the piston off the gate's face retracts");
            logic.statements[0].action.set(0, StatementParamGateSideOnly.ANY);
            helper.setBlock(source, Blocks.AIR);
        });
        helper.runAfterDelay(28, () -> {
            require(helper, !logic.isOn && !logic.triggerOn[0], "without power the trigger and gate turn off");
            for (Direction face : Direction.values()) require(helper, pipe.getRedstoneOutput(face) == 0, "the pipe stops emitting from its " + face + " face");
            require(helper, !helper.getBlockState(piston).getValue(PistonBaseBlock.EXTENDED), "the piston stays retracted");
            helper.succeed();
        });
    }

    /**
     * Ungrouped pairs act on their own trigger. Grouped pairs share their triggers: an AND gate needs every trigger
     * in the group on to turn the group's actions on, and an OR gate needs any one.
     */
    public static void gateGroupsPairsByLogic(GameTestHelper helper) {
        PipeBlockEntity pipe = pipe(helper, new BlockPos(2, 3, 2), BCTransport.STONE_ITEM);
        GateLogic and = gate(pipe, Direction.EAST, IRON_AND).logic();
        GateLogic or = gate(pipe, Direction.WEST, IRON_OR).logic();
        for (GateLogic logic : List.of(and, or)) {
            logic.statements[0].trigger.set(trigger(BCCoreStatements.TRIGGER_TRUE, null));
            logic.statements[1].trigger.set(trigger(BCCoreStatements.TRIGGER_REDSTONE_ACTIVE, null));
            logic.statements[0].action.set(action(BCCoreStatements.ACTION_REDSTONE));
        }

        helper.runAfterDelay(2, () -> {
            and.resolveActions();
            require(helper, and.triggerOn[0] && !and.triggerOn[1] && and.actionOn[0] && !and.actionOn[1] && and.isOn,
                    "ungrouped, the first pair acts on its own trigger and the second stays off");
            and.connections[0] = true;
            or.connections[0] = true;
            and.resolveActions();
            or.resolveActions();
            require(helper, !and.actionOn[0] && !and.actionOn[1] && !and.isOn, "a grouped AND gate needs both triggers on");
            require(helper, or.actionOn[0] && or.actionOn[1] && or.isOn, "a grouped OR gate needs one trigger on");
            GateLogic gold = gate(pipe, Direction.UP, new GateVariant(EnumGateLogic.AND, EnumGateMaterial.GOLD, EnumGateModifier.DIAMOND)).logic();
            require(helper, gold.statements.length == 4 && gold.statements[0].trigger.getParamCount() == 3 && gold.statements[0].action.getParamCount() == 3
                    && !gold.isSplitInTwo(), "a diamond modifier trades half of a gold gate's eight pairs for three parameters each");
            helper.succeed();
        });
    }

    /**
     * Using a gate copier on a configured gate copies it; using the full copier on another gate pastes it there,
     * from either hand. An empty copier on an empty gate says there is nothing to copy and the gate's GUI opens.
     * Shift-using the copier clears it.
     */
    public static void gateCopierCopiesPastesAndClears(GameTestHelper helper) {
        BlockPos first = new BlockPos(2, 3, 2), second = new BlockPos(5, 3, 2);
        PipeBlockEntity firstPipe = pipe(helper, first, BCTransport.STONE_ITEM);
        PipeBlockEntity secondPipe = pipe(helper, second, BCTransport.STONE_ITEM);
        GateLogic source = gate(firstPipe, Direction.SOUTH, IRON_AND).logic();
        GateLogic target = gate(secondPipe, Direction.SOUTH, IRON_AND).logic();
        CapturingConnection connection = new CapturingConnection();
        ServerPlayer player = player(helper, first, connection);
        ItemStack copier = new ItemStack(BCSilicon.GATE_COPIER.get());

        useAt(helper, player, first, Direction.SOUTH, ON_GATE, copier);
        require(helper, "chat.gateCopier.noInformation".equals(connection.lastActionBarKey()) && GateCopierItem.getCopiedGateData(copier) == null,
                "an empty copier on an empty gate has nothing to copy, got " + connection.lastActionBarKey());
        require(helper, player.containerMenu instanceof GateMenu, "with nothing to copy the gate's GUI opens");
        player.containerMenu = player.inventoryMenu;

        source.statements[1].trigger.set(trigger(BCCoreStatements.TRIGGER_REDSTONE_INACTIVE, null));
        source.statements[1].action.set(action(BCCoreStatements.ACTION_REDSTONE));
        source.connections[0] = true;
        useAt(helper, player, first, Direction.SOUTH, ON_GATE, copier);
        require(helper, "chat.gateCopier.gateCopied".equals(connection.lastActionBarKey()) && GateCopierItem.getCopiedGateData(copier) != null,
                "a copier copies a configured gate");
        require(helper, player.containerMenu == player.inventoryMenu, "copying does not open the GUI");

        player.setItemInHand(InteractionHand.OFF_HAND, copier);
        useAt(helper, player, second, Direction.SOUTH, ON_GATE, ItemStack.EMPTY);
        require(helper, "chat.gateCopier.gatePasted".equals(connection.lastActionBarKey()), "a copier in the off hand pastes");
        require(helper, trigger(BCCoreStatements.TRIGGER_REDSTONE_INACTIVE, null).equals(target.statements[1].trigger.get())
                && action(BCCoreStatements.ACTION_REDSTONE).equals(target.statements[1].action.get()) && target.connections[0]
                && target.statements[0].trigger.get() == null, "pasting gives the second gate the first one's statements and grouping");

        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.MAIN_HAND, copier);
        player.setShiftKeyDown(true);
        copier.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        require(helper, GateCopierItem.getCopiedGateData(copier) == null, "shift-using the copier clears it");
        helper.succeed();
    }

    /**
     * Clicking a gate opens its GUI, whose client menu lists the triggers and actions the gate can use by face. The
     * server takes statements, parameters and grouping from that menu only when the gate could use them, and stops
     * listening once the gate is gone. Whether each trigger and action is on reaches the menu as data.
     */
    public static void gateMenuOnlyAcceptsWhatTheGateCanUse(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 3, 2);
        PipeBlockEntity pipe = pipe(helper, pos, BCTransport.STONE_ITEM);
        chest(helper, pos.north(), new ItemStack(Items.COBBLESTONE));
        PluggableGate gate = gate(pipe, Direction.EAST, NETHER_QUARTZ);
        GateLogic logic = gate.logic();
        CapturingConnection connection = new CapturingConnection();
        ServerPlayer player = player(helper, pos, connection);

        helper.runAfterDelay(2, () -> {
            useAt(helper, player, pos, Direction.EAST, ON_GATE, ItemStack.EMPTY);
            require(helper, player.containerMenu instanceof GateMenu, "clicking a gate opens its GUI");
            GateMenu menu = (GateMenu) player.containerMenu;
            int id = menu.containerId;

            GateMenu client = new GateMenu(id, player.getInventory(), buffer(buffer -> GateMenu.writeOpening(buffer, gate)));
            TriggerWrapper contains = trigger(BCCoreStatements.TRIGGER_INVENTORY_CONTAINS, Direction.NORTH);
            require(helper, client.variant.equals(NETHER_QUARTZ) && client.side == Direction.EAST && client.slotHeight == 2, "the client gets the gate's variant and face");
            require(helper, client.triggerGroups.get(0).part() == null && client.triggerGroups.get(0).values().get(0) == null
                    && client.triggerGroups.get(0).values().contains(trigger(BCCoreStatements.TRIGGER_TRUE, null)),
                    "the centre's triggers come first, led by an empty slot");
            require(helper, client.triggerGroups.stream().anyMatch(group -> group.part() == Direction.NORTH && group.values().contains(contains)),
                    "the chest's inventory triggers are offered on its face");
            require(helper, client.isPossibleTrigger(contains) && !client.isPossibleTrigger(trigger(BCCoreStatements.TRIGGER_INVENTORY_CONTAINS, Direction.SOUTH))
                    && client.isPossibleAction(action(BCCoreStatements.ACTION_REDSTONE)), "the client knows which statements the gate can use");

            GateMenu.receive(player, buffer(buffer -> GateMenu.writeStatementChange(buffer, id, 0, false, contains)));
            GateMenu.receive(player, buffer(buffer -> GateMenu.writeStatementChange(buffer, id, 1, false,
                    trigger(BCCoreStatements.TRIGGER_INVENTORY_CONTAINS, Direction.SOUTH))));
            GateMenu.receive(player, buffer(buffer -> GateMenu.writeStatementChange(buffer, id + 1, 1, false, trigger(BCCoreStatements.TRIGGER_TRUE, null))));
            require(helper, contains.equals(logic.statements[0].trigger.get()) && logic.statements[1].trigger.get() == null,
                    "the server takes a trigger the gate can use and ignores one it cannot, or one for another menu");

            StatementWrapper redstone = action(BCCoreStatements.ACTION_REDSTONE);
            GateMenu.receive(player, buffer(buffer -> GateMenu.writeStatementChange(buffer, id, 0, true, redstone)));
            GateMenu.receive(player, buffer(buffer -> GateMenu.writeParameterChange(buffer, id, 0, true, 0, StatementParamGateSideOnly.SPECIFIC)));
            GateMenu.receive(player, buffer(buffer -> GateMenu.writeParameterChange(buffer, id, 0, false, 0, StatementParamGateSideOnly.SPECIFIC)));
            require(helper, redstone.equals(logic.statements[0].action.get()) && logic.statements[0].action.get(0) == StatementParamGateSideOnly.SPECIFIC
                    && logic.statements[0].trigger.get(0) != StatementParamGateSideOnly.SPECIFIC,
                    "the server takes a parameter its statement keeps and ignores one it does not");

            logic.resolveActions();
            require(helper, menu.isTriggerOn(0) && menu.isActionOn(0) && !menu.isTriggerOn(1) && !menu.isActionOn(1),
                    "whether each trigger and action is on reaches the menu");

            GateMenu.receive(player, buffer(buffer -> GateMenu.writeConnectionChange(buffer, id, 0, true)));
            GateMenu.receive(player, buffer(buffer -> GateMenu.writeConnectionChange(buffer, id, 4, true)));
            require(helper, logic.connections[0], "the server groups pairs and ignores pairs the gate does not have");
            logic.resolveActions();
            require(helper, menu.isTriggerOn(0) && !menu.isActionOn(0), "grouped with an empty trigger, the AND gate's action turns off");

            GateMenu.receive(player, buffer(buffer -> GateMenu.writeStatementChange(buffer, id, 0, false, null)));
            require(helper, logic.statements[0].trigger.get() == null, "an empty statement clears the slot");

            pipe.replacePluggable(Direction.EAST, null);
            GateMenu.receive(player, buffer(buffer -> GateMenu.writeStatementChange(buffer, id, 0, false, contains)));
            require(helper, logic.statements[0].trigger.get() == null && !menu.stillValid(player), "the server stops listening once the gate is gone");

            GateLogic gold = gate(pipe, Direction.WEST, GOLD_AND).logic();
            useAt(helper, player, pos, Direction.WEST, ON_GATE, ItemStack.EMPTY);
            require(helper, player.containerMenu instanceof GateMenu goldMenu && goldMenu.slotHeight == 4, "a gold gate's GUI shows two columns of four pairs");
            int goldId = player.containerMenu.containerId;
            GateMenu.receive(player, buffer(buffer -> GateMenu.writeConnectionChange(buffer, goldId, 3, true)));
            GateMenu.receive(player, buffer(buffer -> GateMenu.writeConnectionChange(buffer, goldId, 4, true)));
            require(helper, !gold.connections[3] && gold.connections[4],
                    "the server refuses to group the last pair of a gold gate's left column with the first of its right, which the GUI never links");
            helper.succeed();
        });
    }

    /**
     * An iron pipe's gate can turn the pipe to face any connected side, and a chest or tank beside a pipe offers its
     * inventory or fluid triggers, which read its contents.
     */
    public static void gateTurnsIronPipeAndReadsInventories(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 3, 3);
        PipeBlockEntity iron = pipe(helper, pos, BCTransport.IRON_ITEM);
        pipe(helper, pos.east(), BCTransport.STONE_ITEM);
        pipe(helper, pos.west(), BCTransport.STONE_ITEM);
        chest(helper, pos.south(), new ItemStack(Items.COBBLESTONE, 3));
        helper.setBlock(pos.north(), BCFactory.DISTILLER.get());
        DistillerBlockEntity distiller = tile(helper, pos.north(), DistillerBlockEntity.class);
        require(helper, distiller.fluidPort(Direction.SOUTH).fill(BCEnergy.OIL.heat(0).source(), 1000, false) == 1000, "oil fills the distiller's input");
        GateLogic logic = gate(iron, Direction.UP, GOLD_AND).logic();

        helper.runAfterDelay(2, () -> {
            ActionWrapper east = action(BCTransportStatements.ACTION_PIPE_DIRECTION[Direction.EAST.ordinal()]);
            ActionWrapper west = action(BCTransportStatements.ACTION_PIPE_DIRECTION[Direction.WEST.ordinal()]);
            require(helper, logic.getAllValidActions().containsAll(List.of(east, west))
                    && !logic.getAllValidActions().contains(action(BCTransportStatements.ACTION_PIPE_DIRECTION[Direction.UP.ordinal()])),
                    "an iron pipe's gate can face the pipe to its connected sides only");
            logic.statements[0].trigger.set(trigger(BCCoreStatements.TRIGGER_INVENTORY_CONTAINS, Direction.SOUTH));
            logic.statements[0].action.set(east);
            logic.statements[1].trigger.set(trigger(BCCoreStatements.TRIGGER_INVENTORY_EMPTY, Direction.SOUTH));
            logic.statements[2].trigger.set(trigger(BCCoreStatements.TRIGGER_FLUID_CONTAINS, Direction.NORTH));
            logic.statements[3].trigger.set(trigger(BCCoreStatements.TRIGGER_FLUID_EMPTY, Direction.NORTH));
        });
        helper.runAfterDelay(4, () -> {
            require(helper, logic.triggerOn[0] && !logic.triggerOn[1], "the chest holds items, so it is not empty");
            require(helper, logic.triggerOn[2] && !logic.triggerOn[3],
                    "the distiller's input holds oil, so it is not empty even though that tank cannot be drained");
            require(helper, ((DirectionalBehaviour) iron.behaviour()).currentDir() == Direction.EAST, "the gate turns the iron pipe east");
            logic.statements[0].action.set(action(BCTransportStatements.ACTION_PIPE_DIRECTION[Direction.WEST.ordinal()]));
        });
        helper.runAfterDelay(6, () -> {
            require(helper, ((DirectionalBehaviour) iron.behaviour()).currentDir() == Direction.WEST, "the gate turns the iron pipe west");
            helper.succeed();
        });
    }
}
