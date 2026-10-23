package ports.buildcraft.gametest;

import static ports.buildcraft.gametest.BCTests.chest;
import static ports.buildcraft.gametest.BCTests.count;
import static ports.buildcraft.gametest.BCTests.pipe;
import static ports.buildcraft.gametest.BCTests.require;

import com.mojang.authlib.GameProfile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
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
    private static final GameProfile OWNER = new GameProfile(
            UUID.nameUUIDFromBytes("buildcraft:stripes_test_owner".getBytes(StandardCharsets.UTF_8)), "StripesOwner");

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

    /** Hands {@code stack} to the stripes pipe as if it had just dropped out of the pipe's open end. */
    private static void reachEnd(GameTestHelper helper, StripesBehaviour behaviour, BlockPos at, ItemStack stack) {
        ItemEntity entity = new ItemEntity(helper.getLevel(), helper.absolutePos(at).getX() + 0.5, helper.absolutePos(at).getY() + 0.5,
                helper.absolutePos(at).getZ() + 0.5, stack);
        behaviour.onDrop(entity);
        require(helper, entity.getItem().isEmpty(), "the stripes pipe must take the " + stack + " it is handed");
    }

    private static boolean isPipe(GameTestHelper helper, BlockPos pos, ports.buildcraft.transport.PipeDefinition definition) {
        return helper.getBlockState(pos).is(definition.block());
    }

    /**
     * Two item pipes reaching a stripes pipe in the same tick both lay themselves: the second follows the stripes pipe
     * to where the first moved it, so it ends two blocks on and no pipe is lost.
     */
    public static void stripesPipeLaysTwoPipesInOneTick(GameTestHelper helper) {
        ChestBlockEntity chest = chest(helper, new BlockPos(0, 2, 1));
        pipe(helper, new BlockPos(1, 2, 1), BCTransport.STONE_ITEM);
        BlockPos at = new BlockPos(2, 2, 1);
        PipeBlockEntity stripes = pipe(helper, at, BCTransport.STRIPES_ITEM);
        StripesBehaviour behaviour = (StripesBehaviour) stripes.behaviour();

        helper.startSequence()
                .thenExecuteAfter(2, () -> {
                    require(helper, behaviour.direction() == Direction.EAST, "the pipe points east, points " + behaviour.direction());
                    reachEnd(helper, behaviour, at, new ItemStack(BCTransport.STONE_ITEM.item()));
                    reachEnd(helper, behaviour, at, new ItemStack(BCTransport.COBBLESTONE_ITEM.item()));
                })
                .thenExecuteAfter(1, () -> {
                    require(helper, isPipe(helper, at, BCTransport.STONE_ITEM), "the first pipe is laid where the stripes pipe was, found "
                            + helper.getBlockState(at));
                    require(helper, isPipe(helper, at.east(), BCTransport.COBBLESTONE_ITEM), "the second pipe is laid one block on, found "
                            + helper.getBlockState(at.east()));
                    require(helper, isPipe(helper, at.east(2), BCTransport.STRIPES_ITEM), "the stripes pipe moves two blocks on, found "
                            + helper.getBlockState(at.east(2)));
                })
                .thenExecuteAfter(40, () -> {
                    require(helper, dropped(itemsAround(helper, at.east()), BCTransport.STONE_ITEM.item()) == 0
                            && dropped(itemsAround(helper, at.east()), BCTransport.COBBLESTONE_ITEM.item()) == 0
                            && count(chest, BCTransport.STONE_ITEM.item()) == 0 && count(chest, BCTransport.COBBLESTONE_ITEM.item()) == 0,
                            "both pipes are used, none comes back or drops");
                    PipeBlockEntity moved = (PipeBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(at.east(2)));
                    require(helper, ((StripesBehaviour) moved.behaviour()).direction() == Direction.EAST, "the moved stripes pipe still points east");
                })
                .thenSucceed();
    }

    /**
     * Two void pipes reaching a stripes pipe in the same tick retract it twice, each removing the pipe behind it; both
     * void pipes and both removed pipes come back down the pipe to the chest, so nothing is lost.
     */
    public static void stripesPipeRetractsTwiceInOneTick(GameTestHelper helper) {
        ChestBlockEntity chest = chest(helper, new BlockPos(0, 2, 1));
        pipe(helper, new BlockPos(1, 2, 1), BCTransport.COBBLESTONE_ITEM);
        pipe(helper, new BlockPos(2, 2, 1), BCTransport.STONE_ITEM);
        pipe(helper, new BlockPos(3, 2, 1), BCTransport.STONE_ITEM);
        BlockPos at = new BlockPos(4, 2, 1);
        PipeBlockEntity stripes = pipe(helper, at, BCTransport.STRIPES_ITEM);
        StripesBehaviour behaviour = (StripesBehaviour) stripes.behaviour();

        helper.startSequence()
                .thenExecuteAfter(2, () -> {
                    require(helper, behaviour.direction() == Direction.EAST, "the pipe points east, points " + behaviour.direction());
                    reachEnd(helper, behaviour, at, new ItemStack(BCTransport.VOID_ITEM.item()));
                    reachEnd(helper, behaviour, at, new ItemStack(BCTransport.VOID_ITEM.item()));
                })
                .thenExecuteAfter(1, () -> {
                    require(helper, isPipe(helper, new BlockPos(2, 2, 1), BCTransport.STRIPES_ITEM),
                            "the stripes pipe moves two blocks back, found " + helper.getBlockState(new BlockPos(2, 2, 1)));
                    require(helper, helper.getBlockState(new BlockPos(3, 2, 1)).isAir() && helper.getBlockState(at).isAir(),
                            "both retracted blocks are empty");
                })
                .thenWaitUntil(() -> require(helper, count(chest, BCTransport.VOID_ITEM.item()) == 2 && count(chest, BCTransport.STONE_ITEM.item()) == 2,
                        "both void pipes and both removed stone pipes come back to the chest, chest holds "
                                + count(chest, BCTransport.VOID_ITEM.item()) + " void and " + count(chest, BCTransport.STONE_ITEM.item()) + " stone"))
                .thenSucceed();
    }

    /**
     * Upstream {@code BlockUtil.breakBlockAndGetDrops} posted the break event for the pipe's owner: while the loader's
     * break event is cancelled at the block in front, powering the pipe breaks nothing (and the event names the owner);
     * once it is allowed, the block breaks and its drop comes back.
     */
    public static void stripesPipeRespectsRefusedBreak(GameTestHelper helper) {
        ChestBlockEntity chest = chest(helper, new BlockPos(0, 2, 1));
        pipe(helper, new BlockPos(1, 2, 1), BCTransport.STONE_ITEM);
        BlockPos at = new BlockPos(2, 2, 1);
        BlockPos front = at.east();
        PipeBlockEntity stripes = pipe(helper, at, BCTransport.STRIPES_ITEM);
        stripes.setOwner(OWNER);
        StripesBehaviour behaviour = (StripesBehaviour) stripes.behaviour();
        helper.setBlock(front, Blocks.DIRT);

        helper.startSequence()
                .thenExecuteAfter(2, () -> {
                    require(helper, behaviour.direction() == Direction.EAST, "the pipe points east, points " + behaviour.direction());
                    TestProtection.refuse(helper, front);
                    stripes.energyPort(Direction.UP).receive(Power.toUnits(64 * Power.MJ), false);
                })
                .thenWaitUntil(() -> require(helper, TestProtection.refusedPlayer(helper, front) != null,
                        "breaking the block in front must fire the loader's break event"))
                .thenExecuteAfter(5, () -> {
                    require(helper, OWNER.getName().equals(TestProtection.refusedPlayer(helper, front)),
                            "the break event is fired for the pipe's owner, was " + TestProtection.refusedPlayer(helper, front));
                    require(helper, helper.getBlockState(front).is(Blocks.DIRT), "a refused break leaves the block in front");
                    require(helper, count(chest, Items.DIRT) == 0 && dropped(itemsAround(helper, front), Items.DIRT) == 0,
                            "a refused break drops nothing");
                    TestProtection.allow(helper, front);
                    stripes.energyPort(Direction.UP).receive(Power.toUnits(64 * Power.MJ), false);
                })
                .thenWaitUntil(() -> require(helper, helper.getBlockState(front).isAir(), "once allowed, power breaks the dirt"))
                .thenWaitUntil(() -> require(helper, count(chest, Items.DIRT) == 1, "the broken dirt comes back to the chest"))
                .thenSucceed();
    }

    /**
     * Upstream {@code PipeExtensionManager} posted a break event for the stripes pipe's block and a place event for
     * the block it moves into, for the owner, and kept the pipe where it was when either was cancelled. Here a refused
     * break of the stripes pipe's block, or a refused place into the grass in front (Forge's place event, Fabric's break
     * event for the grass it would replace), leaves both blocks alone and sends the pipe item back to the chest; once
     * allowed, the pipe is laid by the owner's fake player, so it has the same owner.
     */
    public static void stripesPipeRespectsRefusedExtension(GameTestHelper helper) {
        ChestBlockEntity chest = chest(helper, new BlockPos(0, 2, 1));
        pipe(helper, new BlockPos(1, 2, 1), BCTransport.STONE_ITEM);
        BlockPos at = new BlockPos(2, 2, 1);
        BlockPos front = at.east();
        PipeBlockEntity stripes = pipe(helper, at, BCTransport.STRIPES_ITEM);
        stripes.setOwner(OWNER);
        StripesBehaviour behaviour = (StripesBehaviour) stripes.behaviour();
        helper.setBlock(front.below(), Blocks.DIRT);
        helper.setBlock(front, Blocks.GRASS);

        helper.startSequence()
                .thenExecuteAfter(2, () -> {
                    require(helper, behaviour.direction() == Direction.EAST, "the pipe points east, points " + behaviour.direction());
                    TestProtection.refuse(helper, at);
                    reachEnd(helper, behaviour, at, new ItemStack(BCTransport.COBBLESTONE_ITEM.item()));
                })
                .thenExecuteAfter(1, () -> {
                    require(helper, OWNER.getName().equals(TestProtection.refusedPlayer(helper, at)),
                            "moving the stripes pipe fires the break event for its owner, was " + TestProtection.refusedPlayer(helper, at));
                    require(helper, isPipe(helper, at, BCTransport.STRIPES_ITEM) && helper.getBlockState(front).is(Blocks.GRASS),
                            "a refused break keeps the stripes pipe and the grass, found " + helper.getBlockState(at) + " and " + helper.getBlockState(front));
                })
                .thenWaitUntil(() -> require(helper, count(chest, BCTransport.COBBLESTONE_ITEM.item()) == 1,
                        "the refused pipe comes back to the chest"))
                .thenExecute(() -> {
                    TestProtection.allow(helper, at);
                    TestProtection.refuse(helper, front);
                    PipeBlockEntity current = (PipeBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(at));
                    reachEnd(helper, (StripesBehaviour) current.behaviour(), at, new ItemStack(BCTransport.COBBLESTONE_ITEM.item()));
                })
                .thenExecuteAfter(1, () -> {
                    require(helper, OWNER.getName().equals(TestProtection.refusedPlayer(helper, front)),
                            "moving into the grass fires the loader's event there for the owner, was " + TestProtection.refusedPlayer(helper, front));
                    require(helper, isPipe(helper, at, BCTransport.STRIPES_ITEM) && helper.getBlockState(front).is(Blocks.GRASS),
                            "a refused place keeps the stripes pipe and the grass, found " + helper.getBlockState(at) + " and " + helper.getBlockState(front));
                })
                .thenWaitUntil(() -> require(helper, count(chest, BCTransport.COBBLESTONE_ITEM.item()) == 2,
                        "the second refused pipe comes back to the chest"))
                .thenExecute(() -> {
                    TestProtection.allow(helper, front);
                    PipeBlockEntity current = (PipeBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(at));
                    reachEnd(helper, (StripesBehaviour) current.behaviour(), at, new ItemStack(BCTransport.COBBLESTONE_ITEM.item()));
                })
                .thenExecuteAfter(1, () -> {
                    require(helper, isPipe(helper, at, BCTransport.COBBLESTONE_ITEM) && isPipe(helper, front, BCTransport.STRIPES_ITEM),
                            "once allowed, the pipe is laid and the stripes pipe moves into the grass, found "
                                    + helper.getBlockState(at) + " and " + helper.getBlockState(front));
                    PipeBlockEntity laid = (PipeBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(at));
                    PipeBlockEntity moved = (PipeBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(front));
                    require(helper, laid.getOwner().getId().equals(OWNER.getId()) && moved.getOwner().getId().equals(OWNER.getId()),
                            "the laid pipe and the moved stripes pipe belong to the owner, found " + laid.getOwner() + " and " + moved.getOwner());
                    CompoundTag saved = moved.saveWithoutMetadata();
                    require(helper, saved.contains("owner") && OWNER.getName().equals(saved.getCompound("owner").getString("Name")),
                            "the owner is saved with the pipe");
                })
                .thenSucceed();
    }

    /**
     * Extending a stripes pipe at the top of the world: the block above is outside the build height, so the pipe
     * refuses before it moves anything, stays where it is and sends the pipe item back. Upstream took the stripes
     * pipe up, could not place it again and lost it with its contents. The pipes are built in this test's own column
     * at the build limit and removed afterwards.
     */
    public static void stripesPipeStaysAtBuildLimit(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos column = helper.absolutePos(new BlockPos(1, 0, 1));
        int top = level.getMaxBuildHeight() - 1;
        BlockPos stripesPos = new BlockPos(column.getX(), top, column.getZ());
        BlockPos pipePos = stripesPos.below();
        BlockPos chestPos = pipePos.below();
        level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(pipePos, BCTransport.STONE_ITEM.block().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(stripesPos, BCTransport.STRIPES_ITEM.block().defaultBlockState(), Block.UPDATE_ALL);
        Runnable cleanUp = () -> {
            for (BlockPos pos : new BlockPos[] {stripesPos, pipePos, chestPos}) {
                level.removeBlockEntity(pos);
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        };

        helper.startSequence()
                .thenExecuteAfter(2, () -> {
                    PipeBlockEntity stripes = (PipeBlockEntity) level.getBlockEntity(stripesPos);
                    StripesBehaviour behaviour = (StripesBehaviour) stripes.behaviour();
                    require(helper, behaviour.direction() == Direction.UP, "the top pipe points up, points " + behaviour.direction());
                    require(helper, level.isOutsideBuildHeight(stripesPos.above()), "the block above the pipe is outside the world");
                    ItemEntity entity = new ItemEntity(level, stripesPos.getX() + 0.5, stripesPos.getY() + 0.5, stripesPos.getZ() + 0.5,
                            new ItemStack(BCTransport.COBBLESTONE_ITEM.item()));
                    behaviour.onDrop(entity);
                    require(helper, entity.getItem().isEmpty(), "the stripes pipe takes the pipe item");
                })
                .thenExecuteAfter(1, () -> {
                    require(helper, level.getBlockEntity(stripesPos) instanceof PipeBlockEntity pipe && pipe.behaviour() instanceof StripesBehaviour,
                            "the stripes pipe stays at the build limit, found " + level.getBlockState(stripesPos));
                    require(helper, level.getBlockState(pipePos).is(BCTransport.STONE_ITEM.block()),
                            "no pipe is laid below the build limit, found " + level.getBlockState(pipePos));
                })
                .thenWaitUntil(() -> require(helper, level.getBlockEntity(chestPos) instanceof Container chest
                        && chest.countItem(BCTransport.COBBLESTONE_ITEM.item()) == 1, "the unused pipe comes back down to the chest"))
                .thenExecute(cleanUp)
                .thenSucceed();
    }
}
