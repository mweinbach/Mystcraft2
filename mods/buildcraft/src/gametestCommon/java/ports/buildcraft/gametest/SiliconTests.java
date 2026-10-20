package ports.buildcraft.gametest;

import static ports.buildcraft.gametest.BCTests.chest;
import static ports.buildcraft.gametest.BCTests.require;
import static ports.buildcraft.gametest.BCTests.tile;
import static ports.buildcraft.gametest.BuilderTests.dropped;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.lib.LongData;
import ports.buildcraft.lib.Power;
import ports.buildcraft.silicon.AdvancedCraftingTableBlockEntity;
import ports.buildcraft.silicon.AdvancedCraftingTableMenu;
import ports.buildcraft.silicon.AssemblyTableBlockEntity;
import ports.buildcraft.silicon.AssemblyTableMenu;
import ports.buildcraft.silicon.BCSilicon;
import ports.buildcraft.silicon.EnumAssemblyRecipeState;
import ports.buildcraft.silicon.IntegrationTableBlockEntity;
import ports.buildcraft.silicon.IntegrationTableMenu;
import ports.buildcraft.silicon.LaserBlock;
import ports.buildcraft.silicon.LaserBlockEntity;

/** Loader-neutral GameTest bodies for the laser and the assembly, advanced crafting and integration tables. */
public final class SiliconTests {
    private static final BlockPos LASER = new BlockPos(1, 2, 4);
    private static final BlockPos TABLE = new BlockPos(4, 2, 4);
    /** The player inventory comes first in every table menu. */
    private static final int MACHINE_SLOTS = 36;

    private SiliconTests() {}

    private static AssemblyTableBlockEntity assemblyTable(GameTestHelper helper, BlockPos pos, ItemStack... inputs) {
        helper.setBlock(pos, BCSilicon.ASSEMBLY_TABLE.get());
        AssemblyTableBlockEntity table = tile(helper, pos, AssemblyTableBlockEntity.class);
        for (int slot = 0; slot < inputs.length; slot++) table.setItem(slot, inputs[slot]);
        return table;
    }

    private static int droppedCount(GameTestHelper helper, BlockPos pos, Item item) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(pos)).inflate(1), entity -> entity.getItem().is(item))
                .stream().mapToInt(entity -> entity.getItem().getCount()).sum();
    }

    private static long expectedLaserPower(LaserBlockEntity laser) {
        long max = LaserBlockEntity.MAX_POWER_PER_TICK * (laser.battery().stored() + LaserBlockEntity.MAX_POWER_PER_TICK) / (laser.battery().capacity() / 2);
        return Math.min(max, LaserBlockEntity.MAX_POWER_PER_TICK);
    }

    /**
     * A laser beams power into a table that needs it within its six block square cone, only with a clear line
     * of sight: a stone in the way hides the table until it is removed, and putting it back makes the laser give
     * up the table when it next looks for one. Each tick it sends 4 MJ scaled by how full its 1024 MJ battery
     * is, past half full at most 4 MJ. A table beside the cone gets nothing. Clients get the target and average,
     * which picks the beam's colour band, and saving keeps the battery and target. A wrench turns the laser.
     */
    public static void laserPowersTablesInSight(GameTestHelper helper) {
        BlockPos stone = new BlockPos(3, 2, 4);
        helper.setBlock(LASER, BCSilicon.LASER.get().defaultBlockState().setValue(LaserBlock.FACING, Direction.EAST));
        helper.setBlock(stone, Blocks.STONE);
        LaserBlockEntity laser = tile(helper, LASER, LaserBlockEntity.class);
        laser.battery().addPower(64 * Power.MJ);
        AssemblyTableBlockEntity table = assemblyTable(helper, TABLE, new ItemStack(Items.REDSTONE));
        AssemblyTableBlockEntity aside = assemblyTable(helper, new BlockPos(2, 2, 6), new ItemStack(Items.REDSTONE));
        long[] before = new long[1];

        helper.runAfterDelay(1, () -> {
            require(helper, table.recipesStates().size() == 1 && aside.recipesStates().size() == 1, "a redstone dust lists the redstone chipset");
            table.toggle(0);
            aside.toggle(0);
        });
        helper.runAfterDelay(5, () -> {
            require(helper, table.getRequiredLaserPower() == 10_000 * Power.MJ, "a saved redstone chipset needs 10000 MJ, needs "
                    + table.getRequiredLaserPower());
            require(helper, laser.targetPos() == null && table.power() == 0 && laser.battery().stored() == 64 * Power.MJ,
                    "a stone between the laser and the table must hide it");
            helper.setBlock(stone, Blocks.AIR);
            before[0] = laser.battery().stored();
        });
        helper.runAfterDelay(6, () -> {
            long sent = 4 * Power.MJ * (64 + 4) / 512;
            require(helper, laser.targetPos() != null && laser.targetPos().equals(helper.absolutePos(TABLE)), "a cleared line of sight lets the laser target the table");
            require(helper, table.power() == sent && laser.battery().stored() == before[0] - sent,
                    "with 64 MJ stored the laser sends 4 MJ * 68/512 a tick, sent " + table.power());
            laser.battery().set(800 * Power.MJ);
            before[0] = table.power();
        });
        helper.runAfterDelay(7, () -> {
            require(helper, table.power() - before[0] == LaserBlockEntity.MAX_POWER_PER_TICK, "past half full the laser sends 4 MJ a tick, sent "
                    + (table.power() - before[0]));
            require(helper, expectedLaserPower(laser) == LaserBlockEntity.MAX_POWER_PER_TICK, "the formula caps at 4 MJ");
            require(helper, aside.power() == 0, "a table beside the cone gets no power");
            CompoundTag update = laser.getUpdateTag();
            require(helper, update.contains("target_pos") && update.getLong("average") > 0, "clients must receive the target and the average power");
            require(helper, LaserBlockEntity.renderBand(200_000) == -1 && LaserBlockEntity.renderBand(200_001) == 0
                    && LaserBlockEntity.renderBand(1_200_000) == 1 && LaserBlockEntity.renderBand(2_500_000) == 2
                    && LaserBlockEntity.renderBand(4_000_000) == 3, "the beam's colour band follows upstream's RenderLaser");

            CompoundTag saved = laser.saveWithoutMetadata();
            LaserBlockEntity loaded = new LaserBlockEntity(helper.absolutePos(LASER), helper.getBlockState(LASER));
            loaded.load(saved);
            require(helper, loaded.battery().stored() == laser.battery().stored() && helper.absolutePos(TABLE).equals(loaded.targetPos()),
                    "saving must keep the battery and the target");
            helper.setBlock(stone, Blocks.STONE);
        });
        helper.runAfterDelay(30, () -> {
            require(helper, laser.targetPos() == null, "a laser must give up a table it can no longer see");
            BlockState state = helper.getBlockState(LASER);
            BCSilicon.LASER.get().attemptRotation(helper.getLevel(), helper.absolutePos(LASER), state, Direction.UP);
            require(helper, helper.getBlockState(LASER).getValue(LaserBlock.FACING) == Direction.SOUTH, "a wrench turns the laser from east to south");
            helper.succeed();
        });
    }

    /**
     * Ingredients list every chipset they could make; left-clicking a listed recipe saves it. Saved recipes with
     * enough ingredients take turns, each made once its laser power is in, into a neighbouring inventory. A saved
     * recipe stays listed when its ingredients run out and unsaving it drops it. Excess power is handed back.
     * Shift-clicks never land in the display slots, automation reaches all twelve inputs, and the states and
     * power survive a save. A broken table drops its ingredients.
     */
    public static void assemblyTableMakesSavedChipsets(GameTestHelper helper) {
        ChestBlockEntity chest = chest(helper, TABLE.above());
        AssemblyTableBlockEntity table = assemblyTable(helper, TABLE, new ItemStack(Items.REDSTONE, 2), new ItemStack(Items.IRON_INGOT));
        Player player = helper.makeMockPlayer();
        AssemblyTableMenu menu = new AssemblyTableMenu(0, player.getInventory(), table);
        int display = MACHINE_SLOTS + AssemblyTableBlockEntity.SLOTS;

        helper.runAfterDelay(1, () -> {
            require(helper, table.recipesStates().size() == 2 && menu.state(0) == EnumAssemblyRecipeState.POSSIBLE
                    && menu.state(1) == EnumAssemblyRecipeState.POSSIBLE && menu.state(2) == null,
                    "redstone and an iron ingot list the iron and redstone chipsets");
            require(helper, menu.getSlot(display).getItem().is(BCSilicon.IRON_CHIPSET.get()) && menu.getSlot(display + 1).getItem().is(BCSilicon.REDSTONE_CHIPSET.get()),
                    "the display slots show the listed chipsets");
            require(helper, table.getTarget() == 0, "nothing is made until a recipe is saved");
            menu.clicked(display, 0, ClickType.PICKUP, player);
            menu.clicked(display + 1, 0, ClickType.PICKUP, player);
            require(helper, menu.getCarried().isEmpty(), "clicking a listed recipe picks nothing up");
        });
        helper.runAfterDelay(2, () -> {
            require(helper, menu.state(0) == EnumAssemblyRecipeState.SAVED_ENOUGH_ACTIVE && menu.state(1) == EnumAssemblyRecipeState.SAVED_ENOUGH,
                    "the first saved recipe with enough becomes active, got " + menu.state(0) + ", " + menu.state(1));
            require(helper, table.getTarget() == 20_000 * Power.MJ && menu.target() == table.getTarget(), "an iron chipset costs 20000 MJ");
            require(helper, table.receiveLaserPower(table.getRequiredLaserPower() + Power.MJ) == Power.MJ, "power beyond the target is handed back");
            LongData server = LongData.server(table::getTarget), client = LongData.client(1);
            for (int slot = 0; slot < server.getCount(); slot++) client.set(slot, (short) server.get(slot));
            require(helper, client.getLong(0) == 20_000 * Power.MJ, "the menu sends targets beyond 16 bits whole, got " + client.getLong(0));
        });
        helper.runAfterDelay(3, () -> {
            require(helper, chest.countItem(BCSilicon.IRON_CHIPSET.get()) == 1 && table.getItem(1).isEmpty() && table.getItem(0).getCount() == 1,
                    "an iron chipset goes into the chest, using an iron ingot and a redstone dust");
            require(helper, table.power() == 0 && table.getTarget() == 10_000 * Power.MJ, "the redstone chipset takes its turn next");
            table.receiveLaserPower(table.getRequiredLaserPower());
        });
        helper.runAfterDelay(4, () -> {
            require(helper, chest.countItem(BCSilicon.REDSTONE_CHIPSET.get()) == 1 && table.isEmpty(), "a redstone chipset uses the last dust");
        });
        helper.runAfterDelay(5, () -> {
            require(helper, menu.state(0) == EnumAssemblyRecipeState.SAVED && menu.state(1) == EnumAssemblyRecipeState.SAVED,
                    "saved recipes stay listed without ingredients");
            menu.clicked(display, 0, ClickType.PICKUP, player);
        });
        helper.runAfterDelay(6, () -> {
            require(helper, table.recipesStates().size() == 1, "an unsaved recipe without ingredients is dropped from the list");
            ItemStack redstone = new ItemStack(Items.REDSTONE, 5);
            player.getInventory().setItem(9, redstone);
            menu.quickMoveStack(player, 0);
            require(helper, table.getItem(0).getCount() == 5 && menu.getSlot(display).getItem().is(BCSilicon.REDSTONE_CHIPSET.get()),
                    "shift-clicking moves dust into the inputs, never into the display slots");
            for (Direction side : Direction.values()) {
                require(helper, table.getSlotsForFace(side).length == AssemblyTableBlockEntity.SLOTS
                        && table.canPlaceItemThroughFace(11, redstone, side) && table.canTakeItemThroughFace(0, redstone, side),
                        "automation reaches every input from " + side);
            }
            table.receiveLaserPower(Power.MJ);
            CompoundTag saved = table.saveWithoutMetadata();
            AssemblyTableBlockEntity loaded = new AssemblyTableBlockEntity(helper.absolutePos(TABLE), helper.getBlockState(TABLE));
            loaded.load(saved);
            require(helper, loaded.recipesStates().equals(table.recipesStates()) && loaded.power() == table.power()
                    && loaded.getItem(0).getCount() == 5, "saving must keep the recipe states, power and inputs");
            helper.setBlock(TABLE, Blocks.AIR);
            require(helper, dropped(helper, TABLE, new ItemStack(Items.REDSTONE, 5)), "a broken table drops its ingredients");
            helper.succeed();
        });
    }

    /**
     * Clicking the blueprint with a held item sets one copy and clicking empty-handed clears it. With exact
     * copies of every blueprint item among the materials the table wants 500 MJ, then crafts into its results,
     * returning remainders such as buckets to the materials. Automation fills the materials and empties the
     * results, never touching the blueprint, which neither drops nor survives as items.
     */
    public static void advancedCraftingTableCraftsBlueprint(GameTestHelper helper) {
        helper.setBlock(TABLE, BCSilicon.ADVANCED_CRAFTING_TABLE.get());
        AdvancedCraftingTableBlockEntity table = tile(helper, TABLE, AdvancedCraftingTableBlockEntity.class);
        Player player = helper.makeMockPlayer();
        AdvancedCraftingTableMenu menu = new AdvancedCraftingTableMenu(0, player.getInventory(), table);
        int materials = MACHINE_SLOTS, results = materials + AdvancedCraftingTableBlockEntity.MATERIALS_SLOTS;
        int blueprint = results + AdvancedCraftingTableBlockEntity.RESULTS_SLOTS, display = blueprint + AdvancedCraftingTableBlockEntity.BLUEPRINT_SLOTS;
        List<ItemStack> cake = List.of(new ItemStack(Items.MILK_BUCKET), new ItemStack(Items.MILK_BUCKET), new ItemStack(Items.MILK_BUCKET),
                new ItemStack(Items.SUGAR), new ItemStack(Items.EGG), new ItemStack(Items.SUGAR),
                new ItemStack(Items.WHEAT), new ItemStack(Items.WHEAT), new ItemStack(Items.WHEAT));
        for (int slot = 0; slot < cake.size(); slot++) {
            menu.setCarried(cake.get(slot).copy());
            menu.clicked(blueprint + slot, 0, ClickType.PICKUP, player);
        }
        require(helper, ItemStack.matches(menu.getCarried(), cake.get(8)) && table.getItem(AdvancedCraftingTableBlockEntity.BLUEPRINT_START + 4).is(Items.EGG),
                "clicking the blueprint sets a copy and keeps the held item");
        menu.setCarried(new ItemStack(Items.WHEAT, 16));
        menu.clicked(blueprint + 8, 0, ClickType.PICKUP, player);
        require(helper, table.getItem(AdvancedCraftingTableBlockEntity.BLUEPRINT_START + 8).getCount() == 1, "the blueprint holds single items");
        menu.setCarried(ItemStack.EMPTY);
        menu.clicked(blueprint, 0, ClickType.PICKUP, player);
        require(helper, table.getItem(AdvancedCraftingTableBlockEntity.BLUEPRINT_START).isEmpty(), "clicking empty-handed clears a blueprint slot");
        menu.setCarried(new ItemStack(Items.MILK_BUCKET));
        menu.clicked(blueprint, 0, ClickType.PICKUP, player);
        menu.setCarried(ItemStack.EMPTY);

        for (int slot = 0; slot < 3; slot++) table.setItem(AdvancedCraftingTableBlockEntity.MATERIALS_START + slot, new ItemStack(Items.MILK_BUCKET));
        table.setItem(AdvancedCraftingTableBlockEntity.MATERIALS_START + 3, new ItemStack(Items.SUGAR, 2));
        table.setItem(AdvancedCraftingTableBlockEntity.MATERIALS_START + 4, new ItemStack(Items.WHEAT, 2));
        table.setItem(AdvancedCraftingTableBlockEntity.MATERIALS_START + 5, new ItemStack(Items.EGG));

        helper.runAfterDelay(1, () -> {
            require(helper, table.assumedResult().is(Items.CAKE) && menu.getSlot(display).getItem().is(Items.CAKE), "the blueprint shows a cake");
            require(helper, table.getTarget() == 0 && table.power() == 0, "two wheat are not enough for the blueprint's three");
            table.setItem(AdvancedCraftingTableBlockEntity.MATERIALS_START + 6, new ItemStack(Items.WHEAT));
        });
        helper.runAfterDelay(2, () -> {
            require(helper, table.getTarget() == AdvancedCraftingTableBlockEntity.POWER_REQ, "with every item there the table wants 500 MJ");
            table.receiveLaserPower(AdvancedCraftingTableBlockEntity.POWER_REQ);
        });
        helper.runAfterDelay(3, () -> {
            require(helper, table.getItem(AdvancedCraftingTableBlockEntity.RESULTS_START).is(Items.CAKE) && table.power() == 0, "500 MJ crafts the cake");
            int buckets = 0;
            for (int slot = 0; slot < AdvancedCraftingTableBlockEntity.MATERIALS_SLOTS; slot++) {
                ItemStack stack = table.getItem(AdvancedCraftingTableBlockEntity.MATERIALS_START + slot);
                require(helper, stack.isEmpty() || stack.is(Items.BUCKET), "only buckets remain in the materials, found " + stack);
                buckets += stack.is(Items.BUCKET) ? stack.getCount() : 0;
            }
            require(helper, buckets == 3, "the milk buckets' remainders return to the materials, got " + buckets);
            require(helper, !menu.getSlot(results).mayPlace(new ItemStack(Items.CAKE)) && menu.getSlot(results).mayPickup(player)
                    && !menu.getSlot(blueprint + 1).mayPickup(player), "results can only be taken and blueprints never");

            ItemStack sugar = new ItemStack(Items.SUGAR);
            for (Direction side : Direction.values()) {
                int[] slots = table.getSlotsForFace(side);
                require(helper, slots.length == AdvancedCraftingTableBlockEntity.MATERIALS_SLOTS + AdvancedCraftingTableBlockEntity.RESULTS_SLOTS
                        && slots[0] == AdvancedCraftingTableBlockEntity.MATERIALS_START, "automation never reaches the blueprint from " + side);
                require(helper, table.canPlaceItemThroughFace(AdvancedCraftingTableBlockEntity.MATERIALS_START, sugar, side)
                        && !table.canPlaceItemThroughFace(AdvancedCraftingTableBlockEntity.RESULTS_START, sugar, side)
                        && table.canTakeItemThroughFace(AdvancedCraftingTableBlockEntity.RESULTS_START, sugar, side)
                        && !table.canTakeItemThroughFace(AdvancedCraftingTableBlockEntity.MATERIALS_START, sugar, side),
                        "automation fills the materials and empties the results from " + side);
            }
            CompoundTag saved = table.saveWithoutMetadata();
            AdvancedCraftingTableBlockEntity loaded = new AdvancedCraftingTableBlockEntity(helper.absolutePos(TABLE), helper.getBlockState(TABLE));
            loaded.load(saved);
            require(helper, loaded.getItem(AdvancedCraftingTableBlockEntity.BLUEPRINT_START + 4).is(Items.EGG)
                    && loaded.getItem(AdvancedCraftingTableBlockEntity.RESULTS_START).is(Items.CAKE), "saving must keep the blueprint and results");
            helper.setBlock(TABLE, Blocks.AIR);
            require(helper, dropped(helper, TABLE, new ItemStack(Items.CAKE)) && dropped(helper, TABLE, new ItemStack(Items.BUCKET, 3))
                    && droppedCount(helper, TABLE, Items.WHEAT) == 0 && droppedCount(helper, TABLE, Items.MILK_BUCKET) == 0, "a broken table drops its materials and results, but not its blueprint");
            helper.succeed();
        });
    }

    /**
     * With the test datapack's recipe, an iron ingot in the centre and exactly a redstone dust and a gold nugget
     * around it show a clock and want 100 MJ; anything else around it shows nothing. The clock goes into the
     * result slot, which automation can only take from. The table's item says no recipes use it.
     */
    public static void integrationTableIntegratesAroundCentre(GameTestHelper helper) {
        helper.setBlock(TABLE, BCSilicon.INTEGRATION_TABLE.get());
        IntegrationTableBlockEntity table = tile(helper, TABLE, IntegrationTableBlockEntity.class);
        Player player = helper.makeMockPlayer();
        IntegrationTableMenu menu = new IntegrationTableMenu(0, player.getInventory(), table);
        int display = MACHINE_SLOTS + 9, result = display + 1;
        table.setItem(IntegrationTableBlockEntity.TARGET_SLOT, new ItemStack(Items.IRON_INGOT, 2));
        table.setItem(IntegrationTableBlockEntity.TO_INTEGRATE_START, new ItemStack(Items.REDSTONE, 2));
        table.setItem(IntegrationTableBlockEntity.TO_INTEGRATE_START + 3, new ItemStack(Items.GOLD_NUGGET));
        table.setItem(IntegrationTableBlockEntity.TO_INTEGRATE_START + 5, new ItemStack(Items.STICK));

        helper.runAfterDelay(1, () -> {
            require(helper, table.getOutput().isEmpty() && table.getTarget() == 0, "a stick around the centre matches no recipe");
            table.setItem(IntegrationTableBlockEntity.TO_INTEGRATE_START + 5, ItemStack.EMPTY);
        });
        helper.runAfterDelay(2, () -> {
            require(helper, table.getOutput().is(Items.CLOCK) && menu.getSlot(display).getItem().is(Items.CLOCK), "the display shows the clock");
            require(helper, table.getTarget() == 100 * Power.MJ, "the recipe costs 100 MJ, costs " + table.getTarget());
            require(helper, menu.getSlot(MACHINE_SLOTS + 4).getContainerSlot() == IntegrationTableBlockEntity.TARGET_SLOT
                    && menu.getSlot(MACHINE_SLOTS).getContainerSlot() == IntegrationTableBlockEntity.TO_INTEGRATE_START, "the centre slot sits in the middle of the ring");
            table.receiveLaserPower(table.getRequiredLaserPower());
        });
        helper.runAfterDelay(3, () -> {
            require(helper, table.getItem(IntegrationTableBlockEntity.RESULT_SLOT).is(Items.CLOCK) && table.power() == 0, "100 MJ makes the clock");
            require(helper, table.getItem(IntegrationTableBlockEntity.TARGET_SLOT).getCount() == 1
                    && table.getItem(IntegrationTableBlockEntity.TO_INTEGRATE_START).getCount() == 1
                    && table.getItem(IntegrationTableBlockEntity.TO_INTEGRATE_START + 3).isEmpty(), "one of each ingredient is used");
            require(helper, table.getOutput().isEmpty(), "without a gold nugget nothing more can be made");
            ItemStack clock = new ItemStack(Items.CLOCK);
            for (Direction side : Direction.values()) {
                require(helper, !table.canPlaceItemThroughFace(IntegrationTableBlockEntity.RESULT_SLOT, clock, side)
                        && table.canTakeItemThroughFace(IntegrationTableBlockEntity.RESULT_SLOT, clock, side)
                        && table.canPlaceItemThroughFace(IntegrationTableBlockEntity.TARGET_SLOT, clock, side),
                        "automation takes results and fills the other slots from " + side);
            }
            require(helper, !menu.getSlot(result).mayPlace(clock) && !menu.getSlot(display).mayPickup(player), "results can only be taken");
            List<Component> tooltip = new ArrayList<>();
            BCSilicon.INTEGRATION_TABLE.get().appendHoverText(new ItemStack(BCSilicon.INTEGRATION_TABLE_ITEM.get()), helper.getLevel(), tooltip, TooltipFlag.Default.NORMAL);
            require(helper, tooltip.size() == 1 && tooltip.get(0).getContents() instanceof TranslatableContents tip
                    && tip.getKey().equals("block.buildcraft.integration_table.tip"), "the integration table's item carries upstream's tip");
            require(helper, helper.getLevel().getRecipeManager().byKey(BuildCraft.id("gametest_integration")).isPresent(), "the test recipe is loaded");
            helper.succeed();
        });
    }
}
