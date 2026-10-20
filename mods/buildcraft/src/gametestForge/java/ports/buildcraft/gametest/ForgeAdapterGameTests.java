package ports.buildcraft.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import ports.buildcraft.BuildCraft;
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
    private ForgeAdapterGameTests() {}

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
}
