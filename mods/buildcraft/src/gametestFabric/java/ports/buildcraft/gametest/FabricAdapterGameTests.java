package ports.buildcraft.gametest;

import java.util.ArrayList;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.lookup.v1.block.BlockApiCache;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
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
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import ports.buildcraft.core.FragileFluidShardItem;
import ports.buildcraft.energy.BCEnergy;
import ports.buildcraft.factory.BCFactory;
import ports.buildcraft.factory.MiningWellBlockEntity;
import ports.buildcraft.factory.TankBlockEntity;
import ports.buildcraft.lib.FluidAmount;
import ports.buildcraft.lib.Power;
import ports.buildcraft.transport.BCTransport;
import ports.buildcraft.transport.PipeBlockEntity;
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

    @GameTest(template = FabricGameTests.TEMPLATE, timeoutTicks = 20)
    public void transferApiShardStorage(GameTestHelper helper) {
        Fluid oil = BCEnergy.OIL.heat(0).source();
        ContainerItemContext context = ContainerItemContext.withInitial(FragileFluidShardItem.of(new FluidAmount(oil, 300)));
        Storage<FluidVariant> fluids = context.find(FluidStorage.ITEM);
        BCTests.require(helper, fluids != null, "the shard must expose a fluid storage");
        FluidVariant variant = FluidVariant.of(oil);
        try (Transaction transaction = Transaction.openOuter()) {
            BCTests.require(helper, fluids.insert(variant, 100 * DROPLETS_PER_MB, transaction) == 0, "a shard cannot be filled");
            BCTests.require(helper, fluids.extract(FluidVariant.of(Fluids.WATER), 100 * DROPLETS_PER_MB, transaction) == 0, "a shard holds only its fluid");
            BCTests.require(helper, fluids.extract(variant, 100 * DROPLETS_PER_MB, transaction) == 100 * DROPLETS_PER_MB, "draining must return oil");
        }
        BCTests.require(helper, FragileFluidShardItem.getFluid(context.getItemVariant().toStack()).amount() == 300, "an aborted drain must roll back");
        try (Transaction transaction = Transaction.openOuter()) {
            BCTests.require(helper, fluids.extract(variant, 100 * DROPLETS_PER_MB, transaction) == 100 * DROPLETS_PER_MB, "draining must return oil");
            transaction.commit();
        }
        BCTests.require(helper, FragileFluidShardItem.getFluid(context.getItemVariant().toStack()).amount() == 200, "draining 100 mB must leave 200 mB");
        try (Transaction transaction = Transaction.openOuter()) {
            BCTests.require(helper, fluids.extract(variant, 1000 * DROPLETS_PER_MB, transaction) == 200 * DROPLETS_PER_MB, "the rest must drain");
            transaction.commit();
        }
        BCTests.require(helper, context.getItemVariant().isBlank(), "an emptied shard must be used up");
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

    /** A plug closes a pipe face to block API caches and to storages callers kept; removing it opens the face again. */
    @GameTest(template = FabricGameTests.TEMPLATE, timeoutTicks = 20)
    public void keptStoragesFollowPlugs(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        BCTests.chest(helper, new BlockPos(0, 1, 1));
        PipeBlockEntity pipe = BCTests.pipe(helper, pos, BCTransport.STONE_ITEM);
        BCTests.chest(helper, new BlockPos(2, 1, 1));
        BlockApiCache<Storage<ItemVariant>, Direction> cache = BlockApiCache.create(ItemStorage.SIDED, helper.getLevel(), helper.absolutePos(pos));
        ItemVariant apple = ItemVariant.of(Items.APPLE);
        helper.runAfterDelay(2, () -> {
            Storage<ItemVariant> kept = cache.find(Direction.WEST);
            BCTests.require(helper, kept != null && kept.supportsInsertion(), "the pipe must expose an item storage on a connected face");
            pipe.replacePluggable(Direction.WEST, BCTransport.BLOCKER.create(pipe, Direction.WEST));
            BCTests.require(helper, cache.find(Direction.WEST) == null, "a cached lookup must see the plug");
            BCTests.require(helper, !kept.supportsInsertion(), "a kept storage must stop offering insertion behind a plug");
            try (Transaction transaction = Transaction.openOuter()) {
                BCTests.require(helper, kept.insert(apple, 1, transaction) == 0, "a kept storage must refuse items behind a plug");
            }
            pipe.removePluggable(Direction.WEST, new ArrayList<>());
            Storage<ItemVariant> reopened = cache.find(Direction.WEST);
            BCTests.require(helper, reopened != null, "a cached lookup must see the face open again");
            try (Transaction transaction = Transaction.openOuter()) {
                BCTests.require(helper, reopened.insert(apple, 1, transaction) == 1 && kept.insert(apple, 1, transaction) == 1,
                        "both storages must take items once the plug is gone");
            }
            helper.succeed();
        });
    }
}
