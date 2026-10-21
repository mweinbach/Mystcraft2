package ports.buildcraft.gametest;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.lib.Power;
import ports.buildcraft.silicon.BCSilicon;
import ports.buildcraft.silicon.plug.FacadeAssemblyRecipe;
import ports.buildcraft.silicon.plug.FacadeBlockStateInfo;
import ports.buildcraft.silicon.plug.FacadeInstance;
import ports.buildcraft.silicon.plug.FacadeItem;
import ports.buildcraft.silicon.plug.FacadeStateManager;
import ports.buildcraft.silicon.plug.PluggableFacade;
import ports.buildcraft.transport.BCTransport;
import ports.buildcraft.transport.PipeBlock;
import ports.buildcraft.transport.PipeBlockEntity;

/** Facades: which blocks copy, assembly and the hollow swap, and full versus hollow on a pipe. */
public final class FacadeTests {
    private FacadeTests() {}

    /**
     * Stone, glass and an oak log copy; a chest, slab, water and a moving piston do not. An unextended piston
     * copies, because it is a full model cube with no block entity. Glass is allowed though it does not occlude,
     * and a log's axis is a varying property. A block added to the blacklist stops being a facade.
     */
    public static void facadeRules(GameTestHelper helper) {
        FacadeStateManager.refresh();
        requireState(helper, Blocks.STONE.defaultBlockState(), true, false);
        requireState(helper, Blocks.GLASS.defaultBlockState(), true, true);
        FacadeBlockStateInfo log = FacadeStateManager.info(Blocks.OAK_LOG.defaultBlockState());
        BCTests.require(helper, log.visible && log.varyingProperties.contains(BlockStateProperties.AXIS),
                "an oak log copies, and its axis is listed because every log shares one item");
        BCTests.require(helper, !FacadeStateManager.isValid(Blocks.CHEST.defaultBlockState()), "a chest is not a facade");
        BCTests.require(helper, !FacadeStateManager.isValid(Blocks.OAK_SLAB.defaultBlockState()), "a slab is not a facade");
        BCTests.require(helper, !FacadeStateManager.isValid(Blocks.WATER.defaultBlockState()), "water is not a facade");
        BCTests.require(helper, !FacadeStateManager.isValid(Blocks.MOVING_PISTON.defaultBlockState()), "a moving piston is not a facade");
        FacadeStateManager.rescan(Set.of(Blocks.STONE));
        try {
            BCTests.require(helper, !FacadeStateManager.isValid(Blocks.STONE.defaultBlockState())
                    && FacadeStateManager.isValid(Blocks.GLASS.defaultBlockState()), "the blacklist drops stone and keeps glass");
        } finally {
            FacadeStateManager.clearRescan();
        }
        BCTests.require(helper, FacadeStateManager.isValid(Blocks.STONE.defaultBlockState()), "clearing the test blacklist restores stone");
        helper.succeed();
    }

    /**
     * Three structure pipes and a block make six full and six hollow facades for 64 MJ. One facade in a grid
     * comes back hollow; two items do not match.
     */
    public static void assemblyAndSwap(GameTestHelper helper) {
        var recipe = helper.getLevel().getRecipeManager().byKey(BuildCraft.id("assembly/facade")).orElse(null);
        BCTests.require(helper, recipe instanceof FacadeAssemblyRecipe, "the facade assembly recipe loads");
        FacadeAssemblyRecipe assembly = (FacadeAssemblyRecipe) recipe;
        ItemStack pipes = new ItemStack(BCTransport.STRUCTURE.item(), 3);
        ItemStack stone = new ItemStack(Blocks.STONE);
        var outputs = assembly.getOutputs(List.of(pipes, stone));
        FacadeBlockStateInfo info = FacadeStateManager.info(Blocks.STONE.defaultBlockState());
        ItemStack full = FacadeItem.create(info, false);
        ItemStack hollow = FacadeItem.create(info, true);
        BCTests.require(helper, outputs.size() == 2 && ItemStack.matches(outputs.get(0), full) && ItemStack.matches(outputs.get(1), hollow),
                "three pipes and stone make six full and six hollow stone facades");
        BCTests.require(helper, assembly.getRequiredMicroJoulesFor(full) == 64 * Power.MJ, "a facade costs 64 MJ");
        BCTests.require(helper, assembly.getOutputs(List.of(stone)).isEmpty(), "stone alone makes nothing");
        BCTests.require(helper, assembly.getOutputs(List.of(pipes, new ItemStack(Blocks.CHEST))).isEmpty(), "a chest makes no facade");
        var inputs = assembly.getInputsFor(full);
        BCTests.require(helper, inputs.size() == 2 && inputs.get(0).count() == 3 && inputs.get(0).ingredient().test(pipes)
                && inputs.get(1).count() == 1 && inputs.get(1).ingredient().test(stone), "the inputs are three structure pipes and the block");
        BCTests.require(helper, ItemStack.matches(assembly.produced(full), full) && assembly.produced(new ItemStack(Blocks.STONE)).isEmpty(),
                "a saved facade output is still made, and a plain block is not");

        CraftingRecipe swap = (CraftingRecipe) helper.getLevel().getRecipeManager().byKey(BuildCraft.id("facade_swap")).orElseThrow();
        CraftingContainer grid = grid();
        ItemStack one = full.copy();
        one.setCount(1);
        grid.setItem(0, one);
        ItemStack flipped = swap.assemble(grid);
        BCTests.require(helper, ItemStack.isSameItemSameTags(flipped, hollow) && flipped.getCount() == 1 && swap.matches(grid, helper.getLevel()),
                "one facade in a grid flips hollow");
        grid.setItem(1, one.copy());
        BCTests.require(helper, swap.assemble(grid).isEmpty(), "two items do not flip a facade");
        helper.succeed();
    }

    /**
     * A full facade stops both pipes connecting, grows the shape by a two-pixel slab, holds a torch and drops itself.
     * A hollow one leaves the pipes connected.
     */
    public static void fullFacadeBlocksAndHollowStaysOpen(GameTestHelper helper) {
        BlockPos a = new BlockPos(2, 2, 2), b = new BlockPos(3, 2, 2);
        BlockPos c = new BlockPos(2, 2, 4), d = new BlockPos(3, 2, 4);
        PipeBlockEntity pipe = BCTests.pipe(helper, a, BCTransport.STONE_ITEM);
        BCTests.pipe(helper, b, BCTransport.STONE_ITEM);
        PipeBlockEntity hollowPipe = BCTests.pipe(helper, c, BCTransport.STONE_ITEM);
        BCTests.pipe(helper, d, BCTransport.STONE_ITEM);
        Player player = helper.makeMockPlayer();
        BlockPos absolute = helper.absolutePos(a);

        helper.runAfterDelay(2, () -> {
            BCTests.require(helper, PipeBlock.isConnected(helper.getBlockState(a), Direction.EAST), "the pipes connect before a facade");
            ItemStack facades = FacadeItem.create(FacadeStateManager.info(Blocks.STONE.defaultBlockState()), false);
            BCTests.require(helper, BCTests.useOn(helper, player, a, Direction.EAST, facades) == InteractionResult.CONSUME,
                    "using a facade on a face places it");
            BCTests.require(helper, facades.getCount() == 5 && pipe.getPluggable(Direction.EAST) instanceof PluggableFacade placed
                    && !placed.isHollow() && placed.isBlocking(), "placing a full facade uses one and blocks the face");
            ItemStack hollows = FacadeItem.create(FacadeStateManager.info(Blocks.GLASS.defaultBlockState()), true);
            BCTests.require(helper, BCTests.useOn(helper, player, c, Direction.EAST, hollows) == InteractionResult.CONSUME
                    && hollowPipe.getPluggable(Direction.EAST) instanceof PluggableFacade placed && placed.isHollow() && !placed.isBlocking(),
                    "a hollow glass facade places without blocking");
        });
        helper.runAfterDelay(4, () -> {
            BCTests.require(helper, !PipeBlock.isConnected(helper.getBlockState(a), Direction.EAST)
                    && !PipeBlock.isConnected(helper.getBlockState(b), Direction.WEST), "a full facade stops both pipes connecting");
            BCTests.require(helper, PipeBlock.isConnected(helper.getBlockState(c), Direction.EAST)
                    && PipeBlock.isConnected(helper.getBlockState(d), Direction.WEST), "a hollow facade leaves the pipes connected");
            VoxelShape shape = helper.getBlockState(a).getShape(helper.getLevel(), absolute);
            boolean slab = shape.toAabbs().stream().anyMatch(box -> box.maxX > 15 / 16.0 && box.minX >= 14 / 16.0 - 1e-4
                    && box.minY < 1e-4 && box.maxY > 1 - 1e-4 && box.minZ < 1e-4 && box.maxZ > 1 - 1e-4);
            BCTests.require(helper, slab && Math.abs(shape.max(Direction.Axis.X) - 1) < 1e-6, "the facade adds a two-pixel slab across the east face");
            BCTests.require(helper, helper.getBlockState(a).isFaceSturdy(helper.getLevel(), absolute, Direction.EAST),
                    "a stone facade holds a torch on its face");
            CompoundTag update = pipe.getUpdateTag().getCompound("plugs");
            CompoundTag facade = update.getCompound("east").getCompound("data").getCompound("facade");
            BCTests.require(helper, update.getCompound("east").getString("id").equals("buildcraft:facade")
                    && !facade.getBoolean("isHollow")
                    && facade.getList("states", Tag.TAG_COMPOUND).getCompound(0).getCompound("state").getString("Name").equals("minecraft:stone"),
                    "clients get the full stone facade");
            PipeBlockEntity loaded = new PipeBlockEntity(absolute, helper.getBlockState(a));
            loaded.load(pipe.saveWithoutMetadata());
            BCTests.require(helper, loaded.getPluggable(Direction.EAST) instanceof PluggableFacade saved
                    && saved.states().phasedStates[0].info.state.is(Blocks.STONE) && !saved.isHollow(), "saving keeps the stone facade");

            ItemStack drop = FacadeItem.create(FacadeInstance.single(FacadeStateManager.info(Blocks.STONE.defaultBlockState()), false));
            helper.setBlock(b, Blocks.AIR);
            lookWest(helper, player, a);
            BCTests.require(helper, PipeBlock.onPlayerDestroy(helper.getLevel(), absolute, player)
                    && pipe.getPluggable(Direction.EAST) == null && helper.getBlockState(a).getBlock() instanceof PipeBlock
                    && BuilderTests.dropped(helper, a, drop), "breaking the facade drops it and keeps the pipe");

            ServerPlayer serverPlayer = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "facades"), null);
            hollowPipe.replacePluggable(Direction.EAST, new PluggableFacade(BCSilicon.FACADE, hollowPipe, Direction.EAST,
                    FacadeInstance.single(FacadeStateManager.info(Blocks.GLASS.defaultBlockState()), true)));
            helper.setBlock(d, Blocks.AIR);
            lookWest(helper, serverPlayer, c);
            serverPlayer.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            BlockPos hollowAbsolute = helper.absolutePos(c);
            ItemStack glassDrop = FacadeItem.create(FacadeInstance.single(FacadeStateManager.info(Blocks.GLASS.defaultBlockState()), true));
            BCTests.require(helper, !serverPlayer.gameMode.destroyBlock(hollowAbsolute)
                    && helper.getBlockState(c).getBlock() instanceof PipeBlock
                    && hollowPipe.getPluggable(Direction.EAST) == null
                    && BuilderTests.dropped(helper, c, glassDrop), "the server's block breaking takes off only the hollow facade");
            helper.succeed();
        });
    }

    private static void requireState(GameTestHelper helper, BlockState state, boolean valid, boolean transparent) {
        FacadeBlockStateInfo info = FacadeStateManager.info(state);
        BCTests.require(helper, FacadeStateManager.isValid(state) == valid && info.visible == valid && info.transparent == transparent,
                state.getBlock().getName().getString() + " validity");
    }

    private static CraftingContainer grid() {
        AbstractContainerMenu menu = new AbstractContainerMenu(null, -1) {
            @Override public boolean stillValid(Player player) { return true; }
            @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
        };
        return new CraftingContainer(menu, 3, 3);
    }

    private static void lookWest(GameTestHelper helper, Player player, BlockPos pos) {
        Vec3 centre = Vec3.atCenterOf(helper.absolutePos(pos));
        player.moveTo(centre.x + 3, centre.y - 1.62, centre.z, 90, 0);
        player.setYHeadRot(90);
    }
}
