package ports.jurassicraft.vehicle;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ports.jurassicraft.platform.Network;

import java.util.stream.Collectors;

/** Optional in-world checks. */
public final class VehicleGameTests {
    private static final BlockPos POS = new BlockPos(7, 1, 7);
    private VehicleGameTests() {}
    private static void require(GameTestHelper helper, boolean condition, String message) { if (!condition) helper.fail(message); }
    private static void floor(GameTestHelper helper) {
        for (int x = 0; x < 15; x++) for (int z = 0; z < 15; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
    }
    private static void water(GameTestHelper helper, boolean present) {
        for (int x = 6; x <= 8; x++) for (int y = 1; y <= 4; y++) for (int z = 6; z <= 8; z++)
            helper.setBlock(new BlockPos(x, y, z), present ? Blocks.WATER : Blocks.AIR);
    }
    private static void helicopterInput(HelicopterEntity helicopter, Player pilot, int controls, int ticks) {
        for (int i = 0; i < ticks; i++) {
            helicopter.acceptControls(pilot, controls);
            helicopter.tick();
        }
    }
    /** The same bytes the client writes: entity id, then one control byte. */
    private static boolean controlPacket(Player player, VehicleEntity vehicle, int state) {
        FriendlyByteBuf buffer = Network.buffer(data -> data.writeVarInt(vehicle.getId()).writeByte(state));
        try {
            return VehicleNetwork.applyControl(player, buffer);
        } finally {
            buffer.release();
        }
    }
    private static void controlPackets(GameTestHelper helper, Player player, VehicleEntity vehicle, int state, int ticks, String message) {
        for (int i = 0; i < ticks; i++) {
            require(helper, controlPacket(player, vehicle, state), message);
            vehicle.tick();
        }
    }
    private static void groundVehicleIgnoresAltitude(GameTestHelper helper, VehicleEntity vehicle, String name) {
        Player driver = helper.makeMockPlayer();
        require(helper, driver.startRiding(vehicle), name + " driver should mount");
        for (int i = 0; i < 5; i++) vehicle.tick();
        Vec3 parked = vehicle.position();
        controlPackets(helper, driver, vehicle, VehicleEntity.UP | VehicleEntity.DOWN, 20, name + " should accept an altitude packet");
        require(helper, Math.abs(vehicle.getY() - parked.y) < .05 && vehicle.position().distanceTo(parked) < .05,
                name + " must not move when the packet only asks to ascend and descend");
        controlPackets(helper, driver, vehicle, VehicleEntity.FORWARD | VehicleEntity.UP | VehicleEntity.DOWN, 10,
                name + " should accept throttle");
        require(helper, Math.hypot(vehicle.getX() - parked.x, vehicle.getZ() - parked.z) > .1
                        && Math.abs(vehicle.getY() - parked.y) < .05,
                name + " should drive without climbing when altitude bits are set");
        vehicle.ejectPassengers();
        vehicle.discard();
    }
    @GameTest(template = "jurassicraft:vehicle_test")
    public static void movingVehicleHitsLivingEntities(GameTestHelper helper) {
        floor(helper);
        JeepEntity jeep = helper.spawn(VehicleRegistry.JEEP.get(), POS);
        Pig pig = helper.spawn(EntityType.PIG, POS);
        float initialHealth = pig.getHealth();
        jeep.tick();
        require(helper, pig.getHealth() == initialHealth, "a stationary vehicle must not hurt an animal");
        Vec3 away = helper.absoluteVec(new Vec3(2.5, 1, 2.5));
        pig.setPos(away.x, away.y, away.z);
        Vec3 start = jeep.position();
        jeep.setDeltaMovement(0, 0, .3);
        jeep.tick();
        require(helper, jeep.position().distanceTo(start) > .1, "vehicle should be travelling fast enough to hit");
        pig.setPos(jeep.getX(), jeep.getY(), jeep.getZ());
        jeep.tick();
        require(helper, pig.getHealth() < initialHealth, "a moving vehicle should damage an animal at its front");
        pig.discard();
        jeep.discard();
        helper.succeed();
    }
    @GameTest(template = "jurassicraft:vehicle_test")
    public static void waterDamagesOccupantsAndDisablesEngines(GameTestHelper helper) {
        floor(helper);
        water(helper, true);
        JeepEntity jeep = helper.spawn(VehicleRegistry.JEEP.get(), POS);
        Player driver = helper.makeMockPlayer();
        require(helper, driver.startRiding(jeep) && jeep.acceptControls(driver, VehicleEntity.FORWARD), "driver should be able to start the engine");
        float health = driver.getHealth();
        double startZ = jeep.getZ();
        for (int i = 0; i < 24; i++) jeep.tick();
        require(helper, jeep.isInWater() && Math.abs(jeep.getZ() - startZ) < .01, "water should disable the vehicle's engine");
        require(helper, driver.getHealth() < health, "a submerged vehicle should drown its occupants");
        require(helper, Math.abs(jeep.damageFraction() - .15F) < .001, "water should deal a quarter damage per tick without healing");
        water(helper, false);
        for (int i = 0; i < 45; i++) jeep.tick();
        require(helper, Math.abs(jeep.damageFraction() - .15F) < .001, "water damage should remain after the vehicle dries out");
        jeep.ejectPassengers();
        jeep.discard();
        helper.succeed();
    }
    @GameTest(template = "jurassicraft:vehicle_test")
    public static void helicopterCannotLiftInWater(GameTestHelper helper) {
        floor(helper);
        water(helper, true);
        HelicopterEntity helicopter = helper.spawn(VehicleRegistry.HELICOPTER.get(), POS);
        Player pilot = helper.makeMockPlayer();
        require(helper, pilot.startRiding(helicopter) && helicopter.acceptControls(pilot, VehicleEntity.UP), "pilot should mount and request lift");
        double startY = helicopter.getY();
        for (int i = 0; i < 5; i++) helicopter.tick();
        require(helper, helicopter.isInWater() && helicopter.getY() <= startY + .001, "a submerged helicopter must not gain lift");
        require(helper, helicopter.damageFraction() > 0, "the submerged helicopter should sustain damage");
        helicopter.ejectPassengers();
        helicopter.discard();
        water(helper, false);
        helper.succeed();
    }
    @GameTest(template = "jurassicraft:vehicle_test")
    public static void playerHitRepairsAfterDelay(GameTestHelper helper) {
        floor(helper);
        JeepEntity jeep = helper.spawn(VehicleRegistry.JEEP.get(), POS);
        jeep.hurt(DamageSource.GENERIC, 2);
        jeep.hurt(DamageSource.playerAttack(helper.makeMockPlayer()), 1);
        require(helper, Math.abs(jeep.damageFraction() - .3F) < .001, "player damage should be multiplied by ten");
        CompoundTag tag = jeep.saveWithoutId(new CompoundTag());
        JeepEntity loaded = VehicleRegistry.JEEP.get().create(helper.getLevel());
        require(helper, loaded != null, "jeep should be restorable");
        loaded.load(tag);
        for (int i = 0; i < 40; i++) loaded.tick();
        require(helper, Math.abs(loaded.damageFraction() - .3F) < .001, "player damage should not repair before the cooldown");
        for (int i = 0; i < 20; i++) loaded.tick();
        require(helper, Math.abs(loaded.damageFraction() - .05F) < .001, "only the saved player-hit damage should repair");
        jeep.discard();
        loaded.discard();
        helper.succeed();
    }
    @GameTest(template = "jurassicraft:vehicle_test")
    public static void jeepSeatsDriverAndMovement(GameTestHelper helper) {
        floor(helper);
        JeepEntity jeep = helper.spawn(VehicleRegistry.JEEP.get(), POS);
        Player driver = helper.makeMockPlayer(), passenger = helper.makeMockPlayer();
        require(helper, driver.startRiding(jeep), "first player should mount as driver");
        require(helper, passenger.startRiding(jeep), "second player should mount as a passenger");
        require(helper, jeep.getControllingPassenger() == driver, "first seat should control the vehicle");
        require(helper, !jeep.acceptControls(passenger, VehicleEntity.BACK), "passenger controls must be rejected");
        require(helper, jeep.acceptControls(driver, VehicleEntity.FORWARD | VehicleEntity.RIGHT), "driver controls must be accepted");
        double oldX = jeep.getX(), oldZ = jeep.getZ();
        for (int i = 0; i < 10; i++) jeep.tick();
        require(helper, Math.hypot(jeep.getX() - oldX, jeep.getZ() - oldZ) > .1 && jeep.getYRot() > 0,
                "driver input must move and steer the jeep");
        require(helper, helper.makeMockPlayer().startRiding(jeep) && helper.makeMockPlayer().startRiding(jeep), "four players should fit");
        require(helper, !helper.makeMockPlayer().startRiding(jeep), "a fifth player must be rejected");
        jeep.positionRider(driver); jeep.positionRider(passenger);
        require(helper, driver.position().distanceTo(passenger.position()) > .5, "passengers should occupy different seats");
        jeep.ejectPassengers();
        jeep.discard();
        helper.succeed();
    }
    @GameTest(template = "jurassicraft:vehicle_test")
    public static void ridersDoNotCarryFallDistance(GameTestHelper helper) {
        floor(helper);
        JeepEntity jeep = helper.spawn(VehicleRegistry.JEEP.get(), POS);
        Player driver = helper.makeMockPlayer(), passenger = helper.makeMockPlayer(), outsider = helper.makeMockPlayer();
        driver.fallDistance = 15;
        require(helper, driver.startRiding(jeep) && driver.fallDistance == 0, "mounting should clear the driver's fall distance");
        require(helper, passenger.startRiding(jeep), "second rider should mount");
        driver.fallDistance = 8; passenger.fallDistance = 12; outsider.fallDistance = 20;
        jeep.tick();
        require(helper, driver.fallDistance == 0 && passenger.fallDistance == 0,
                "the vehicle should clear fall distance for every seated rider");
        require(helper, outsider.fallDistance == 20, "players outside the vehicle must be unaffected");
        jeep.ejectPassengers();
        jeep.discard();
        helper.succeed();
    }
    @GameTest(template = "jurassicraft:vehicle_test")
    public static void steeringAloneMovesFromRest(GameTestHelper helper) {
        floor(helper);
        JeepEntity jeep = helper.spawn(VehicleRegistry.JEEP.get(), POS);
        Player driver = helper.makeMockPlayer();
        require(helper, driver.startRiding(jeep) && jeep.acceptControls(driver, VehicleEntity.RIGHT),
                "driver should be able to steer from rest");
        Vec3 start = jeep.position();
        float yaw = jeep.getYRot();
        for (int i = 0; i < 10; i++) jeep.tick();
        require(helper, jeep.position().distanceTo(start) > .1 && jeep.getYRot() > yaw,
                "turning without throttle should creep forward and steer");
        jeep.ejectPassengers();
        jeep.discard();
        helper.succeed();
    }
    @GameTest(template = "jurassicraft:vehicle_test")
    public static void vehicleDamageAndRecoveryPersist(GameTestHelper helper) {
        floor(helper);
        JeepEntity jeep = helper.spawn(VehicleRegistry.JEEP.get(), POS);
        jeep.hurt(DamageSource.GENERIC, 12);
        CompoundTag saved = jeep.saveWithoutId(new CompoundTag());
        JeepEntity loaded = VehicleRegistry.JEEP.get().create(helper.getLevel());
        require(helper, loaded != null, "jeep entity must be restorable");
        loaded.load(saved);
        require(helper, loaded.saveWithoutId(new CompoundTag()).getFloat("Damage") == 12, "damage must survive a save");
        require(helper, Math.abs(jeep.damageFraction() - .3F) < .001F && Math.abs(loaded.damageFraction() - .3F) < .001F,
                "visible damage state should match persisted vehicle damage");
        AABB area = new AABB(helper.absolutePos(POS)).inflate(4);
        var previousItems = helper.getLevel().getEntitiesOfClass(ItemEntity.class, area).stream().map(ItemEntity::getUUID).collect(Collectors.toSet());
        jeep.hurt(DamageSource.GENERIC, 40);
        require(helper, jeep.isRemoved(), "severely damaged jeep should be removed");
        var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, area,
                item -> !previousItems.contains(item.getUUID()) && item.getItem().is(VehicleRegistry.JEEP_ITEM.get()));
        require(helper, drops.size() == 1 && drops.get(0).getItem().getCount() == 1, "destroying the jeep should drop one jeep item");
        drops.forEach(ItemEntity::discard);
        helper.succeed();
    }
    @GameTest(template = "jurassicraft:vehicle_test")
    public static void recordSwapAndPersistence(GameTestHelper helper) {
        JeepEntity jeep = helper.spawn(VehicleRegistry.JEEP.get(), POS);
        Player driver = helper.makeMockPlayer(), passenger = helper.makeMockPlayer(), outsider = helper.makeMockPlayer();
        require(helper, driver.startRiding(jeep) && passenger.startRiding(jeep), "players should mount the jeep");
        ItemStack disc = new ItemStack(Items.MUSIC_DISC_13);
        disc.getOrCreateTag().putString("custom", "saved");
        outsider.setItemInHand(InteractionHand.MAIN_HAND, disc.copy());
        require(helper, !jeep.swapRecord(outsider) && jeep.record().isEmpty(), "non-passengers cannot operate the radio");
        driver.setItemInHand(InteractionHand.MAIN_HAND, disc.copy());
        require(helper, jeep.swapRecord(driver) && driver.getMainHandItem().isEmpty(), "inserting a disc should consume the held item");
        require(helper, ItemStack.matches(disc, jeep.record()), "the slot should preserve the disc and its data");
        JeepEntity loaded = VehicleRegistry.JEEP.get().create(helper.getLevel());
        require(helper, loaded != null, "jeep should be restorable");
        loaded.load(jeep.saveWithoutId(new CompoundTag()));
        require(helper, ItemStack.matches(disc, loaded.record()), "saving and loading must preserve the record");
        passenger.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));
        require(helper, !jeep.swapRecord(passenger) && ItemStack.matches(disc, jeep.record()), "non-record items must not alter the slot");
        passenger.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.MUSIC_DISC_CAT));
        require(helper, jeep.swapRecord(passenger) && ItemStack.matches(disc, passenger.getMainHandItem())
                && jeep.record().is(Items.MUSIC_DISC_CAT), "a passenger should swap discs and receive the previous one");
        driver.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        require(helper, jeep.swapRecord(driver) && driver.getMainHandItem().is(Items.MUSIC_DISC_CAT)
                && jeep.record().isEmpty(), "an empty hand should eject the current disc");
        passenger.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        require(helper, !jeep.swapRecord(passenger), "an empty slot and empty hand should do nothing");
        jeep.ejectPassengers();
        jeep.discard();
        helper.succeed();
    }
    @GameTest(template = "jurassicraft:vehicle_test")
    public static void brokenVehicleDropsRecord(GameTestHelper helper) {
        JeepEntity jeep = helper.spawn(VehicleRegistry.JEEP.get(), POS);
        Player driver = helper.makeMockPlayer();
        require(helper, driver.startRiding(jeep), "driver should mount");
        ItemStack disc = new ItemStack(Items.MUSIC_DISC_13);
        disc.getOrCreateTag().putString("custom", "survives");
        driver.setItemInHand(InteractionHand.MAIN_HAND, disc.copy());
        require(helper, jeep.swapRecord(driver), "disc should be inserted");
        AABB area = new AABB(helper.absolutePos(POS)).inflate(4);
        var previousItems = helper.getLevel().getEntitiesOfClass(ItemEntity.class, area).stream().map(ItemEntity::getUUID).collect(Collectors.toSet());
        jeep.hurt(DamageSource.GENERIC, 40);
        var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, area,
                item -> !previousItems.contains(item.getUUID()));
        var records = drops.stream().filter(item -> item.getItem().is(Items.MUSIC_DISC_13)).toList();
        require(helper, records.size() == 1 && ItemStack.matches(disc, records.get(0).getItem()), "destroying the jeep must drop the inserted record and its data once");
        drops.forEach(ItemEntity::discard);
        helper.succeed();
    }
    @GameTest(template = "jurassicraft:vehicle_test")
    public static void helicopterAscendsAndDriverChanges(GameTestHelper helper) {
        floor(helper);
        HelicopterEntity helicopter = helper.spawn(VehicleRegistry.HELICOPTER.get(), POS);
        Player pilot = helper.makeMockPlayer(), passenger = helper.makeMockPlayer();
        require(helper, pilot.startRiding(helicopter) && passenger.startRiding(helicopter), "pilot and passenger should mount");
        require(helper, helicopter.acceptControls(pilot, VehicleEntity.UP), "pilot lift input must be accepted");
        double initialY = helicopter.getY();
        helicopterInput(helicopter, pilot, VehicleEntity.UP, 170);
        require(helper, helicopter.getY() > initialY + .1, "helicopter should gain altitude");
        pilot.stopRiding();
        require(helper, !helicopter.acceptControls(pilot, VehicleEntity.UP), "former pilot must lose control");
        require(helper, helicopter.getControllingPassenger() == null && helicopter.changeSeat(passenger, 0)
                        && helicopter.getControllingPassenger() == passenger && helicopter.acceptControls(passenger, VehicleEntity.DOWN),
                "a passenger should be able to take the empty pilot seat");
        helicopter.ejectPassengers();
        helicopter.discard();
        helper.succeed();
    }
    @GameTest(template = "jurassicraft:vehicle_test")
    public static void helicopterAltitudeFollowsControlPacket(GameTestHelper helper) {
        floor(helper);
        HelicopterEntity helicopter = helper.spawn(VehicleRegistry.HELICOPTER.get(), POS);
        Player pilot = helper.makeMockPlayer(), passenger = helper.makeMockPlayer();
        require(helper, pilot.startRiding(helicopter) && passenger.startRiding(helicopter), "pilot and passenger should mount");
        FriendlyByteBuf extra = Network.buffer(data -> data.writeVarInt(helicopter.getId()).writeByte(VehicleEntity.UP).writeByte(1));
        try {
            require(helper, !VehicleNetwork.applyControl(pilot, extra), "a control packet with trailing bytes must be ignored");
        } finally {
            extra.release();
        }
        require(helper, !controlPacket(passenger, helicopter, VehicleEntity.UP), "a passenger altitude packet must be rejected");
        double startY = helicopter.getY();
        controlPackets(helper, pilot, helicopter, VehicleEntity.UP, 170, "the ascend packet should be accepted");
        require(helper, helicopter.getY() > startY + .1, "the ascend packet should lift the helicopter");
        double peak = helicopter.getY();
        float rpm = helicopter.engineRpm();
        controlPackets(helper, pilot, helicopter, VehicleEntity.DOWN, 200, "the descend packet should be accepted");
        require(helper, helicopter.getY() < peak - .1 && helicopter.engineRpm() < rpm, "the descend packet should lower the helicopter");
        helicopter.ejectPassengers();
        helicopter.discard();
        groundVehicleIgnoresAltitude(helper, helper.spawn(VehicleRegistry.JEEP.get(), POS), "jeep");
        groundVehicleIgnoresAltitude(helper, helper.spawn(VehicleRegistry.EXPLORER.get(), POS), "explorer");
        helper.succeed();
    }
    @GameTest(template = "jurassicraft:vehicle_test")
    public static void changingSeatsIsServerControlled(GameTestHelper helper) {
        floor(helper);
        JeepEntity jeep = helper.spawn(VehicleRegistry.JEEP.get(), POS);
        Player driver = helper.makeMockPlayer(), passenger = helper.makeMockPlayer(), outsider = helper.makeMockPlayer();
        require(helper, driver.startRiding(jeep) && passenger.startRiding(jeep), "two riders should mount");
        require(helper, jeep.seatOf(driver) == 0 && jeep.seatOf(passenger) == 1, "riders should take distinct seats");
        require(helper, !jeep.changeSeat(outsider, 2) && !jeep.changeSeat(passenger, -1)
                        && !jeep.changeSeat(passenger, 4) && !jeep.changeSeat(passenger, 0),
                "outsiders, invalid seats and occupied seats must be rejected");
        require(helper, jeep.acceptControls(driver, VehicleEntity.FORWARD), "driver should be able to steer");
        require(helper, jeep.changeSeat(driver, 2) && jeep.seatOf(driver) == 2 && jeep.seatOf(passenger) == 1
                        && jeep.getControllingPassenger() == null,
                "changing seats must leave other riders in place and empty the driver seat");
        jeep.positionRider(driver); jeep.positionRider(passenger);
        require(helper, driver.position().distanceTo(passenger.position()) > .5, "riders should move to their selected positions");
        double initialX = jeep.getX(), initialZ = jeep.getZ();
        jeep.tick();
        require(helper, Math.hypot(jeep.getX() - initialX, jeep.getZ() - initialZ) < .001,
                "driver controls must clear when the driver leaves that seat");
        require(helper, jeep.changeSeat(passenger, 0) && jeep.getControllingPassenger() == passenger
                        && !jeep.acceptControls(driver, VehicleEntity.FORWARD)
                        && jeep.acceptControls(passenger, VehicleEntity.FORWARD),
                "only the rider who takes the driver seat may drive");
        passenger.stopRiding();
        require(helper, jeep.getControllingPassenger() == null && jeep.seatOf(driver) == 2,
                "dismounting must not move remaining riders");
        jeep.ejectPassengers();
        jeep.discard();
        helper.succeed();
    }
    @GameTest(template = "jurassicraft:vehicle_test")
    public static void helicopterStrafesAndTurnsSeparately(GameTestHelper helper) {
        floor(helper);
        HelicopterEntity helicopter = helper.spawn(VehicleRegistry.HELICOPTER.get(), POS);
        Player pilot = helper.makeMockPlayer();
        require(helper, pilot.startRiding(helicopter), "pilot should mount");
        require(helper, helicopter.acceptControls(pilot, VehicleEntity.UP), "pilot should take off");
        helicopterInput(helicopter, pilot, VehicleEntity.UP, 170);
        double startX = helicopter.getX();
        float startYaw = helicopter.getYRot();
        require(helper, helicopter.acceptControls(pilot, VehicleEntity.LEFT), "pilot should strafe");
        for (int i = 0; i < 5; i++) helicopter.tick();
        require(helper, helicopter.getX() < startX - .1 && Math.abs(helicopter.getYRot() - startYaw) < .01,
                "left movement must strafe without rotating the aircraft");
        require(helper, helicopter.acceptControls(pilot, VehicleEntity.ROTATE_RIGHT), "pilot should rotate");
        for (int i = 0; i < 5; i++) helicopter.tick();
        require(helper, helicopter.getYRot() > startYaw + .5, "rotate-right must turn the aircraft without forward speed");
        helicopter.ejectPassengers();
        helicopter.discard();
        helper.succeed();
    }
    @GameTest(template = "jurassicraft:vehicle_test")
    public static void helicopterFlightModesAreAuthorizedAndPersistent(GameTestHelper helper) {
        floor(helper);
        HelicopterEntity helicopter = helper.spawn(VehicleRegistry.HELICOPTER.get(), POS);
        Player pilot = helper.makeMockPlayer(), passenger = helper.makeMockPlayer(), outsider = helper.makeMockPlayer();
        require(helper, pilot.startRiding(helicopter) && passenger.startRiding(helicopter), "pilot and passenger should mount");
        require(helper, helicopter.autopilot() && helicopter.rotationLock(), "flight assistance and lock should start enabled");
        require(helper, !helicopter.toggleFlightMode(passenger, HelicopterEntity.TOGGLE_AUTOPILOT)
                        && !helicopter.toggleFlightMode(outsider, HelicopterEntity.TOGGLE_ROTATION_LOCK)
                        && !helicopter.toggleFlightMode(pilot, 99), "only the pilot may request valid mode changes");
        require(helper, helicopter.toggleFlightMode(pilot, HelicopterEntity.TOGGLE_AUTOPILOT)
                        && helicopter.toggleFlightMode(pilot, HelicopterEntity.TOGGLE_ROTATION_LOCK)
                        && !helicopter.autopilot() && !helicopter.rotationLock(), "pilot should be able to disable both assists");
        CompoundTag saved = helicopter.saveWithoutId(new CompoundTag());
        saved.putFloat("EngineRpm", 200);
        saved.putFloat("FlightPitch", 12);
        saved.putFloat("FlightRoll", -8);
        HelicopterEntity loaded = VehicleRegistry.HELICOPTER.get().create(helper.getLevel());
        require(helper, loaded != null, "helicopter should be restorable");
        loaded.load(saved);
        require(helper, !loaded.autopilot() && !loaded.rotationLock() && loaded.engineRpm() == 200
                        && loaded.flightPitch() == 12 && loaded.flightRoll() == -8,
                "saved flight modes, rotor speed and attitude should survive loading");
        helicopter.ejectPassengers();
        helicopter.discard();
        loaded.discard();
        helper.succeed();
    }
    @GameTest(template = "jurassicraft:vehicle_test")
    public static void helicopterRotorAndAutopilotAffectFlight(GameTestHelper helper) {
        require(helper, Math.abs(HelicopterFlight.hoverRpm(0, 0) - 172.4F) < .2F,
                "transport helicopter should use its original hover RPM");
        double hover = HelicopterFlight.motion(Vec3.ZERO, 0, HelicopterFlight.hoverRpm(0, 0), 0, 0, false, true).y;
        require(helper, Math.abs(hover) < .001 && HelicopterFlight.motion(Vec3.ZERO, 0, 220, 0, 0, false, true).y > 0
                        && HelicopterFlight.motion(Vec3.ZERO, 0, 100, 0, 0, false, true).y < 0,
                "rotor force should lift, hover, or descend according to RPM");
        require(helper, HelicopterFlight.maximumTilt(4, true) == 15 && HelicopterFlight.maximumTilt(4, false) == 180,
                "rotation lock should limit flight attitude");
        floor(helper);
        HelicopterEntity helicopter = helper.spawn(VehicleRegistry.HELICOPTER.get(), POS);
        Player pilot = helper.makeMockPlayer();
        require(helper, pilot.startRiding(helicopter), "pilot should mount");
        CompoundTag state = helicopter.saveWithoutId(new CompoundTag());
        state.putFloat("EngineRpm", 180);
        state.putFloat("FlightPitch", 10);
        state.putFloat("FlightRoll", 8);
        helicopter.load(state);
        helicopter.setPos(helicopter.getX(), helicopter.getY() + 5, helicopter.getZ());
        helicopterInput(helicopter, pilot, 0, 5);
        require(helper, helicopter.flightPitch() < 10 && helicopter.flightRoll() < 8,
                "autopilot with rotation lock should level an airborne aircraft");
        require(helper, helicopter.toggleFlightMode(pilot, HelicopterEntity.TOGGLE_AUTOPILOT), "pilot should be able to disable autopilot");
        float oldPitch = helicopter.flightPitch(), oldRoll = helicopter.flightRoll();
        helicopterInput(helicopter, pilot, 0, 5);
        require(helper, Math.abs(helicopter.flightPitch() - oldPitch) < .001 && Math.abs(helicopter.flightRoll() - oldRoll) < .001,
                "manual flight should retain its attitude when controls are released");
        helicopter.ejectPassengers();
        helicopter.discard();
        helper.succeed();
    }
    @GameTest(template = "jurassicraft:vehicle_test")
    public static void helicopterRotorTakesDamageFromObstructions(GameTestHelper helper) {
        floor(helper);
        HelicopterEntity helicopter = helper.spawn(VehicleRegistry.HELICOPTER.get(), POS);
        Player pilot = helper.makeMockPlayer();
        require(helper, pilot.startRiding(helicopter), "pilot should mount");
        require(helper, helicopter.toggleFlightMode(pilot, HelicopterEntity.TOGGLE_AUTOPILOT), "pilot should disable automatic engine control");
        BlockPos obstacle = POS.offset(4, 3, 0);
        helper.setBlock(obstacle, Blocks.STONE);
        helicopterInput(helicopter, pilot, 0, 1);
        require(helper, helicopter.damageFraction() == 0, "a stopped rotor must not take obstruction damage");
        CompoundTag running = helicopter.saveWithoutId(new CompoundTag());
        running.putFloat("EngineRpm", 150);
        helicopter.load(running);
        helicopterInput(helicopter, pilot, 0, 1);
        require(helper, Math.abs(helicopter.damageFraction() - .025F) < .001,
                "a rotor striking a block should take damage proportional to RPM");
        helper.setBlock(obstacle, Blocks.AIR);
        helicopterInput(helicopter, pilot, 0, 1);
        require(helper, Math.abs(helicopter.damageFraction() - .025F) < .001,
                "damage should stop when the rotor is clear");
        helicopter.ejectPassengers();
        helicopter.discard();
        helper.succeed();
    }
    @GameTest(template = "jurassicraft:vehicle_test")
    public static void damagedHelicopterSpiralsAndCrashes(GameTestHelper helper) {
        floor(helper);
        HelicopterEntity helicopter = helper.spawn(VehicleRegistry.HELICOPTER.get(), POS);
        Player pilot = helper.makeMockPlayer();
        require(helper, pilot.startRiding(helicopter), "pilot should mount");
        helicopter.hurt(DamageSource.GENERIC, 38);
        CompoundTag damaged = helicopter.saveWithoutId(new CompoundTag());
        damaged.putFloat("EngineRpm", 180);
        helicopter.load(damaged);
        helicopter.setPos(helicopter.getX(), helicopter.getY() + 8, helicopter.getZ());
        helicopterInput(helicopter, pilot, 0, 5);
        require(helper, helicopter.flightRoll() <= -4 && helicopter.engineRpm() <= 170.01F,
                "critical damage in flight should roll the aircraft and drain rotor speed");
        helicopter.ejectPassengers();
        helicopter.discard();

        HelicopterEntity landing = helper.spawn(VehicleRegistry.HELICOPTER.get(), POS.above());
        CompoundTag tilted = landing.saveWithoutId(new CompoundTag());
        tilted.putFloat("FlightRoll", 40);
        landing.load(tilted);
        landing.setDeltaMovement(0, -1, 0);
        AABB area = new AABB(helper.absolutePos(POS)).inflate(5);
        var before = helper.getLevel().getEntitiesOfClass(ItemEntity.class, area).stream().map(ItemEntity::getUUID).collect(Collectors.toSet());
        landing.tick();
        require(helper, landing.isRemoved(), "landing with excessive roll should destroy the helicopter");
        var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, area, item -> !before.contains(item.getUUID()));
        require(helper, drops.stream().filter(item -> item.getItem().is(VehicleRegistry.HELICOPTER_ITEM.get())).count() == 1,
                "a crash should leave exactly one recoverable helicopter item");
        drops.forEach(ItemEntity::discard);
        helper.succeed();
    }
    @GameTest(template = "jurassicraft:vehicle_test")
    public static void helicopterDownwashMovesNearbySmallEntities(GameTestHelper helper) {
        floor(helper);
        HelicopterEntity helicopter = helper.spawn(VehicleRegistry.HELICOPTER.get(), POS.above(2));
        Player pilot = helper.makeMockPlayer();
        require(helper, pilot.startRiding(helicopter), "pilot should mount");
        require(helper, helicopter.toggleFlightMode(pilot, HelicopterEntity.TOGGLE_AUTOPILOT), "pilot should select manual control");
        ItemEntity near = helper.spawnItem(Items.STICK, 10.5F, 1.1F, 7.5F);
        ItemEntity far = helper.spawnItem(Items.STICK, 13.5F, 1.1F, 7.5F);
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(4, 1, 7));
        near.setDeltaMovement(Vec3.ZERO); far.setDeltaMovement(Vec3.ZERO); pig.setDeltaMovement(Vec3.ZERO);
        helicopterInput(helicopter, pilot, 0, 1);
        require(helper, near.getDeltaMovement().lengthSqr() == 0, "a stopped rotor should not blow objects away");
        CompoundTag state = helicopter.saveWithoutId(new CompoundTag());
        state.putFloat("EngineRpm", 200);
        helicopter.load(state);
        helicopterInput(helicopter, pilot, 0, 1);
        require(helper, near.getDeltaMovement().x > 0 && far.getDeltaMovement().x > 0
                        && near.getDeltaMovement().x > far.getDeltaMovement().x,
                "rotor wash should push small objects outward, more strongly when nearer");
        require(helper, pig.getDeltaMovement().lengthSqr() == 0, "rotor wash should not move large animals");
        near.setDeltaMovement(Vec3.ZERO);
        helicopter.setPos(helicopter.getX(), helicopter.getY() + 10, helicopter.getZ());
        helicopterInput(helicopter, pilot, 0, 1);
        require(helper, near.getDeltaMovement().lengthSqr() == 0, "downwash near the ground should stop at high altitude");
        helicopter.ejectPassengers();
        helicopter.discard();
        near.discard(); far.discard(); pig.discard();
        helper.succeed();
    }
    @GameTest(template = "jurassicraft:vehicle_test")
    public static void suspensionSamplesWheelHeights(GameTestHelper helper) {
        floor(helper);
        JeepEntity jeep = helper.spawn(VehicleRegistry.JEEP.get(), POS);
        VehicleSuspension.Tilt flat = VehicleSuspension.sample(jeep);
        require(helper, Math.abs(flat.pitch()) < .1 && Math.abs(flat.roll()) < .1, "flat ground should leave the car level");
        helper.setBlock(new BlockPos(6, 1, 10), Blocks.STONE);
        helper.setBlock(new BlockPos(8, 1, 10), Blocks.STONE);
        VehicleSuspension.Tilt slope = VehicleSuspension.sample(jeep);
        require(helper, slope.pitch() > 10 && Math.abs(slope.roll()) < .1, "raised front wheels should pitch the car uphill");
        helper.setBlock(new BlockPos(6, 1, 10), Blocks.AIR);
        helper.setBlock(new BlockPos(8, 1, 6), Blocks.STONE);
        VehicleSuspension.Tilt bank = VehicleSuspension.sample(jeep);
        require(helper, bank.roll() > 15 && Math.abs(bank.pitch()) < .1, "raised right wheels should bank the car");
        jeep.discard();
        helper.succeed();
    }
    @GameTest(template = "jurassicraft:vehicle_test")
    public static void vehiclesClearFoliageOnlyWhenEnabled(GameTestHelper helper) {
        floor(helper);
        JeepEntity jeep = helper.spawn(VehicleRegistry.JEEP.get(), POS);
        BlockPos leaves = new BlockPos(7, 1, 8), vine = new BlockPos(8, 1, 8), stone = new BlockPos(6, 1, 8);
        helper.setBlock(leaves, Blocks.OAK_LEAVES);
        helper.setBlock(vine, Blocks.VINE.defaultBlockState().setValue(VineBlock.UP, true));
        helper.setBlock(stone, Blocks.STONE);
        VehicleObstructions.clear(jeep, false);
        require(helper, helper.getBlockState(leaves).is(Blocks.OAK_LEAVES) && helper.getBlockState(vine).is(Blocks.VINE),
                "disabled block destruction must leave foliage alone");
        AABB area = new AABB(helper.absolutePos(POS)).inflate(4);
        var before = helper.getLevel().getEntitiesOfClass(ItemEntity.class, area).stream().map(ItemEntity::getUUID).collect(Collectors.toSet());
        VehicleObstructions.clear(jeep, true);
        require(helper, helper.getBlockState(leaves).isAir() && helper.getBlockState(vine).isAir()
                        && helper.getBlockState(stone).is(Blocks.STONE),
                "enabled block destruction should remove leaves and vines but preserve other blocks");
        var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, area, item -> !before.contains(item.getUUID()));
        require(helper, drops.stream().noneMatch(item -> item.getItem().is(Blocks.OAK_LEAVES.asItem())),
                "leaves should not drop themselves");
        drops.forEach(ItemEntity::discard);
        jeep.discard();
        helper.succeed();
    }
    @GameTest(template = "jurassicraft:vehicle_test")
    public static void vehiclesTakeFallDamage(GameTestHelper helper) {
        floor(helper);
        JeepEntity jeep = helper.spawn(VehicleRegistry.JEEP.get(), POS);
        require(helper, !jeep.causeFallDamage(3, 1, DamageSource.FALL), "a short fall should not damage a vehicle");
        require(helper, jeep.causeFallDamage(7, 1, DamageSource.FALL)
                        && Math.abs(jeep.damageFraction() - .125F) < .001,
                "a longer fall should damage the vehicle based on distance");
        jeep.discard();
        HelicopterEntity helicopter = helper.spawn(VehicleRegistry.HELICOPTER.get(), POS.above(8));
        Player pilot = helper.makeMockPlayer();
        require(helper, pilot.startRiding(helicopter), "pilot should mount");
        require(helper, helicopter.causeFallDamage(10, 1, DamageSource.FALL),
                "a seated pilot must not prevent damage from an unpowered fall");
        helicopterInput(helicopter, pilot, VehicleEntity.UP, 1);
        require(helper, !helicopter.causeFallDamage(10, 1, DamageSource.FALL), "active flight should prevent fall damage");
        HelicopterEntity loaded = VehicleRegistry.HELICOPTER.get().create(helper.getLevel());
        require(helper, loaded != null, "helicopter should be restorable");
        loaded.load(helicopter.saveWithoutId(new CompoundTag()));
        require(helper, !loaded.causeFallDamage(10, 1, DamageSource.FALL), "active flight should survive saving");
        pilot.stopRiding();
        require(helper, !helicopter.causeFallDamage(10, 1, DamageSource.FALL),
                "flying state should last until landing if the pilot leaves");
        helicopter.setPos(helicopter.getX(), helper.absolutePos(POS).getY() + .25, helicopter.getZ());
        helicopter.setDeltaMovement(0, -1, 0);
        helicopter.tick();
        require(helper, helicopter.isOnGround() && helicopter.causeFallDamage(7, 1, DamageSource.FALL),
                "landing should end flight immunity");
        helicopter.discard(); loaded.discard();
        helper.succeed();
    }
    @GameTest(template = "jurassicraft:vehicle_test")
    public static void helicopterExplosionDoesNotDamageTerrain(GameTestHelper helper) {
        floor(helper);
        HelicopterEntity helicopter = helper.spawn(VehicleRegistry.HELICOPTER.get(), POS);
        BlockPos nearby = POS.offset(2, 0, 0);
        helper.setBlock(nearby, Blocks.STONE);
        AABB area = new AABB(helper.absolutePos(POS)).inflate(4);
        var before = helper.getLevel().getEntitiesOfClass(ItemEntity.class, area).stream().map(ItemEntity::getUUID).collect(Collectors.toSet());
        helicopter.hurt(DamageSource.GENERIC, 40);
        require(helper, helicopter.isRemoved() && helper.getBlockState(nearby).is(Blocks.STONE),
                "helicopter destruction should be visual and leave nearby blocks intact");
        var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, area, item -> !before.contains(item.getUUID()));
        require(helper, drops.stream().filter(item -> item.getItem().is(VehicleRegistry.HELICOPTER_ITEM.get())).count() == 1,
                "the destroyed helicopter should leave one vehicle item");
        drops.forEach(ItemEntity::discard);
        helper.succeed();
    }
    @GameTest(template = "jurassicraft:vehicle_test")
    public static void helicopterZoomRespectsWallsAndLimits(GameTestHelper helper) {
        require(helper, VehicleZoom.step(4, true, false) == 4 && VehicleZoom.step(20, false, true) == 20
                        && VehicleZoom.step(10, true, true) == 10 && VehicleZoom.step(10, false, true) == 11,
                "zoom keys must obey limits and cancel each other");
        Player viewer = helper.makeMockPlayer();
        Vec3 eye = helper.absoluteVec(new Vec3(7.5, 2.5, 7.5)), look = new Vec3(0, 0, 1);
        require(helper, VehicleZoom.distance(helper.getLevel(), viewer, eye, look, 6) == 6,
                "an unobstructed view should reach the requested distance");
        helper.setBlock(new BlockPos(7, 2, 4), Blocks.STONE);
        double clipped = VehicleZoom.distance(helper.getLevel(), viewer, eye, look, 6);
        require(helper, clipped > 1 && clipped < 4, "a wall should prevent the camera passing through it");
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:vehicle_test")
    public static void vehicleTurnsEachRiderOnce(GameTestHelper helper) {
        JeepEntity jeep = helper.spawn(VehicleRegistry.JEEP.get(), POS);
        jeep.setYRot(170);
        Player driver = helper.makeMockPlayer(), passenger = helper.makeMockPlayer();
        driver.setYRot(30); driver.setYHeadRot(15);
        passenger.setYRot(-45); passenger.setYHeadRot(-60);
        require(helper, driver.startRiding(jeep) && passenger.startRiding(jeep), "both players should mount");
        jeep.setYRot(-170);
        jeep.positionRider(driver); jeep.positionRider(passenger);
        require(helper, Math.abs(driver.getYRot() - 50) < .001 && Math.abs(driver.getYHeadRot() - 35) < .001
                        && Math.abs(passenger.getYRot() + 25) < .001 && Math.abs(passenger.getYHeadRot() + 40) < .001,
                "both riders should follow a turn across the yaw wrap while retaining their view offsets");
        jeep.positionRider(driver); jeep.positionRider(passenger);
        require(helper, Math.abs(driver.getYRot() - 50) < .001 && Math.abs(passenger.getYRot() + 25) < .001,
                "repositioning the riders twice must not apply a turn twice");
        driver.setYRot(62); driver.setYHeadRot(47); // The rider looks around independently.
        jeep.setYRot(-160);
        jeep.positionRider(driver); jeep.positionRider(passenger);
        require(helper, Math.abs(driver.getYRot() - 72) < .001 && Math.abs(driver.getYHeadRot() - 57) < .001
                        && Math.abs(passenger.getYRot() + 15) < .001,
                "the next turn must add to each rider's current view");
        passenger.stopRiding();
        jeep.setYRot(-120);
        Player latePassenger = helper.makeMockPlayer();
        latePassenger.setYRot(10); latePassenger.setYHeadRot(5);
        require(helper, latePassenger.startRiding(jeep), "another passenger should be able to mount");
        jeep.positionRider(latePassenger);
        require(helper, Math.abs(latePassenger.getYRot() - 10) < .001 && Math.abs(latePassenger.getYHeadRot() - 5) < .001,
                "a new rider must not inherit turns from before mounting");
        jeep.ejectPassengers();
        jeep.discard();
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:vehicle_test")
    public static void carCanClimbOriginalStepHeight(GameTestHelper helper) {
        floor(helper);
        for (int x = 5; x <= 9; x++) {
            helper.setBlock(new BlockPos(x, 1, 11), Blocks.STONE);
            helper.setBlock(new BlockPos(x, 2, 11), Blocks.STONE_SLAB);
        }
        JeepEntity jeep = helper.spawn(VehicleRegistry.JEEP.get(), POS);
        Player driver = helper.makeMockPlayer();
        require(helper, driver.startRiding(jeep) && jeep.acceptControls(driver, VehicleEntity.FORWARD),
                "driver should be able to drive toward the step");
        double ground = jeep.getY(), highest = ground;
        for (int i = 0; i < 20; i++) {
            jeep.tick();
            highest = Math.max(highest, jeep.getY());
        }
        require(helper, highest >= ground + 1.49,
                "the original 1.5-block step height should climb a block topped by a slab");
        jeep.ejectPassengers(); jeep.discard();

        for (int x = 5; x <= 9; x++) helper.setBlock(new BlockPos(x, 2, 11), Blocks.STONE);
        JeepEntity blocked = helper.spawn(VehicleRegistry.JEEP.get(), POS);
        Player otherDriver = helper.makeMockPlayer();
        require(helper, otherDriver.startRiding(blocked) && blocked.acceptControls(otherDriver, VehicleEntity.FORWARD),
                "second driver should be able to drive toward the wall");
        double wallStart = blocked.getY(), wallHighest = wallStart;
        for (int i = 0; i < 20; i++) {
            blocked.tick();
            wallHighest = Math.max(wallHighest, blocked.getY());
        }
        require(helper, wallHighest < wallStart + 1.0 && blocked.getZ() < helper.absolutePos(new BlockPos(7, 1, 11)).getZ(),
                "a two-block wall must still stop the vehicle");
        blocked.ejectPassengers(); blocked.discard();
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:vehicle_test")
    public static void earlyRadioPacketsWaitForEntityAndRespectWorldChanges(GameTestHelper helper) {
        RadioPendingQueue<Object> pending = new RadioPendingQueue<>();
        Object world = new Object();
        ItemStack disc = new ItemStack(Items.MUSIC_DISC_13);
        pending.offer(4, world, disc);
        disc.shrink(1);
        pending.tick(world, (id, record) -> false);
        require(helper, pending.size() == 1, "a radio packet must wait until its entity spawns");
        pending.tick(world, (id, record) -> id == 4 && record.is(Items.MUSIC_DISC_13));
        require(helper, pending.size() == 0, "a queued radio packet must retain and deliver its record");
        pending.offer(4, world, new ItemStack(Items.MUSIC_DISC_13));
        pending.offer(4, world, ItemStack.EMPTY);
        pending.tick(world, (id, record) -> record.isEmpty());
        require(helper, pending.size() == 0, "a later stop must replace an earlier queued record");
        pending.offer(4, world, new ItemStack(Items.MUSIC_DISC_13));
        pending.tick(new Object(), (id, record) -> { helper.fail("old world record must not play"); return true; });
        require(helper, pending.size() == 0, "world changes must discard queued radio packets");
        pending.offer(4, world, new ItemStack(Items.MUSIC_DISC_13));
        for (int i = 0; i < 41; i++) pending.tick(world, (id, record) -> false);
        require(helper, pending.size() == 0, "undeliverable radio packets must expire");
        helper.succeed();
    }
}
