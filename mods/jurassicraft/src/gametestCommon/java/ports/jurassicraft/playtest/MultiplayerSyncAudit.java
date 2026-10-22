package ports.jurassicraft.playtest;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.entity.DinosaurEggEntity;
import ports.jurassicraft.entity.DinosaurEntity;
import ports.jurassicraft.entity.DinosaurRegistry;
import ports.jurassicraft.feeder.FeederBlockEntity;
import ports.jurassicraft.feeder.FeederRegistry;
import ports.jurassicraft.fence.FenceRegistry;
import ports.jurassicraft.vehicle.ExplorerEntity;
import ports.jurassicraft.vehicle.HelicopterEntity;
import ports.jurassicraft.vehicle.VehicleEntity;
import ports.jurassicraft.vehicle.VehicleRegistry;

/**
 * Server-side two-player contract: seat data, a shared machine menu, one powered fence,
 * and a breeding result both players would be sent. Animation playback stays with the
 * animation work and is not asserted here.
 */
public final class MultiplayerSyncAudit {
    private MultiplayerSyncAudit() {}

    public static List<String> failures(GameTestHelper helper) {
        List<String> failures = new ArrayList<>();
        vehicles(helper, failures);
        feeder(helper, failures);
        fence(helper, failures);
        breeding(helper, failures);
        return failures;
    }

    private static void vehicles(GameTestHelper helper, List<String> failures) {
        for (int x = 0; x < 15; x++) for (int z = 0; z < 15; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        HelicopterEntity helicopter = helper.spawn(VehicleRegistry.HELICOPTER.get(), new BlockPos(7, 1, 7));
        Player pilot = helper.makeMockPlayer();
        Player passenger = helper.makeMockPlayer();
        if (!pilot.startRiding(helicopter) || !passenger.startRiding(helicopter))
            failures.add("two players could not board the helicopter");
        else {
            if (helicopter.passengerInSeat(0) != pilot || helicopter.passengerInSeat(1) != passenger)
                failures.add("helicopter seat data did not assign the first two riders");
            if (helicopter.acceptControls(passenger, VehicleEntity.UP))
                failures.add("a helicopter passenger was accepted as the pilot");
            if (!helicopter.acceptControls(pilot, VehicleEntity.UP))
                failures.add("the helicopter pilot's control packet was rejected");
            helicopter.positionRider(pilot);
            helicopter.positionRider(passenger);
            if (pilot.position().distanceTo(passenger.position()) < .4)
                failures.add("helicopter riders share one seat position");
            passenger.stopRiding();
            if (helicopter.passengerInSeat(1) != null)
                failures.add("leaving the helicopter left a stale seat");
        }
        helicopter.ejectPassengers();
        helicopter.discard();

        ExplorerEntity explorer = helper.spawn(VehicleRegistry.EXPLORER.get(), new BlockPos(3, 1, 3));
        Player driver = helper.makeMockPlayer();
        Player guest = helper.makeMockPlayer();
        if (!driver.startRiding(explorer) || !guest.startRiding(explorer))
            failures.add("two players could not board the tour vehicle");
        explorer.setOnTourRail(true);
        if (!explorer.isOnTourRail() || explorer.getDimensions(explorer.getPose()).height > .3F)
            failures.add("tour-rail state did not sync onto the explorer");
        explorer.positionRider(driver);
        explorer.positionRider(guest);
        if (driver.getVehicle() == explorer && guest.getVehicle() == explorer
                && driver.position().distanceTo(guest.position()) < .4)
            failures.add("tour-rail riders share one seat position");
        explorer.ejectPassengers();
        explorer.discard();
    }

    private static void feeder(GameTestHelper helper, List<String> failures) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, FeederRegistry.BLOCK.get());
        FeederBlockEntity block = (FeederBlockEntity) helper.getBlockEntity(pos);
        BlockPos world = helper.absolutePos(pos);
        Player first = helper.makeMockPlayer();
        Player second = helper.makeMockPlayer();
        first.moveTo(world.getX() + .5, world.getY(), world.getZ() + .5, 0, 0);
        second.moveTo(world.getX() + .5, world.getY(), world.getZ() + 1.5, 0, 0);
        AbstractContainerMenu menuA = block.createMenu(1, first.getInventory(), first);
        AbstractContainerMenu menuB = block.createMenu(2, second.getInventory(), second);
        if (menuA == null || menuB == null || !menuA.stillValid(first) || !menuB.stillValid(second)) {
            failures.add("two players could not keep the feeder menu open");
            return;
        }
        first.getInventory().setItem(0, new ItemStack(Items.BEEF, 3));
        second.getInventory().setItem(0, new ItemStack(Items.CARROT, 2));
        if (menuA.quickMoveStack(first, 45).isEmpty()) failures.add("first player could not move meat into the feeder");
        if (menuB.quickMoveStack(second, 45).isEmpty()) failures.add("second player could not move plants into the feeder");
        if (!menuB.getSlot(0).getItem().is(Items.BEEF) || menuB.getSlot(0).getItem().getCount() != 3)
            failures.add("second player's feeder menu did not see the meat placed by the first");
        if (!menuA.getSlot(9).getItem().is(Items.CARROT) || menuA.getSlot(9).getItem().getCount() != 2)
            failures.add("first player's feeder menu did not see the plants placed by the second");
        menuA.removed(first);
        menuB.removed(second);
    }

    private static void fence(GameTestHelper helper, List<String> failures) {
        helper.setBlock(new BlockPos(2, 1, 10), FenceRegistry.BASE.get());
        helper.setBlock(new BlockPos(2, 2, 10), FenceRegistry.POLE.get());
        helper.setBlock(new BlockPos(3, 1, 10), FenceRegistry.BASE.get());
        helper.setBlock(new BlockPos(3, 2, 10), FenceRegistry.WIRE.get());
        helper.setBlock(new BlockPos(1, 1, 10), Blocks.REDSTONE_BLOCK);
        BlockPos wire = helper.absolutePos(new BlockPos(3, 2, 10));
        var state = helper.getBlockState(new BlockPos(3, 2, 10));
        if (!state.is(FenceRegistry.WIRE.get())) {
            failures.add("shared fence wire did not place");
            return;
        }
        Player left = helper.makeMockPlayer();
        Player right = helper.makeMockPlayer();
        left.tickCount = 20;
        right.tickCount = 20;
        float leftHealth = left.getHealth();
        float rightHealth = right.getHealth();
        state.getBlock().entityInside(state, helper.getLevel(), wire, left);
        state.getBlock().entityInside(state, helper.getLevel(), wire, right);
        if (left.getHealth() >= leftHealth || right.getHealth() >= rightHealth)
            failures.add("one powered fence did not shock both players");
    }

    private static void breeding(GameTestHelper helper, List<String> failures) {
        DinosaurEntity mother = DinosaurRegistry.type(Species.MUSSAURUS).create(helper.getLevel());
        DinosaurEntity father = DinosaurRegistry.type(Species.MUSSAURUS).create(helper.getLevel());
        if (mother == null || father == null) {
            failures.add("could not create a breeding pair");
            return;
        }
        BlockPos pos = helper.absolutePos(new BlockPos(10, 1, 10));
        mother.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
        father.moveTo(pos.getX() + 1.2, pos.getY(), pos.getZ() + .5, 0, 0);
        mother.setMale(false);
        father.setMale(true);
        mother.setBaby(true);
        mother.setBaby(false);
        father.setBaby(false);
        if (mother.growth() <= .75f || !mother.getEntityData().isDirty())
            failures.add("adult growth was not marked for client sync");
        mother.getEntityData().clearDirty();
        if (!mother.forceBreedWith(father) || !mother.deliverPregnancyNow()) {
            failures.add("two adults did not produce a shared breeding result");
            mother.discard();
            father.discard();
            return;
        }
        AABB area = new AABB(pos).inflate(4);
        List<DinosaurEggEntity> eggs = helper.getLevel().getEntitiesOfClass(DinosaurEggEntity.class, area, entity -> true);
        if (eggs.isEmpty()) failures.add("breeding did not spawn an egg both players can track");
        else if (eggs.get(0).species() != Species.MUSSAURUS)
            failures.add("laid egg species was not synced for the second player");
        eggs.forEach(DinosaurEggEntity::discard);
        mother.discard();
        father.discard();
    }
}
