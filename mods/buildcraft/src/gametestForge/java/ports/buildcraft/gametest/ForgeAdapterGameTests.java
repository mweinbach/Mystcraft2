package ports.buildcraft.gametest;

import java.util.ArrayList;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.EnchantmentTableBlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.energy.StirlingEngineBlockEntity;
import ports.buildcraft.core.FragileFluidShardItem;
import ports.buildcraft.energy.BCEnergy;
import ports.buildcraft.factory.BCFactory;
import ports.buildcraft.factory.MiningWellBlockEntity;
import ports.buildcraft.factory.TankBlockEntity;
import ports.buildcraft.lib.FluidAmount;
import ports.buildcraft.lib.Power;
import ports.buildcraft.transport.BCTransport;
import ports.buildcraft.transport.PipeBlockEntity;

/** BuildCraft blocks as other Forge mods see them: Forge Energy, fluid handler and item handler capabilities. */
@GameTestHolder(BuildCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ForgeAdapterGameTests {
    private static final ResourceLocation FOREIGN = new ResourceLocation("buildcraft_gametest", "foreign_storage");

    static {
        // Stand-ins for other mods' blocks, reached only through capabilities: an enchanting table holds Forge Energy
        // and a lectern holds one slot of items.
        @SuppressWarnings({"unchecked", "rawtypes"})
        Class<AttachCapabilitiesEvent<BlockEntity>> type = (Class) AttachCapabilitiesEvent.class;
        MinecraftForge.EVENT_BUS.addGenericListener(BlockEntity.class, EventPriority.NORMAL, false, type, event -> {
            if (event.getObject() instanceof EnchantmentTableBlockEntity) {
                event.addCapability(FOREIGN, provider(ForgeCapabilities.ENERGY, new EnergyStorage(10_000, 10_000, 0)));
            } else if (event.getObject() instanceof LecternBlockEntity) {
                event.addCapability(FOREIGN, provider(ForgeCapabilities.ITEM_HANDLER, new ItemStackHandler(1)));
            }
        });
    }

    private ForgeAdapterGameTests() {}

    private static <T> ICapabilityProvider provider(Capability<T> capability, T instance) {
        LazyOptional<T> optional = LazyOptional.of(() -> instance);
        return new ICapabilityProvider() {
            @Override
            public <C> LazyOptional<C> getCapability(Capability<C> asked, @Nullable Direction side) {
                return asked == capability ? optional.cast() : LazyOptional.empty();
            }
        };
    }

    /** Upstream's providing transactor: a mining well offers pipes an item handler to connect to, but takes no items. */
    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 20)
    public static void providingMachinesRefuseInsertion(GameTestHelper helper) {
        MiningWellBlockEntity well = BCTests.idleMiningWell(helper, new BlockPos(1, 1, 1));
        IItemHandler items = well.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.NORTH).orElseThrow(IllegalStateException::new);
        ItemStack apple = new ItemStack(Items.APPLE);
        BCTests.require(helper, ItemStack.matches(items.insertItem(0, apple.copy(), false), apple), "the mining well must take no items");
        BCTests.require(helper, items.extractItem(0, 64, false).isEmpty(), "the mining well must give no items");
        helper.succeed();
    }

    /** A Stirling engine pushes its power into another mod's Forge Energy storage. */
    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 200)
    public static void enginePushesIntoForeignEnergyStorage(GameTestHelper helper) {
        StirlingEngineBlockEntity engine = BCTests.engine(helper, new BlockPos(1, 1, 1), BCEnergy.STIRLING_ENGINE.get(), Direction.EAST,
                StirlingEngineBlockEntity.class);
        engine.setItem(0, new ItemStack(Items.COAL));
        helper.setBlock(new BlockPos(2, 1, 1), Blocks.ENCHANTING_TABLE);
        helper.setBlock(new BlockPos(0, 1, 1), Blocks.REDSTONE_BLOCK);
        helper.succeedWhen(() -> {
            IEnergyStorage storage = helper.getBlockEntity(new BlockPos(2, 1, 1)).getCapability(ForgeCapabilities.ENERGY, Direction.WEST)
                    .orElseThrow(IllegalStateException::new);
            BCTests.require(helper, storage instanceof EnergyStorage, "the stand-in energy storage must be the one found");
            BCTests.require(helper, storage.getEnergyStored() > 0, "the engine must push energy into the foreign storage");
        });
    }

    /** An item pipe delivers into another mod's item handler. */
    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 400)
    public static void pipeDeliversIntoForeignItemStorage(GameTestHelper helper) {
        BCTests.chest(helper, new BlockPos(0, 1, 1));
        PipeBlockEntity pipe = BCTests.pipe(helper, new BlockPos(1, 1, 1), BCTransport.STONE_ITEM);
        helper.setBlock(new BlockPos(2, 1, 1), Blocks.LECTERN);
        helper.runAfterDelay(2, () -> {
            IItemHandler items = pipe.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.WEST).orElseThrow(IllegalStateException::new);
            BCTests.require(helper, items.insertItem(0, new ItemStack(Items.APPLE, 3), false).isEmpty(), "the pipe must take the apples");
            helper.succeedWhen(() -> {
                IItemHandler foreign = helper.getBlockEntity(new BlockPos(2, 1, 1)).getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.WEST)
                        .orElseThrow(IllegalStateException::new);
                ItemStack held = foreign.getStackInSlot(0);
                BCTests.require(helper, foreign instanceof ItemStackHandler && held.is(Items.APPLE) && held.getCount() == 3,
                        "the apples must reach the foreign item handler, found " + held);
            });
        });
    }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 20)
    public static void forgeEnergyCapability(GameTestHelper helper) {
        MiningWellBlockEntity well = BCTests.idleMiningWell(helper, new BlockPos(1, 1, 1));
        IEnergyStorage energy = well.getCapability(ForgeCapabilities.ENERGY, Direction.NORTH).orElseThrow(IllegalStateException::new);
        BCTests.require(helper, energy.canReceive() && !energy.canExtract(), "machines only receive Forge Energy");
        BCTests.require(helper, energy.receiveEnergy(250, true) == 250 && well.battery().stored() == 0, "simulated FE must not be stored");
        BCTests.require(helper, energy.receiveEnergy(250, false) == 250, "the mining well must accept 250 FE");
        BCTests.require(helper, well.battery().stored() == 25 * Power.MJ, "250 FE must store 25 MJ, stored " + well.battery().stored());
        BCTests.require(helper, energy.getEnergyStored() == 250 && energy.getMaxEnergyStored() == 5_000, "FE readings must use 10 FE per MJ");
        helper.succeed();
    }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 20)
    public static void forgeFluidHandler(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 1, 1), BCFactory.TANK.get());
        TankBlockEntity tank = BCTests.tile(helper, new BlockPos(1, 1, 1), TankBlockEntity.class);
        IFluidHandler fluids = tank.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.UP).orElseThrow(IllegalStateException::new);
        BCTests.require(helper, fluids.fill(new FluidStack(Fluids.WATER, 3000), IFluidHandler.FluidAction.EXECUTE) == 3000, "the tank must take 3000 mB");
        BCTests.require(helper, fluids.fill(new FluidStack(Fluids.LAVA, 1000), IFluidHandler.FluidAction.SIMULATE) == 0, "the tank must not mix fluids");
        FluidStack drained = fluids.drain(1000, IFluidHandler.FluidAction.EXECUTE);
        BCTests.require(helper, drained.getFluid() == Fluids.WATER && drained.getAmount() == 1000, "draining must return water");
        BCTests.require(helper, tank.tank().amount() == 2000, "the tank must keep 2000 mB, has " + tank.tank().amount());
        helper.succeed();
    }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 20)
    public static void forgeShardFluidHandler(GameTestHelper helper) {
        Fluid oil = BCEnergy.OIL.heat(0).source();
        ItemStack shard = FragileFluidShardItem.of(new FluidAmount(oil, 300));
        IFluidHandlerItem fluids = shard.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElseThrow(IllegalStateException::new);
        BCTests.require(helper, fluids.getFluidInTank(0).getAmount() == 300 && fluids.getTankCapacity(0) == 500, "the shard must report 300 / 500 mB");
        BCTests.require(helper, fluids.fill(new FluidStack(oil, 100), IFluidHandler.FluidAction.EXECUTE) == 0, "a shard cannot be filled");
        BCTests.require(helper, fluids.drain(new FluidStack(Fluids.WATER, 100), IFluidHandler.FluidAction.EXECUTE).isEmpty(), "a shard holds only its fluid");
        BCTests.require(helper, fluids.drain(100, IFluidHandler.FluidAction.SIMULATE).getAmount() == 100
                && FragileFluidShardItem.getFluid(fluids.getContainer()).amount() == 300, "a simulated drain must not empty the shard");
        BCTests.require(helper, fluids.drain(100, IFluidHandler.FluidAction.EXECUTE).getAmount() == 100
                && FragileFluidShardItem.getFluid(fluids.getContainer()).amount() == 200, "draining 100 mB must leave 200 mB");
        FluidStack rest = fluids.drain(1000, IFluidHandler.FluidAction.EXECUTE);
        BCTests.require(helper, rest.getFluid() == oil && rest.getAmount() == 200 && fluids.getContainer().isEmpty(), "an emptied shard must be used up");
        helper.succeed();
    }

    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 400)
    public static void forgeItemHandler(GameTestHelper helper) {
        BCTests.chest(helper, new BlockPos(0, 1, 1));
        PipeBlockEntity pipe = BCTests.pipe(helper, new BlockPos(1, 1, 1), BCTransport.STONE_ITEM);
        ChestBlockEntity chest = BCTests.chest(helper, new BlockPos(2, 1, 1));
        helper.runAfterDelay(2, () -> {
            IItemHandler items = pipe.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.WEST).orElseThrow(IllegalStateException::new);
            BCTests.require(helper, items.insertItem(0, new ItemStack(Items.APPLE, 2), true).isEmpty(), "a pipe accepts items on connected faces");
            BCTests.require(helper, BCTests.count(chest, Items.APPLE) == 0, "simulated insertion must not move items");
            BCTests.require(helper, items.insertItem(0, new ItemStack(Items.APPLE, 2), false).isEmpty(), "the pipe must take the apples");
            helper.succeedWhen(() -> BCTests.require(helper, BCTests.count(chest, Items.APPLE) == 2, "the apples must reach the chest"));
        });
    }

    /** A plug invalidates capabilities callers kept for a pipe face; removing it offers the face again. */
    @GameTest(template = BCTests.TEMPLATE, timeoutTicks = 20)
    public static void keptCapabilitiesFollowPlugs(GameTestHelper helper) {
        BCTests.chest(helper, new BlockPos(0, 1, 1));
        PipeBlockEntity pipe = BCTests.pipe(helper, new BlockPos(1, 1, 1), BCTransport.STONE_ITEM);
        BCTests.chest(helper, new BlockPos(2, 1, 1));
        helper.runAfterDelay(2, () -> {
            LazyOptional<IItemHandler> kept = pipe.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.WEST);
            BCTests.require(helper, kept.isPresent(), "the pipe must offer an item handler on a connected face");
            pipe.replacePluggable(Direction.WEST, BCTransport.BLOCKER.create(pipe, Direction.WEST));
            BCTests.require(helper, !kept.isPresent(), "a plug must invalidate the kept item handler");
            BCTests.require(helper, !pipe.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.WEST).isPresent(), "a plugged face offers no item handler");
            pipe.removePluggable(Direction.WEST, new ArrayList<>());
            IItemHandler reopened = pipe.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.WEST).orElseThrow(IllegalStateException::new);
            BCTests.require(helper, reopened.insertItem(0, new ItemStack(Items.APPLE), true).isEmpty(), "the face must take items once the plug is gone");
            helper.succeed();
        });
    }
}
