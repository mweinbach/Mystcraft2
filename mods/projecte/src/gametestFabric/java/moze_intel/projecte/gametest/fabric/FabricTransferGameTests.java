/*
 * ProjectE 1.19.2 port - in-world tests, Fabric side.
 * Copyright (c) 2026 ProjectE 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package moze_intel.projecte.gametest.fabric;

import moze_intel.projecte.gameObjs.block_entities.CondenserBlockEntity;
import moze_intel.projecte.gameObjs.registries.PEBlocks;
import moze_intel.projecte.gametest.PEGameTestSupport;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
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
}
