package ports.gridwright.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.phys.AABB;
import ports.gridwright.item.AngelRingFlight;
import ports.gridwright.item.BagMenu;
import ports.gridwright.item.BagOfHoldingItem;
import ports.gridwright.item.BuildersWandItem;
import ports.gridwright.item.DestructionWandItem;
import ports.gridwright.item.GoldenLassoItem;
import ports.gridwright.item.PocketWorkbenchItem;
import ports.gridwright.item.WateringCanItem;
import ports.gridwright.registry.ModAutomation;
import ports.gridwright.registry.ModUtilities;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static ports.gridwright.gametest.GameTestSupport.Owner;
import static ports.gridwright.gametest.GameTestSupport.TEMPLATE;
import static ports.gridwright.gametest.GameTestSupport.check;
import static ports.gridwright.gametest.GameTestSupport.connectedPlayer;
import static ports.gridwright.gametest.GameTestSupport.grid;
import static ports.gridwright.gametest.GameTestSupport.platform;
import static ports.gridwright.gametest.GameTestSupport.snapshot;

/** Phase 2b items: Angel Ring, Golden Lasso, both wands, Watering Can, Bag of Holding, Pocket Workbench. */
public class UtilityItemGameTests {

    private static int count(Inventory inventory, net.minecraft.world.item.Item item) {
        int total = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(item)) total += inventory.getItem(slot).getCount();
        }
        return total;
    }

    // --- Angel Ring -------------------------------------------------------------------------------

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void angelRingFliesOnGridPower(GameTestHelper helper) {
        Owner owner = Owner.random("gwAR");
        ServerPlayer player = connectedPlayer(helper, owner);
        player.getInventory().add(new ItemStack(ModUtilities.ANGEL_RING.get()));
        AtomicBoolean powered = new AtomicBoolean();
        helper.onEachTick(() -> {
            if (powered.get()) grid(helper).addProduction(owner.gridKey(), "test", 64.0);
        });
        helper.startSequence()
                .thenExecute(() -> {
                    check(AngelRingFlight.update(player) == AngelRingFlight.State.UNPOWERED, "A ring on an empty grid must not grant flight");
                    check(!player.getAbilities().mayfly, "Unpowered ring granted flight");
                    powered.set(true);
                })
                .thenExecuteAfter(3, () -> {
                    check(AngelRingFlight.update(player) == AngelRingFlight.State.READY, "A powered ring should be ready");
                    check(player.getAbilities().mayfly && player.getTags().contains(AngelRingFlight.TAG), "Powered ring did not grant flight");
                    player.getAbilities().flying = true;
                    check(AngelRingFlight.update(player) == AngelRingFlight.State.FLYING, "Flying with the ring");
                })
                .thenExecuteAfter(1, () -> {
                    check(AngelRingFlight.update(player) == AngelRingFlight.State.FLYING, "Still flying while the grid pays");
                    check(snapshot(helper, owner.gridKey()).demand() >= AngelRingFlight.GP - 1e-6,
                            "Flying should draw " + AngelRingFlight.GP + " GP: " + snapshot(helper, owner.gridKey()));
                    powered.set(false);
                })
                .thenExecuteAfter(3, () -> {
                    check(AngelRingFlight.update(player) == AngelRingFlight.State.UNPOWERED, "Grid lost its power");
                    check(!player.getAbilities().mayfly && !player.getAbilities().flying, "Flight not revoked");
                    check(player.hasEffect(MobEffects.SLOW_FALLING), "A flyer grounded mid-air gets Slow Falling");
                    check(!player.getTags().contains(AngelRingFlight.TAG), "Ring tag left behind");
                    // Flight granted by something else is never taken.
                    player.getAbilities().mayfly = true;
                    powered.set(true);
                })
                .thenExecuteAfter(3, () -> {
                    check(AngelRingFlight.update(player) == AngelRingFlight.State.NONE, "Foreign flight is left alone");
                    player.getInventory().clearContent();
                    check(AngelRingFlight.update(player) == AngelRingFlight.State.NONE, "No ring");
                    check(player.getAbilities().mayfly, "Removing the ring took flight it never granted");
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE)
    public static void angelRingRevokedWhenRemoved(GameTestHelper helper) {
        Owner owner = Owner.random("gwAS");
        ServerPlayer player = connectedPlayer(helper, owner);
        player.getInventory().add(new ItemStack(ModUtilities.ANGEL_RING.get()));
        helper.onEachTick(() -> grid(helper).addProduction(owner.gridKey(), "test", 64.0));
        helper.startSequence()
                .thenExecuteAfter(3, () -> {
                    check(AngelRingFlight.update(player) == AngelRingFlight.State.READY, "Powered ring should be ready");
                    player.getInventory().clearContent();
                    check(AngelRingFlight.update(player) == AngelRingFlight.State.NONE, "Ring removed");
                    check(!player.getAbilities().mayfly, "Removing the ring must take its flight back");
                })
                .thenSucceed();
    }

    // --- Golden Lasso -----------------------------------------------------------------------------

    @GameTest(template = TEMPLATE)
    public static void lassoCatchesAndReleasesPassiveCreatures(GameTestHelper helper) {
        ServerPlayer player = connectedPlayer(helper, Owner.random("gwL"));
        ItemStack lasso = new ItemStack(ModUtilities.GOLDEN_LASSO.get());
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, 2, 1, 2);
        cow.setCustomName(net.minecraft.network.chat.Component.literal("Bessie"));
        UUID original = cow.getUUID();
        check(GoldenLassoItem.capture(lasso, player, cow), "The lasso should catch a cow");
        check(cow.isRemoved() && GoldenLassoItem.holdsMob(lasso), "The cow should be inside the lasso");
        Cow other = helper.spawnWithNoFreeWill(EntityType.COW, 5, 1, 5);
        check(!GoldenLassoItem.capture(lasso, player, other), "A full lasso holds only one creature");
        Entity released = GoldenLassoItem.release(lasso, helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 5)));
        check(released instanceof Cow, "Released " + released);
        check(!released.getUUID().equals(original), "A released creature gets a fresh UUID");
        check("Bessie".equals(released.getCustomName().getString()), "The creature keeps its data");
        check(!GoldenLassoItem.holdsMob(lasso) && !lasso.hasTag(), "The lasso is empty again");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void cursedLassoTakesHostilesButNotBosses(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = connectedPlayer(helper, Owner.random("gwCl"));
        ItemStack lasso = new ItemStack(ModAutomation.CURSED_LASSO.get());
        check(GoldenLassoItem.refusal(EntityType.WITHER.create(level), player, true) == GoldenLassoItem.Refusal.BOSS, "Wither");
        check(GoldenLassoItem.refusal(EntityType.ELDER_GUARDIAN.create(level), player, true) == GoldenLassoItem.Refusal.BOSS, "Elder guardian");
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 1, 3);
        zombie.setCustomName(net.minecraft.network.chat.Component.literal("Shambles"));
        check(GoldenLassoItem.capture(lasso, player, zombie), "The cursed lasso should take a zombie");
        check(zombie.isRemoved() && GoldenLassoItem.heldType(lasso).orElse(null) == EntityType.ZOMBIE, "Zombie not inside");
        Entity released = GoldenLassoItem.release(lasso, level, helper.absolutePos(new BlockPos(2, 1, 5)));
        check(released instanceof Zombie && "Shambles".equals(released.getCustomName().getString()), "Released " + released);
        released.discard();
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, 5, 1, 5);
        check(GoldenLassoItem.capture(lasso, player, cow), "The cursed lasso also takes passive creatures");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void lassoRefusesHostilesBossesPlayersAndPets(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Owner owner = Owner.random("gwM");
        ServerPlayer player = connectedPlayer(helper, owner);
        ItemStack lasso = new ItemStack(ModUtilities.GOLDEN_LASSO.get());
        Zombie zombie = EntityType.ZOMBIE.create(level);
        check(GoldenLassoItem.refusal(zombie, player) == GoldenLassoItem.Refusal.HOSTILE, "Zombie");
        check(GoldenLassoItem.refusal(EntityType.WITHER.create(level), player) == GoldenLassoItem.Refusal.BOSS, "Wither");
        check(GoldenLassoItem.refusal(EntityType.ENDER_DRAGON.create(level), player) == GoldenLassoItem.Refusal.BOSS, "Dragon");
        check(GoldenLassoItem.refusal(EntityType.ELDER_GUARDIAN.create(level), player) == GoldenLassoItem.Refusal.BOSS,
                "Elder guardian (#gridwright:lasso_immune)");
        check(GoldenLassoItem.refusal(connectedPlayer(helper, Owner.random("gwN")), player) == GoldenLassoItem.Refusal.NOT_A_CREATURE, "Player");
        Wolf pet = EntityType.WOLF.create(level);
        pet.setTame(true);
        pet.setOwnerUUID(UUID.randomUUID());
        check(GoldenLassoItem.refusal(pet, player) == GoldenLassoItem.Refusal.OWNED, "Someone else's pet");
        pet.setOwnerUUID(owner.id());
        check(GoldenLassoItem.refusal(pet, player) == null, "Your own pet may be caught");
        Zombie live = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 1, 3);
        check(!GoldenLassoItem.capture(lasso, player, live) && live.isAlive() && !live.isRemoved(), "A hostile creature must stay put");
        check(!GoldenLassoItem.holdsMob(lasso), "Nothing caught");
        live.discard();
        helper.succeed();
    }

    // --- Wands ------------------------------------------------------------------------------------

    private static void row(GameTestHelper helper, int z, int length, net.minecraft.world.level.block.Block block) {
        for (int x = 1; x <= length; x++) helper.setBlock(new BlockPos(x, 1, z), block);
    }

    @GameTest(template = TEMPLATE)
    public static void buildersWandExtendsAFace(GameTestHelper helper) {
        ServerPlayer player = connectedPlayer(helper, Owner.random("gwB"));
        player.getInventory().add(new ItemStack(Items.STONE, 64));
        row(helper, 2, 3, Blocks.STONE);
        int placed = BuildersWandItem.build(player, InteractionHand.MAIN_HAND, helper.absolutePos(new BlockPos(1, 1, 2)), Direction.UP, BuildersWandItem.LIMIT);
        check(placed == 3, "Placed " + placed);
        for (int x = 1; x <= 3; x++) helper.assertBlockPresent(Blocks.STONE, new BlockPos(x, 2, 2));
        check(count(player.getInventory(), Items.STONE) == 61, "Three stone should be used: " + count(player.getInventory(), Items.STONE));
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void buildersWandStopsAtItsLimitAndSupply(GameTestHelper helper) {
        ServerPlayer player = connectedPlayer(helper, Owner.random("gwC"));
        player.getInventory().add(new ItemStack(Items.STONE, 64));
        for (int z = 1; z <= 4; z++) row(helper, z, 4, Blocks.STONE);
        int placed = BuildersWandItem.build(player, InteractionHand.MAIN_HAND, helper.absolutePos(new BlockPos(2, 1, 2)), Direction.UP, BuildersWandItem.LIMIT);
        check(placed == BuildersWandItem.LIMIT, "A 4x4 face should take the limit of " + BuildersWandItem.LIMIT + ", placed " + placed);
        ServerPlayer poor = connectedPlayer(helper, Owner.random("gwD"));
        poor.getInventory().add(new ItemStack(Items.STONE, 2));
        int few = BuildersWandItem.build(poor, InteractionHand.MAIN_HAND, helper.absolutePos(new BlockPos(2, 1, 2)), Direction.DOWN, BuildersWandItem.LIMIT);
        check(few == 2, "A player with two stone places two: " + few);
        check(count(poor.getInventory(), Items.STONE) == 2 - few, "Inventory and placements disagree");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void buildersWandRespectsClaims(GameTestHelper helper) {
        ServerPlayer player = connectedPlayer(helper, Owner.random("gwE"));
        player.getInventory().add(new ItemStack(Items.STONE, 64));
        row(helper, 3, 3, Blocks.STONE);
        BlockPos claimed = helper.absolutePos(new BlockPos(2, 2, 3));
        platform().protect(claimed, true);
        try {
            int placed = BuildersWandItem.build(player, InteractionHand.MAIN_HAND, helper.absolutePos(new BlockPos(1, 1, 3)), Direction.UP, BuildersWandItem.LIMIT);
            check(placed == 2, "The claimed block must be skipped: placed " + placed);
            helper.assertBlockPresent(Blocks.AIR, new BlockPos(2, 2, 3));
            check(count(player.getInventory(), Items.STONE) == 62, "A refused placement must not use a block");
        } finally {
            platform().protect(claimed, false);
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void destructionWandBreaksAFace(GameTestHelper helper) {
        ServerPlayer player = connectedPlayer(helper, Owner.random("gwF"));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModUtilities.DESTRUCTION_WAND.get()));
        row(helper, 2, 3, Blocks.STONE);
        int broken = DestructionWandItem.destroy(player, helper.absolutePos(new BlockPos(1, 1, 2)), Direction.UP, DestructionWandItem.LIMIT);
        check(broken == 3, "Broke " + broken);
        for (int x = 1; x <= 3; x++) helper.assertBlockPresent(Blocks.AIR, new BlockPos(x, 1, 2));
        int cobble = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(new BlockPos(2, 1, 2))).inflate(3))
                .stream().filter(item -> item.getItem().is(Items.COBBLESTONE)).mapToInt(item -> item.getItem().getCount()).sum();
        check(cobble == 3, "Stone should drop cobblestone as if mined: " + cobble);
        row(helper, 5, 2, Blocks.OBSIDIAN);
        check(DestructionWandItem.destroy(player, helper.absolutePos(new BlockPos(1, 1, 5)), Direction.UP, DestructionWandItem.LIMIT) == 0,
                "Obsidian is too hard for the wand");
        helper.setBlock(new BlockPos(5, 1, 5), Blocks.CHEST);
        check(!DestructionWandItem.breakable(helper.getLevel(), helper.absolutePos(new BlockPos(5, 1, 5))), "Blocks with block entities are left alone");
        helper.killAllEntities();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void destructionWandRespectsClaims(GameTestHelper helper) {
        ServerPlayer player = connectedPlayer(helper, Owner.random("gwG"));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModUtilities.DESTRUCTION_WAND.get()));
        row(helper, 4, 3, Blocks.DIRT);
        BlockPos claimed = helper.absolutePos(new BlockPos(2, 1, 4));
        platform().protect(claimed, true);
        try {
            int broken = DestructionWandItem.destroy(player, helper.absolutePos(new BlockPos(1, 1, 4)), Direction.UP, DestructionWandItem.LIMIT);
            check(broken == 2, "The claimed block must survive: broke " + broken);
            helper.assertBlockPresent(Blocks.DIRT, new BlockPos(2, 1, 4));
        } finally {
            platform().protect(claimed, false);
        }
        helper.killAllEntities();
        helper.succeed();
    }

    // --- Watering Can -----------------------------------------------------------------------------

    @GameTest(template = TEMPLATE)
    public static void wateringCanWetsFarmlandAndPutsOutFire(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.FARMLAND);
        helper.setBlock(new BlockPos(3, 1, 3), Blocks.NETHERRACK);
        helper.setBlock(new BlockPos(3, 2, 3), Blocks.FIRE);
        check(helper.getBlockState(new BlockPos(2, 1, 2)).getValue(FarmBlock.MOISTURE) == 0, "Farmland starts dry");
        WateringCanItem.pour(level, helper.absolutePos(new BlockPos(2, 2, 2)), RandomSource.create(1L));
        check(helper.getBlockState(new BlockPos(2, 1, 2)).getValue(FarmBlock.MOISTURE) == FarmBlock.MAX_MOISTURE, "Farmland should be wet");
        helper.assertBlockPresent(Blocks.AIR, new BlockPos(3, 2, 3));
        check(WateringCanItem.plant(Blocks.WHEAT.defaultBlockState()) && WateringCanItem.plant(Blocks.OAK_SAPLING.defaultBlockState())
                && WateringCanItem.plant(Blocks.CACTUS.defaultBlockState()), "Crops, saplings and cacti are plants");
        check(!WateringCanItem.plant(Blocks.GRASS_BLOCK.defaultBlockState()) && !WateringCanItem.plant(Blocks.OAK_LEAVES.defaultBlockState())
                && !WateringCanItem.plant(Blocks.FARMLAND.defaultBlockState()), "Grass, leaves and farmland are not");
        ItemStack can = new ItemStack(ModUtilities.WATERING_CAN.get());
        WateringCanItem.setWater(can, WateringCanItem.CAPACITY + 50);
        check(WateringCanItem.water(can) == WateringCanItem.CAPACITY, "Water is capped at the can's capacity");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void wateringCanGrowsPlants(GameTestHelper helper) {
        // Cacti grow without light, so this works in either loader's arena.
        helper.setBlock(new BlockPos(4, 1, 4), Blocks.SAND);
        helper.setBlock(new BlockPos(4, 2, 4), Blocks.CACTUS);
        RandomSource random = RandomSource.create(42L);
        BlockPos center = helper.absolutePos(new BlockPos(4, 3, 4));
        int ticked = 0;
        for (int pulse = 0; pulse < 400; pulse++) ticked += WateringCanItem.pour(helper.getLevel(), center, random);
        check(ticked > 0, "No plant was ticked");
        check(helper.getBlockState(new BlockPos(4, 3, 4)).getBlock() instanceof CactusBlock, "The cactus should have grown a block taller");
        helper.succeed();
    }

    // --- Bag of Holding ---------------------------------------------------------------------------

    @GameTest(template = TEMPLATE)
    public static void bagOfHoldingStoresAndRefusesNesting(GameTestHelper helper) {
        ServerPlayer player = connectedPlayer(helper, Owner.random("gwH"));
        Inventory inventory = player.getInventory();
        ItemStack bag = new ItemStack(ModUtilities.BAG_OF_HOLDING.get());
        inventory.setItem(0, bag);
        inventory.setItem(5, new ItemStack(ModUtilities.BAG_OF_HOLDING.get()));
        inventory.setItem(6, new ItemStack(Items.SHULKER_BOX));
        inventory.setItem(7, new ItemStack(Items.DIAMOND, 7));
        check(!BagOfHoldingItem.canHold(bag) && !BagOfHoldingItem.canHold(new ItemStack(Items.SHULKER_BOX))
                && BagOfHoldingItem.canHold(new ItemStack(Items.STONE)), "Bags and shulker boxes never go in a bag");

        BagMenu menu = BagMenu.server(1, inventory, 0);
        int hotbar = BagOfHoldingItem.SIZE + 27;
        menu.getSlot(0).set(new ItemStack(Items.GOLD_INGOT, 5));
        check(BagOfHoldingItem.storedStacks(bag) == 1, "The bag item should hold the gold");
        check(!menu.getSlot(1).mayPlace(new ItemStack(ModUtilities.BAG_OF_HOLDING.get())), "A bag slot must refuse a bag");
        menu.quickMoveStack(player, hotbar + 5);
        menu.quickMoveStack(player, hotbar + 6);
        check(inventory.getItem(5).is(ModUtilities.BAG_OF_HOLDING.get()) && inventory.getItem(6).is(Items.SHULKER_BOX),
                "Shift-clicking a bag or shulker box must not move it into the bag");
        menu.quickMoveStack(player, hotbar + 7);
        check(inventory.getItem(7).isEmpty() && BagOfHoldingItem.storedStacks(bag) == 2, "Diamonds should shift-click into the bag");

        menu.clicked(hotbar, 0, ClickType.PICKUP, player);
        menu.clicked(hotbar, 0, ClickType.THROW, player);
        menu.clicked(3, 0, ClickType.SWAP, player);
        check(menu.getCarried().isEmpty() && inventory.getItem(0) == bag, "The open bag's own slot is locked");
        check(menu.stillValid(player), "The bag is still in place");
        inventory.setItem(0, ItemStack.EMPTY);
        check(!menu.stillValid(player), "The screen must close once the bag leaves its slot");

        inventory.setItem(0, bag);
        BagMenu reopened = BagMenu.server(2, inventory, 0);
        check(reopened.getSlot(0).getItem().is(Items.GOLD_INGOT) && reopened.getSlot(0).getItem().getCount() == 5,
                "Contents should survive reopening");
        bag.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        check(player.containerMenu instanceof BagMenu, "Using the bag should open it: " + player.containerMenu);
        helper.succeed();
    }

    // --- Pocket Workbench -------------------------------------------------------------------------

    @GameTest(template = TEMPLATE)
    public static void pocketWorkbenchCrafts(GameTestHelper helper) {
        ServerPlayer player = connectedPlayer(helper, Owner.random("gwW"));
        ItemStack workbench = new ItemStack(ModUtilities.POCKET_WORKBENCH.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, workbench);
        workbench.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        check(player.containerMenu instanceof PocketWorkbenchItem.Menu, "Using the workbench should open a crafting grid: " + player.containerMenu);
        PocketWorkbenchItem.Menu menu = (PocketWorkbenchItem.Menu) player.containerMenu;
        menu.getSlot(1).set(new ItemStack(Items.OAK_LOG));
        ItemStack result = menu.getSlot(0).getItem();
        check(result.is(Items.OAK_PLANKS) && result.getCount() == 4, "A log should make four planks: " + result);
        check(menu.stillValid(player), "Valid while the player has the workbench");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        check(!menu.stillValid(player), "Invalid once the workbench is gone");
        menu.removed(player);
        check(count(player.getInventory(), Items.OAK_LOG) == 1, "Closing the grid returns its items");
        helper.succeed();
    }
}
