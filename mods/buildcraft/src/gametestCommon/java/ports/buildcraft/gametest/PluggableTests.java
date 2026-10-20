package ports.buildcraft.gametest;

import static ports.buildcraft.gametest.BCTests.chest;
import static ports.buildcraft.gametest.BCTests.count;
import static ports.buildcraft.gametest.BCTests.engine;
import static ports.buildcraft.gametest.BCTests.pipe;
import static ports.buildcraft.gametest.BCTests.require;
import static ports.buildcraft.gametest.BuilderTests.dropped;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import ports.buildcraft.energy.BCEnergy;
import ports.buildcraft.energy.EngineBlockEntity;
import ports.buildcraft.platform.EnergyPort;
import ports.buildcraft.transport.BCTransport;
import ports.buildcraft.transport.PipeBlock;
import ports.buildcraft.transport.PipeBlockEntity;
import ports.buildcraft.transport.pluggable.BlockerPluggable;
import ports.buildcraft.transport.pluggable.PowerAdaptorPluggable;

/** Loader-neutral GameTest bodies for pipe pluggables and structure pipes. */
public final class PluggableTests {
    private static final double EYE_HEIGHT = 1.62;

    private PluggableTests() {}

    private static boolean connected(GameTestHelper helper, BlockPos pos, Direction face) {
        return helper.getBlockState(pos).getValue(PipeBlock.SIDES.get(face)).isConnected();
    }

    /** Right-clicks the middle of a pipe block's {@code face} holding {@code held}, as {@code use} would. */
    private static InteractionResult useOn(GameTestHelper helper, Player player, BlockPos pos, Direction face, ItemStack held) {
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        BlockPos absolute = helper.absolutePos(pos);
        Vec3 location = Vec3.atCenterOf(absolute).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        BlockHitResult hit = new BlockHitResult(location, face, absolute, false);
        return helper.getBlockState(pos).use(helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
    }

    /**
     * Stands {@code player} three blocks east of {@code pos}, looking west at the middle of its block. Picks follow
     * the head, which a ticking player turns with its body; these players never tick.
     */
    private static void lookWestAt(GameTestHelper helper, Player player, BlockPos pos) {
        Vec3 centre = Vec3.atCenterOf(helper.absolutePos(pos));
        player.moveTo(centre.x + 3, centre.y - EYE_HEIGHT, centre.z, 90, 0);
        player.setYHeadRot(90);
    }

    /**
     * A pipe plug placed by right-clicking a connected face stops both pipes connecting through it, hides the
     * pipe's item port there and adds its box to the pipe's shape; clients get it and saving keeps it. Breaking
     * the plug while looking at it takes off only the plug, which drops, through the shared hook and through the
     * server's game mode; breaking the pipe itself in creative takes its plugs without drops. A broken pipe
     * drops its plugs.
     */
    public static void plugBlocksConnectionsAndBreaksOff(GameTestHelper helper) {
        BlockPos a = new BlockPos(2, 2, 2), b = new BlockPos(3, 2, 2), dropper = new BlockPos(5, 2, 6);
        PipeBlockEntity pipe = pipe(helper, a, BCTransport.STONE_ITEM);
        pipe(helper, b, BCTransport.STONE_ITEM);
        Player player = helper.makeMockPlayer();
        BlockPos absolute = helper.absolutePos(a);

        helper.runAfterDelay(2, () -> {
            require(helper, connected(helper, a, Direction.EAST) && connected(helper, b, Direction.WEST), "two stone pipes connect");
            require(helper, pipe.itemPort(Direction.EAST) != null, "an item pipe takes items on a connected face");
            ItemStack plugs = new ItemStack(BCTransport.PLUG_BLOCKER.get(), 2);
            require(helper, useOn(helper, player, a, Direction.EAST, plugs) == InteractionResult.CONSUME,
                    "right-clicking a face with a pipe plug places it");
            require(helper, plugs.getCount() == 1 && pipe.getPluggable(Direction.EAST) instanceof BlockerPluggable && pipe.isBlocked(Direction.EAST),
                    "placing a plug uses one item");
        });
        helper.runAfterDelay(4, () -> {
            require(helper, !connected(helper, a, Direction.EAST) && !connected(helper, b, Direction.WEST),
                    "a plug stops both pipes connecting through it");
            require(helper, pipe.itemPort(Direction.EAST) == null && pipe.itemPort(Direction.WEST) != null, "a plug hides the pipe's port on its face only");
            VoxelShape shape = helper.getBlockState(a).getShape(helper.getLevel(), absolute);
            require(helper, Math.abs(shape.max(Direction.Axis.X) - 14 / 16.0) < 1e-6 && Math.abs(shape.min(Direction.Axis.X) - 4 / 16.0) < 1e-6,
                    "the plug's box, 2 to 4 pixels in from the east face, joins the pipe's shape");
            require(helper, PipeBlock.pluggableAt(pipe, Vec3.atLowerCornerOf(absolute).add(13 / 16.0, 0.5, 0.5)) == Direction.EAST
                    && PipeBlock.pluggableAt(pipe, Vec3.atCenterOf(absolute)) == null, "a hit on the plug's box picks the plug, a hit on the pipe does not");
            CompoundTag update = pipe.getUpdateTag().getCompound("plugs");
            require(helper, update.getCompound("east").getString("id").equals("buildcraft:blocker") && update.size() == 1,
                    "clients get the plug by face, got " + update);
            PipeBlockEntity loaded = new PipeBlockEntity(absolute, helper.getBlockState(a));
            loaded.load(pipe.saveWithoutMetadata());
            require(helper, loaded.getPluggable(Direction.EAST) instanceof BlockerPluggable && loaded.isBlocked(Direction.EAST)
                    && loaded.getPluggable(Direction.WEST) == null, "saving keeps the plug");

            helper.setBlock(b, Blocks.AIR);
            lookWestAt(helper, player, a);
            require(helper, PipeBlock.onPlayerDestroy(helper.getLevel(), absolute, player), "breaking while looking at the plug hits the plug");
            require(helper, pipe.getPluggable(Direction.EAST) == null && helper.getBlockState(a).getBlock() instanceof PipeBlock
                    && dropped(helper, a, new ItemStack(BCTransport.PLUG_BLOCKER.get())), "breaking the plug drops it and keeps the pipe");
            require(helper, !PipeBlock.onPlayerDestroy(helper.getLevel(), absolute, player), "without a plug in sight the pipe itself breaks");

            pipe.replacePluggable(Direction.EAST, BCTransport.BLOCKER.create(pipe, Direction.EAST));
            ServerPlayer serverPlayer = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "plugs"), null);
            lookWestAt(helper, serverPlayer, a);
            require(helper, !serverPlayer.gameMode.destroyBlock(absolute) && helper.getBlockState(a).getBlock() instanceof PipeBlock
                    && pipe.getPluggable(Direction.EAST) == null && dropped(helper, a, new ItemStack(BCTransport.PLUG_BLOCKER.get(), 2)),
                    "the server's block breaking takes off only the plug");

            pipe.replacePluggable(Direction.NORTH, BCTransport.BLOCKER.create(pipe, Direction.NORTH));
            serverPlayer.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
            require(helper, serverPlayer.gameMode.destroyBlock(absolute) && helper.getBlockState(a).isAir(), "breaking the pipe itself in creative removes it");
            require(helper, dropped(helper, a, new ItemStack(BCTransport.PLUG_BLOCKER.get(), 2)), "a creative break drops no plugs");

            PipeBlockEntity other = pipe(helper, dropper, BCTransport.STONE_ITEM);
            other.replacePluggable(Direction.UP, BCTransport.BLOCKER.create(other, Direction.UP));
            helper.setBlock(dropper, Blocks.AIR);
            require(helper, dropped(helper, dropper, new ItemStack(BCTransport.PLUG_BLOCKER.get())), "a broken pipe drops its plugs");
            helper.succeed();
        });
    }

    /**
     * A plug on a wooden pipe's top keeps a redstone engine above from powering it. A power adapter in its place
     * blocks the face just the same but hands the engine's power to the pipe, which pulls cobblestone out of a
     * chest. The adapter only goes on faces where the pipe's behaviour takes redstone engine power.
     */
    public static void powerAdaptorDrivesWoodenPipeThroughBlockedFace(GameTestHelper helper) {
        BlockPos woodPos = new BlockPos(1, 2, 1), stonePos = new BlockPos(2, 2, 1);
        ChestBlockEntity source = chest(helper, new BlockPos(0, 2, 1), new ItemStack(Items.COBBLESTONE, 8));
        PipeBlockEntity wood = pipe(helper, woodPos, BCTransport.WOOD_ITEM);
        pipe(helper, stonePos, BCTransport.STONE_ITEM);
        pipe(helper, new BlockPos(3, 2, 1), BCTransport.GOLD_ITEM);
        ChestBlockEntity target = chest(helper, new BlockPos(4, 2, 1));
        wood.replacePluggable(Direction.UP, BCTransport.BLOCKER.create(wood, Direction.UP));
        EngineBlockEntity engine = engine(helper, woodPos.above(), BCEnergy.REDSTONE_ENGINE.get(), Direction.DOWN, EngineBlockEntity.class);
        helper.setBlock(woodPos.above(2), Blocks.REDSTONE_BLOCK);
        Player player = helper.makeMockPlayer();

        ItemStack adaptor = new ItemStack(BCTransport.PLUG_POWER_ADAPTOR.get());
        require(helper, useOn(helper, player, stonePos, Direction.UP, adaptor) == InteractionResult.PASS && adaptor.getCount() == 1,
                "a power adapter does not go on a stone pipe");

        helper.runAfterDelay(100, () -> {
            require(helper, wood.energyPort(Direction.UP) == null && count(source, Items.COBBLESTONE) == 8 && !engine.isPumping(),
                    "a plug keeps the engine from powering the pipe");
            wood.removePluggable(Direction.UP, new ArrayList<>());
            require(helper, useOn(helper, player, woodPos, Direction.UP, adaptor) == InteractionResult.CONSUME && adaptor.isEmpty(),
                    "a power adapter goes on a wooden pipe");
            EnergyPort port = wood.energyPort(Direction.UP);
            require(helper, wood.getPluggable(Direction.UP) instanceof PowerAdaptorPluggable && wood.isBlocked(Direction.UP)
                    && port != null && port.acceptsRedstoneEngine(), "the adapter hands the face's power to the wooden pipe");
            helper.succeedWhen(() -> {
                require(helper, !connected(helper, woodPos, Direction.UP), "the adapter blocks the face");
                require(helper, count(target, Items.COBBLESTONE) >= 1, "cobblestone must reach the far chest; engine pumping " + engine.isPumping());
                require(helper, count(source, Items.COBBLESTONE) + count(target, Items.COBBLESTONE) <= 8, "items must not duplicate");
            });
        });
    }

    /** Structure pipes connect to each other and to nothing else, and offer no ports. */
    public static void structurePipesConnectOnlyToEachOther(GameTestHelper helper) {
        BlockPos first = new BlockPos(2, 2, 2), second = new BlockPos(3, 2, 2), stone = new BlockPos(2, 2, 3);
        PipeBlockEntity structure = pipe(helper, first, BCTransport.STRUCTURE);
        pipe(helper, second, BCTransport.STRUCTURE);
        pipe(helper, stone, BCTransport.STONE_ITEM);
        chest(helper, new BlockPos(1, 2, 2));

        helper.runAfterDelay(2, () -> {
            require(helper, connected(helper, first, Direction.EAST) && connected(helper, second, Direction.WEST), "structure pipes connect to each other");
            require(helper, !connected(helper, first, Direction.SOUTH) && !connected(helper, stone, Direction.NORTH),
                    "a structure pipe and a stone pipe do not connect");
            require(helper, !connected(helper, first, Direction.WEST), "a structure pipe does not connect to a chest");
            for (Direction face : Direction.values()) {
                require(helper, structure.itemPort(face) == null && structure.fluidPort(face) == null && structure.energyPort(face) == null,
                        "a structure pipe has no ports on " + face);
            }
            helper.succeed();
        });
    }
}
