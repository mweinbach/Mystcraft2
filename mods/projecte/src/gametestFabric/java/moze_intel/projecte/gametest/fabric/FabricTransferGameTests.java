/*
 * ProjectE 1.19.2 port - in-world tests, Fabric side.
 * Copyright (c) 2026 ProjectE 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package moze_intel.projecte.gametest.fabric;

import moze_intel.projecte.gameObjs.block_entities.CondenserBlockEntity;
import moze_intel.projecte.gameObjs.block_entities.DMFurnaceBlockEntity;
import moze_intel.projecte.gameObjs.registries.PEBlocks;
import moze_intel.projecte.gametest.PEGameTestSupport;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * What other Fabric mods see: ProjectE's inventories through Fabric's Transfer API, which hoppers, pipes and
 * Tekxit's storage mods use instead of Forge's item handler capability.
 */
public class FabricTransferGameTests {

    private static final BlockPos FIRST = new BlockPos(1, 1, 1);

    @GameTest(template = PEGameTestSupport.TEMPLATE)
    public static void alchemicalChestIsAFabricItemStorage(GameTestHelper helper) {
        helper.setBlock(FIRST, PEBlocks.ALCHEMICAL_CHEST.getBlock());
        BlockPos pos = helper.absolutePos(FIRST);
        Storage<ItemVariant> storage = ItemStorage.SIDED.find(helper.getLevel(), pos, Direction.UP);
        if (storage == null) {
            helper.fail("The alchemical chest exposes no Fabric item storage");
            return;
        }
        try (Transaction transaction = Transaction.openOuter()) {
            long inserted = storage.insert(ItemVariant.of(Items.DIAMOND), 70, transaction);
            transaction.commit();
            if (inserted != 70) {
                helper.fail("Inserted " + inserted + " of 70 diamonds");
            }
        }
        try (Transaction transaction = Transaction.openOuter()) {
            long extracted = storage.extract(ItemVariant.of(Items.DIAMOND), 70, transaction);
            transaction.commit();
            if (extracted != 70) {
                helper.fail("Extracted " + extracted + " of 70 diamonds");
            }
        }
        helper.succeed();
    }

    /** A hopper-style extractor pulls the condensed items, and only those, out of a condenser. */
    @GameTest(template = PEGameTestSupport.TEMPLATE, timeoutTicks = 200)
    public static void condenserOutputIsExtractable(GameTestHelper helper) {
        helper.setBlock(FIRST, PEBlocks.CONDENSER.getBlock());
        CondenserBlockEntity condenser = (CondenserBlockEntity) helper.getBlockEntity(FIRST);
        ServerPlayer player = PEGameTestSupport.player(helper, "fabric_condenser");
        player.containerMenu.setCarried(new ItemStack(Items.COBBLESTONE));
        condenser.attemptCondenserSet(player);
        player.containerMenu.setCarried(ItemStack.EMPTY);
        Storage<ItemVariant> storage = ItemStorage.SIDED.find(helper.getLevel(), helper.absolutePos(FIRST), Direction.DOWN);
        if (storage == null) {
            helper.fail("The condenser exposes no Fabric item storage");
            return;
        }
        try (Transaction transaction = Transaction.openOuter()) {
            storage.insert(ItemVariant.of(Items.DIRT), 3, transaction);
            transaction.commit();
        }
        helper.succeedWhen(() -> {
            try (Transaction transaction = Transaction.openOuter()) {
                long dirt = storage.extract(ItemVariant.of(Items.DIRT), 64, transaction);
                long cobblestone = storage.extract(ItemVariant.of(Items.COBBLESTONE), 64, transaction);
                PEGameTestSupport.require(dirt == 0, "Unprocessed dirt could be extracted");
                PEGameTestSupport.require(cobblestone == 3, "Extracted " + cobblestone + " of 3 condensed cobblestone");
            }
        });
    }

    /**
     * Two lookups of the same chest are two storage objects. Extracting its last item through both in one transaction
     * moves it once: the second view sees the first view's move, and the chest's slots stay the only record.
     */
    @GameTest(template = PEGameTestSupport.TEMPLATE)
    public static void twoLookupsCannotExtractTheSameItemTwice(GameTestHelper helper) {
        helper.setBlock(FIRST, PEBlocks.ALCHEMICAL_CHEST.getBlock());
        BlockPos pos = helper.absolutePos(FIRST);
        Storage<ItemVariant> first = ItemStorage.SIDED.find(helper.getLevel(), pos, Direction.UP);
        Storage<ItemVariant> second = ItemStorage.SIDED.find(helper.getLevel(), pos, Direction.UP);
        Storage<ItemVariant> otherFace = ItemStorage.SIDED.find(helper.getLevel(), pos, Direction.NORTH);
        PEGameTestSupport.check(helper, first != null && second != null && otherFace != null, "The chest exposes no Fabric item storage");
        insertIntoLastSlot(helper, first, ItemVariant.of(Items.DIAMOND), 1);

        long moved;
        try (Transaction transaction = Transaction.openOuter()) {
            moved = first.extract(ItemVariant.of(Items.DIAMOND), 1, transaction)
                    + second.extract(ItemVariant.of(Items.DIAMOND), 1, transaction)
                    + otherFace.extract(ItemVariant.of(Items.DIAMOND), 1, transaction);
            transaction.commit();
        }
        PEGameTestSupport.check(helper, moved == 1, "Three views extracted " + moved + " diamonds from a chest holding 1");
        long left = count(ItemStorage.SIDED.find(helper.getLevel(), pos, Direction.UP), Items.DIAMOND);
        PEGameTestSupport.check(helper, left == 0, "The chest still holds " + left + " diamonds after giving its only one away");
        helper.succeed();
    }

    /**
     * An aborted transaction puts every slot back, including changes made through several views and committed nested
     * transactions: nothing is created or lost.
     */
    @GameTest(template = PEGameTestSupport.TEMPLATE)
    public static void abortedTransactionsRestoreTheChest(GameTestHelper helper) {
        helper.setBlock(FIRST, PEBlocks.ALCHEMICAL_CHEST.getBlock());
        BlockPos pos = helper.absolutePos(FIRST);
        Storage<ItemVariant> first = ItemStorage.SIDED.find(helper.getLevel(), pos, Direction.UP);
        Storage<ItemVariant> second = ItemStorage.SIDED.find(helper.getLevel(), pos, Direction.EAST);
        PEGameTestSupport.check(helper, first != null && second != null, "The chest exposes no Fabric item storage");
        try (Transaction transaction = Transaction.openOuter()) {
            first.insert(ItemVariant.of(Items.DIAMOND), 5, transaction);
            transaction.commit();
        }
        try (Transaction outer = Transaction.openOuter()) {
            long moved;
            try (Transaction nested = outer.openNested()) {
                moved = first.extract(ItemVariant.of(Items.DIAMOND), 2, nested);
                nested.commit();
            }
            moved += second.extract(ItemVariant.of(Items.DIAMOND), 64, outer);
            PEGameTestSupport.check(helper, moved == 5, "Extracted " + moved + " of 5 diamonds through two views");
            try (Transaction nested = outer.openNested()) {
                second.insert(ItemVariant.of(Items.DIAMOND), 1, nested);
                first.insert(ItemVariant.of(Items.DIRT), 7, nested);
                // nested aborts here
            }
            PEGameTestSupport.check(helper, count(first, Items.DIRT) == 0 && count(second, Items.DIAMOND) == 0, "An aborted nested transaction left items behind");
            // outer aborts here
        }
        long diamonds = count(first, Items.DIAMOND);
        PEGameTestSupport.check(helper, diamonds == 5 && count(first, Items.DIRT) == 0, "After aborting, the chest holds " + diamonds + " diamonds, not 5");
        helper.succeed();
    }

    /**
     * The Dark Matter furnace's bottom face and its sides are different handlers over the same output slots. Taking
     * the one ingot through both in one transaction moves it once.
     */
    @GameTest(template = PEGameTestSupport.TEMPLATE)
    public static void furnaceFacesSharingSlotsCannotDoubleExtract(GameTestHelper helper) {
        helper.setBlock(FIRST, PEBlocks.DARK_MATTER_FURNACE.getBlock());
        DMFurnaceBlockEntity furnace = (DMFurnaceBlockEntity) helper.getBlockEntity(FIRST);
        furnace.getOutput().insertItem(0, new ItemStack(Items.IRON_INGOT), false);
        BlockPos pos = helper.absolutePos(FIRST);
        Storage<ItemVariant> bottom = ItemStorage.SIDED.find(helper.getLevel(), pos, Direction.DOWN);
        Storage<ItemVariant> side = ItemStorage.SIDED.find(helper.getLevel(), pos, Direction.NORTH);
        PEGameTestSupport.check(helper, bottom != null && side != null, "The furnace exposes no Fabric item storage");
        long moved;
        try (Transaction transaction = Transaction.openOuter()) {
            moved = bottom.extract(ItemVariant.of(Items.IRON_INGOT), 1, transaction) + side.extract(ItemVariant.of(Items.IRON_INGOT), 1, transaction);
            transaction.commit();
        }
        PEGameTestSupport.check(helper, moved == 1, "Two faces extracted " + moved + " ingots from an output holding 1");
        PEGameTestSupport.check(helper, furnace.getOutput().getStackInSlot(0).isEmpty(), "The output still holds " + furnace.getOutput().getStackInSlot(0));
        helper.succeed();
    }

    private static void insertIntoLastSlot(GameTestHelper helper, Storage<ItemVariant> storage, ItemVariant resource, long amount) {
        StorageView<ItemVariant> last = null;
        for (StorageView<ItemVariant> view : storage) {
            last = view;
        }
        if (!(last instanceof SingleSlotStorage<ItemVariant> slot)) {
            helper.fail("The chest's storage has no slots");
            return;
        }
        try (Transaction transaction = Transaction.openOuter()) {
            PEGameTestSupport.check(helper, slot.insert(resource, amount, transaction) == amount, "Could not fill the chest's last slot");
            transaction.commit();
        }
    }

    private static long count(Storage<ItemVariant> storage, Item item) {
        long total = 0;
        for (StorageView<ItemVariant> view : storage) {
            if (!view.isResourceBlank() && view.getResource().isOf(item)) {
                total += view.getAmount();
            }
        }
        return total;
    }
}
