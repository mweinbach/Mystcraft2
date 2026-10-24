package ports.gridwright.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import ports.gridwright.machine.GeneratorBlockEntity;
import ports.gridwright.machine.GeneratorKind;
import ports.gridwright.power.ConverterBlock;
import ports.gridwright.power.ConverterBlockEntity;
import ports.gridwright.power.ConverterMode;
import ports.gridwright.registry.ModPower;
import ports.gridwright.transfer.NodeBlock;
import ports.gridwright.transfer.NodeBlockEntity;
import ports.gridwright.transfer.NodeKind;
import ports.gridwright.transfer.NodeMenu;
import ports.gridwright.transfer.TransferRegistry;

import static ports.gridwright.gametest.GameTestSupport.TEMPLATE;
import static ports.gridwright.gametest.GameTestSupport.check;
import static ports.gridwright.gametest.GameTestSupport.connectedPlayer;
import static ports.gridwright.gametest.GameTestSupport.generator;
import static ports.gridwright.gametest.GameTestSupport.platform;

/**
 * Phase 3 additions to the transfer nodes: redstone control and the energy transfer node. Same line as
 * TransferGameTests: the served block at x = 1, the node at x = 2 facing west, a pipe, a target.
 */
public class NodeControlGameTests {

    private static final BlockPos FRONT = new BlockPos(1, 2, 1);
    private static final BlockPos NODE = new BlockPos(2, 2, 1);
    private static final BlockPos PIPE = new BlockPos(3, 2, 1);
    private static final BlockPos END = new BlockPos(4, 2, 1);

    private static NodeBlockEntity node(GameTestHelper helper, NodeKind kind) {
        helper.setBlock(NODE, TransferRegistry.NODES.get(kind).get().defaultBlockState().setValue(NodeBlock.FACING, Direction.WEST));
        helper.setBlock(PIPE, TransferRegistry.PIPE.get());
        return (NodeBlockEntity) helper.getBlockEntity(NODE);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void energyNodeMovesGeneratorOutput(GameTestHelper helper) {
        GeneratorBlockEntity source = generator(helper, GeneratorKind.REDSTONE, FRONT);
        source.setItem(0, new ItemStack(Items.REDSTONE_BLOCK));
        NodeBlockEntity node = node(helper, NodeKind.ENERGY_TRANSFER);
        helper.setBlock(END, Blocks.BARREL);
        BlockEntity sink = helper.getBlockEntity(END);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    int burned = 1_800 - source.burnLeft();
                    long sunk = platform().sunk(sink);
                    check(sunk > 200, "Too little reached the barrel: " + sunk);
                    // The generator cannot reach the barrel itself, so every unit came through the node.
                    check(sunk + source.energy().stored() == 40L * burned, "Energy lost or made: sunk " + sunk + " + stored "
                            + source.energy().stored() + " vs " + 40L * burned);
                    check(node.operations() > 0, "No operations counted");
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void energyNodeKeepsToItsRate(GameTestHelper helper) {
        GeneratorBlockEntity source = generator(helper, GeneratorKind.REDSTONE, FRONT);
        net.minecraft.nbt.CompoundTag full = source.saveWithoutMetadata();
        full.putLong("Energy", GeneratorKind.CAPACITY);
        source.load(full);
        node(helper, NodeKind.ENERGY_TRANSFER);
        helper.setBlock(END, Blocks.BARREL);
        BlockEntity sink = helper.getBlockEntity(END);
        long placed = helper.getTick();
        helper.startSequence()
                .thenIdle(20)
                .thenExecute(() -> {
                    // The sink exists once the node has found it, so it is only read after the node has run.
                    long ticks = helper.getTick() - placed;
                    long moved = platform().sunk(sink);
                    check(moved > 0 && moved <= (ticks + 1) * NodeKind.ENERGY, "Moved " + moved + " in " + ticks + " ticks");
                })
                .thenSucceed();
    }

    /**
     * A target that takes only part of the tick's energy must not stop the rest reaching the next one:
     * a converter with room for 300 comes first on the network and a barrel second, so every tick still
     * moves the node's full 1,000.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void energyNodeFillsEveryTargetInOneTick(GameTestHelper helper) {
        GeneratorBlockEntity source = generator(helper, GeneratorKind.REDSTONE, FRONT);
        net.minecraft.nbt.CompoundTag full = source.saveWithoutMetadata();
        full.putLong("Energy", GeneratorKind.CAPACITY);
        source.load(full);
        node(helper, NodeKind.ENERGY_TRANSFER);
        // North of the pipe is searched before east, so the nearly full converter is the first target.
        BlockPos converterPos = PIPE.north();
        helper.setBlock(converterPos, ModPower.POWER_CONVERTER.get().defaultBlockState()
                .setValue(ConverterBlock.MODE, ConverterMode.TO_GRID));
        ConverterBlockEntity converter = (ConverterBlockEntity) helper.getBlockEntity(converterPos);
        net.minecraft.nbt.CompoundTag almost = converter.saveWithoutMetadata();
        almost.putLong("Energy", ConverterBlockEntity.CAPACITY - 300);
        converter.load(almost);
        helper.setBlock(END, Blocks.BARREL);
        helper.startSequence()
                .thenWaitUntil(() -> check(converter.stored() == ConverterBlockEntity.CAPACITY, "Converter not filled"))
                .thenExecuteFor(10, () -> {
                    long taken = GeneratorKind.CAPACITY - source.energy().stored();
                    check(taken % NodeKind.ENERGY == 0, "A tick moved less than the node's rate: " + taken + " taken in total");
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void nodesFollowTheirRedstoneMode(GameTestHelper helper) {
        helper.setBlock(FRONT, Blocks.CHEST);
        Container source = (Container) helper.getBlockEntity(FRONT);
        source.setItem(0, new ItemStack(Items.COBBLESTONE, 16));
        NodeBlockEntity node = node(helper, NodeKind.ITEM_TRANSFER);
        helper.setBlock(END, Blocks.CHEST);
        Container target = (Container) helper.getBlockEntity(END);
        NodeMenu menu = new NodeMenu(0, connectedPlayer(helper, GameTestSupport.Owner.random("gwRs")).getInventory(),
                node.inventory(), new net.minecraft.world.inventory.SimpleContainerData(NodeBlockEntity.DATA_COUNT), p -> true,
                node::cycleRedstone);
        check(node.redstone() == NodeBlockEntity.Redstone.IGNORED, "Nodes start ignoring redstone");
        check(menu.clickMenuButton(null, NodeMenu.BUTTON_REDSTONE), "The redstone button was not handled");
        check(node.redstone() == NodeBlockEntity.Redstone.WITH_SIGNAL, "Button did not switch mode: " + node.redstone());
        helper.startSequence()
                .thenExecuteFor(45, () -> check(target.isEmpty(), "Moved items without the signal it needs"))
                .thenExecute(() -> helper.setBlock(NODE.above(), Blocks.REDSTONE_BLOCK))
                .thenWaitUntil(() -> check(!target.isEmpty(), "Signal did not start the node"))
                .thenExecute(() -> {
                    node.cycleRedstone();
                    check(node.redstone() == NodeBlockEntity.Redstone.WITHOUT_SIGNAL, "Mode " + node.redstone());
                    net.minecraft.nbt.CompoundTag saved = node.saveWithoutMetadata();
                    node.load(saved);
                    check(node.redstone() == NodeBlockEntity.Redstone.WITHOUT_SIGNAL, "Mode lost on reload");
                })
                .thenSucceed();
    }
}
