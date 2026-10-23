package ports.buildcraft.gametest;

import static ports.buildcraft.gametest.BCTests.chest;
import static ports.buildcraft.gametest.BCTests.count;
import static ports.buildcraft.gametest.BCTests.pipe;
import static ports.buildcraft.gametest.BCTests.require;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import ports.buildcraft.core.statements.BCCoreStatements;
import ports.buildcraft.lib.Power;
import ports.buildcraft.silicon.BCSilicon;
import ports.buildcraft.silicon.gate.EnumGateLogic;
import ports.buildcraft.silicon.gate.EnumGateMaterial;
import ports.buildcraft.silicon.gate.EnumGateModifier;
import ports.buildcraft.silicon.gate.GateVariant;
import ports.buildcraft.silicon.gate.PluggableGate;
import ports.buildcraft.statement.Action;
import ports.buildcraft.statement.ActionWrapper;
import ports.buildcraft.statement.TriggerWrapper;
import ports.buildcraft.transport.BCTransport;
import ports.buildcraft.transport.PipeBlockEntity;
import ports.buildcraft.transport.StripesPipeBlock;
import ports.buildcraft.transport.behaviour.StripesBehaviour;
import ports.buildcraft.transport.flow.ItemFlow;
import ports.buildcraft.transport.statements.BCTransportStatements;

/** Loader-neutral GameTests of the stripes pipe. */
public final class StripesPipeTests {
    private static final GateVariant IRON_AND = new GateVariant(EnumGateLogic.AND, EnumGateMaterial.IRON, EnumGateModifier.NO_MODIFIER);
    private static final double SPEED = 0.25;

    private StripesPipeTests() {}

    private static List<ItemEntity> itemsAround(GameTestHelper helper, BlockPos pos) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(pos)).inflate(1.5));
    }

    private static long dropped(List<ItemEntity> entities, net.minecraft.world.item.Item item) {
        return entities.stream().filter(e -> e.getItem().is(item)).mapToLong(e -> e.getItem().getCount()).sum();
    }

    /**
     * Upstream PipeBehaviourStripes: the pipe points away from its only connection, which the block draws as the rod.
     * A block item reaching its open end is placed in front; power breaks the block in front, and the drops come
     * back through the pipe; an item no stripes handler can use drops as a pipe end would drop it.
     */
    public static void stripesPipePlacesBreaksAndDrops(GameTestHelper helper) {
        ChestBlockEntity chest = chest(helper, new BlockPos(0, 2, 1));
        pipe(helper, new BlockPos(1, 2, 1), BCTransport.STONE_ITEM);
        BlockPos at = new BlockPos(2, 2, 1);
        BlockPos front = at.east();
        PipeBlockEntity stripes = pipe(helper, at, BCTransport.STRIPES_ITEM);
        StripesBehaviour behaviour = (StripesBehaviour) stripes.behaviour();
        ItemFlow items = (ItemFlow) stripes.flow();

        helper.startSequence()
                .thenExecuteAfter(2, () -> {
                    require(helper, behaviour.direction() == Direction.EAST, "the pipe points away from its connection, points " + behaviour.direction());
                    require(helper, helper.getBlockState(at).getValue(StripesPipeBlock.DIRECTION) == StripesPipeBlock.Facing.EAST,
                            "the block draws the rod on the east end");
                    require(helper, stripes.energyPort(Direction.UP) != null && stripes.energyPort(Direction.UP).acceptsRedstoneEngine(),
                            "the stripes pipe takes power, also from redstone engines");
                    items.injectItem(new ItemStack(Items.DIRT), true, Direction.WEST, null, SPEED);
                })
                .thenWaitUntil(() -> require(helper, helper.getBlockState(front).is(Blocks.DIRT), "the dirt must be placed in front of the pipe"))
                .thenExecute(() -> {
                    require(helper, dropped(itemsAround(helper, front), Items.DIRT) == 0, "a placed block leaves nothing behind");
                    require(helper, helper.getBlockState(front).is(Blocks.DIRT), "without power the pipe breaks nothing");
                    stripes.energyPort(Direction.UP).receive(Power.toUnits(64 * Power.MJ), false);
                    require(helper, behaviour.battery().stored() > 0, "the pipe stores the power, holds "
                            + Power.formatMj(behaviour.battery().stored()));
                })
                .thenWaitUntil(() -> require(helper, helper.getBlockState(front).isAir(), "power must break the dirt in front"))
                .thenWaitUntil(() -> require(helper, count(chest, Items.DIRT) == 1, "the broken dirt comes back through the pipe to the chest"))
                .thenExecute(() -> {
                    require(helper, behaviour.battery().stored() < 64 * Power.MJ, "breaking spends power");
                    items.injectItem(new ItemStack(Items.STICK), true, Direction.WEST, null, SPEED);
                })
                .thenWaitUntil(() -> require(helper, dropped(itemsAround(helper, at), Items.STICK) == 1, "a stick it cannot use must drop"))
                .thenExecute(() -> {
                    require(helper, helper.getBlockState(front).isAir(), "the stick must not be placed");
                    CompoundTag saved = new CompoundTag();
                    behaviour.save(saved);
                    StripesBehaviour loaded = new StripesBehaviour(stripes);
                    loaded.load(saved);
                    require(helper, loaded.direction() == Direction.EAST && loaded.battery().stored() == behaviour.battery().stored(),
                            "saving keeps the direction and power");
                })
                .thenSucceed();
    }

    /** A gate's pipe direction action turns the stripes pipe to a free face, and the block draws the rod there. */
    public static void stripesPipeTurnsByGate(GameTestHelper helper) {
        chest(helper, new BlockPos(0, 2, 1));
        pipe(helper, new BlockPos(1, 2, 1), BCTransport.STONE_ITEM);
        BlockPos at = new BlockPos(2, 2, 1);
        PipeBlockEntity stripes = pipe(helper, at, BCTransport.STRIPES_ITEM);
        StripesBehaviour behaviour = (StripesBehaviour) stripes.behaviour();

        helper.startSequence()
                .thenExecuteAfter(2, () -> {
                    List<Action.Internal> actions = new ArrayList<>();
                    behaviour.addInternalActions(actions);
                    require(helper, actions.contains(BCTransportStatements.ACTION_PIPE_DIRECTION[Direction.UP.ordinal()])
                            && !actions.contains(BCTransportStatements.ACTION_PIPE_DIRECTION[Direction.WEST.ordinal()]),
                            "the stripes pipe offers its free faces and not its connection, offers " + actions);
                    PluggableGate gate = new PluggableGate(BCSilicon.GATE, stripes, Direction.NORTH, IRON_AND);
                    stripes.replacePluggable(Direction.NORTH, gate);
                    gate.logic().statements[0].trigger.set(TriggerWrapper.wrap(BCCoreStatements.TRIGGER_TRUE, null));
                    gate.logic().statements[0].action.set(ActionWrapper.wrap(BCTransportStatements.ACTION_PIPE_DIRECTION[Direction.UP.ordinal()], null));
                })
                .thenWaitUntil(() -> require(helper, behaviour.direction() == Direction.UP, "the gate turns the pipe up, points " + behaviour.direction()))
                .thenExecuteAfter(5, () -> {
                    require(helper, behaviour.direction() == Direction.UP, "the pipe keeps pointing up while the gate holds it");
                    require(helper, helper.getBlockState(at).getValue(StripesPipeBlock.DIRECTION) == StripesPipeBlock.Facing.UP,
                            "the block draws the rod on the top end");
                })
                .thenSucceed();
    }
}
