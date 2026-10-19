package ports.buildcraft.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.material.Fluids;
import ports.buildcraft.factory.BCFactory;
import ports.buildcraft.factory.MiningWellBlockEntity;
import ports.buildcraft.factory.TankBlockEntity;
import ports.buildcraft.lib.Power;
import ports.buildcraft.transport.BCTransport;
import team.reborn.energy.api.EnergyStorage;

/** BuildCraft blocks as other Fabric mods see them: Team Reborn Energy and Transfer API storages. */
public final class FabricAdapterGameTests implements FabricGameTest {
    private static final long DROPLETS_PER_MB = 81;

    @GameTest(template = FabricGameTests.TEMPLATE, timeoutTicks = 20)
    public void teamRebornEnergyStorage(GameTestHelper helper) {
        MiningWellBlockEntity well = BCTests.idleMiningWell(helper, new BlockPos(1, 1, 1));
        EnergyStorage energy = EnergyStorage.SIDED.find(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)), Direction.NORTH);
        BCTests.require(helper, energy != null, "the mining well must expose a Team Reborn energy storage");
        BCTests.require(helper, energy.supportsInsertion() && !energy.supportsExtraction(), "machines only receive energy");
        try (Transaction transaction = Transaction.openOuter()) {
            BCTests.require(helper, energy.insert(250, transaction) == 250, "the mining well must accept 250 E");
        }
        BCTests.require(helper, well.battery().stored() == 0, "an aborted transaction must not store energy");
        try (Transaction transaction = Transaction.openOuter()) {
            BCTests.require(helper, energy.insert(250, transaction) == 250, "the mining well must accept 250 E");
            transaction.commit();
        }
        BCTests.require(helper, well.battery().stored() == 25 * Power.MJ, "250 E must store 25 MJ, stored " + well.battery().stored());
        BCTests.require(helper, energy.getAmount() == 250 && energy.getCapacity() == 5_000, "energy readings must use 10 E per MJ");
        helper.succeed();
    }

    @GameTest(template = FabricGameTests.TEMPLATE, timeoutTicks = 20)
    public void transferApiFluidStorage(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 1, 1), BCFactory.TANK.get());
        TankBlockEntity tank = BCTests.tile(helper, new BlockPos(1, 1, 1), TankBlockEntity.class);
        Storage<FluidVariant> fluids = FluidStorage.SIDED.find(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)), Direction.UP);
        BCTests.require(helper, fluids != null, "the tank must expose a fluid storage");
        FluidVariant water = FluidVariant.of(Fluids.WATER);
        try (Transaction transaction = Transaction.openOuter()) {
            BCTests.require(helper, fluids.insert(water, 3000 * DROPLETS_PER_MB, transaction) == 3000 * DROPLETS_PER_MB, "the tank must take 3000 mB");
            BCTests.require(helper, fluids.insert(FluidVariant.of(Fluids.LAVA), DROPLETS_PER_MB * 1000, transaction) == 0, "the tank must not mix fluids");
            BCTests.require(helper, fluids.insert(water, DROPLETS_PER_MB - 1, transaction) == 0, "partial millibuckets must be refused");
            transaction.commit();
        }
        try (Transaction transaction = Transaction.openOuter()) {
            BCTests.require(helper, fluids.extract(water, 500 * DROPLETS_PER_MB, transaction) == 500 * DROPLETS_PER_MB, "draining must return water");
        }
        BCTests.require(helper, tank.tank().amount() == 3000, "an aborted drain must roll back, has " + tank.tank().amount());
        try (Transaction transaction = Transaction.openOuter()) {
            BCTests.require(helper, fluids.extract(water, 1000 * DROPLETS_PER_MB, transaction) == 1000 * DROPLETS_PER_MB, "draining must return water");
            transaction.commit();
        }
        BCTests.require(helper, tank.tank().amount() == 2000, "the tank must keep 2000 mB, has " + tank.tank().amount());
        helper.succeed();
    }

    @GameTest(template = FabricGameTests.TEMPLATE, timeoutTicks = 400)
    public void transferApiItemStorage(GameTestHelper helper) {
        BCTests.chest(helper, new BlockPos(0, 1, 1));
        BCTests.pipe(helper, new BlockPos(1, 1, 1), BCTransport.STONE_ITEM);
        ChestBlockEntity chest = BCTests.chest(helper, new BlockPos(2, 1, 1));
        helper.runAfterDelay(2, () -> {
            Storage<ItemVariant> items = ItemStorage.SIDED.find(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)), Direction.WEST);
            BCTests.require(helper, items != null, "the pipe must expose an item storage");
            ItemVariant apple = ItemVariant.of(Items.APPLE);
            try (Transaction transaction = Transaction.openOuter()) {
                BCTests.require(helper, items.insert(apple, 2, transaction) == 2, "a pipe accepts items on connected faces");
            }
            try (Transaction transaction = Transaction.openOuter()) {
                BCTests.require(helper, items.insert(apple, 2, transaction) == 2, "the pipe must take the apples");
                transaction.commit();
            }
            helper.succeedWhen(() -> BCTests.require(helper, BCTests.count(chest, Items.APPLE) == 2,
                    "exactly the committed apples must reach the chest, found " + BCTests.count(chest, Items.APPLE)));
        });
    }
}
