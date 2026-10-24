/*
 * ProjectE 1.19.2 port - in-world tests shared by the Forge and Fabric builds.
 * Copyright (c) 2026 ProjectE 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package moze_intel.projecte.gametest;

import static moze_intel.projecte.gametest.PEGameTestSupport.TEMPLATE;
import static moze_intel.projecte.gametest.PEGameTestSupport.check;

import moze_intel.projecte.api.capabilities.PECapabilities;
import moze_intel.projecte.api.capabilities.item.IItemEmcHolder;
import moze_intel.projecte.gameObjs.block_entities.AlchBlockEntityChest;
import moze_intel.projecte.gameObjs.block_entities.CollectorMK1BlockEntity;
import moze_intel.projecte.gameObjs.block_entities.CondenserBlockEntity;
import moze_intel.projecte.gameObjs.block_entities.RelayMK1BlockEntity;
import moze_intel.projecte.gameObjs.registries.PEBlocks;
import moze_intel.projecte.gameObjs.registries.PEItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

/** Energy collectors, antimatter relays, energy condensers and the alchemical chest. */
public class EmcBlockGameTests {

    private static final BlockPos FIRST = new BlockPos(1, 1, 1);
    private static final BlockPos SECOND = new BlockPos(2, 1, 1);

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void collectorGeneratesEmc(GameTestHelper helper) {
        helper.setBlock(FIRST, PEBlocks.COLLECTOR.getBlock());
        CollectorMK1BlockEntity collector = blockEntity(helper, FIRST, CollectorMK1BlockEntity.class);
        helper.succeedWhen(() -> PEGameTestSupport.require(collector.getStoredEmc() > 0, "The collector has generated no EMC"));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void condenserTurnsItsInputIntoItsLock(GameTestHelper helper) {
        CondenserBlockEntity condenser = lockedCondenser(helper, FIRST, Items.COBBLESTONE, "condense");
        IItemHandler handler = itemHandler(condenser);
        ItemStack rest = ItemHandlerHelper.insertItemStacked(handler, new ItemStack(Items.DIRT, 4), false);
        check(helper, rest.isEmpty(), "The condenser refused dirt: " + rest);
        helper.succeedWhen(() -> {
            PEGameTestSupport.require(count(condenser.getOutput(), Items.COBBLESTONE) == 4, "Condensed cobblestone: " + count(condenser.getOutput(), Items.COBBLESTONE));
            PEGameTestSupport.require(count(condenser.getInput(), Items.DIRT) == 0, "Dirt left in the condenser");
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void relayBurnsItemsAndFeedsANeighbour(GameTestHelper helper) {
        helper.setBlock(FIRST, PEBlocks.RELAY.getBlock());
        RelayMK1BlockEntity relay = blockEntity(helper, FIRST, RelayMK1BlockEntity.class);
        CondenserBlockEntity condenser = lockedCondenser(helper, SECOND, Items.COBBLESTONE, "relay");
        ItemStack rest = ItemHandlerHelper.insertItemStacked(relay.getInput(), new ItemStack(Items.IRON_INGOT), false);
        check(helper, rest.isEmpty(), "The relay refused iron: " + rest);
        helper.succeedWhen(() -> PEGameTestSupport.require(count(condenser.getOutput(), Items.COBBLESTONE) >= 64,
              "The relay sent too little EMC: " + count(condenser.getOutput(), Items.COBBLESTONE) + " cobblestone condensed"));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void relayChargesAKleinStar(GameTestHelper helper) {
        helper.setBlock(FIRST, PEBlocks.RELAY.getBlock());
        RelayMK1BlockEntity relay = blockEntity(helper, FIRST, RelayMK1BlockEntity.class);
        ItemStack star = new ItemStack(PEItems.KLEIN_STAR_EIN.get());
        check(helper, relay.getOutput().insertItem(0, star, false).isEmpty(), "The relay refused a Klein Star");
        ItemHandlerHelper.insertItemStacked(relay.getInput(), new ItemStack(Items.DIAMOND), false);
        helper.succeedWhen(() -> {
            ItemStack charging = relay.getOutput().getStackInSlot(0);
            IItemEmcHolder holder = charging.getCapability(PECapabilities.EMC_HOLDER_ITEM_CAPABILITY).orElse(null);
            PEGameTestSupport.require(holder != null, "The Klein Star has no EMC holder");
            PEGameTestSupport.require(holder.getStoredEmc(charging) > 0, "The Klein Star was not charged");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void alchemicalChestHoldsItemsThroughItsItemHandler(GameTestHelper helper) {
        helper.setBlock(FIRST, PEBlocks.ALCHEMICAL_CHEST.getBlock());
        AlchBlockEntityChest chest = blockEntity(helper, FIRST, AlchBlockEntityChest.class);
        IItemHandler handler = itemHandler(chest);
        check(helper, handler.getSlots() == 104, "The alchemical chest has " + handler.getSlots() + " slots, not 104");
        check(helper, ItemHandlerHelper.insertItemStacked(handler, new ItemStack(Items.COBBLESTONE, 100), false).isEmpty(), "The chest refused cobblestone");
        check(helper, count(handler, Items.COBBLESTONE) == 100, "The chest holds " + count(handler, Items.COBBLESTONE) + " cobblestone");
        ItemStack taken = handler.extractItem(0, 64, false);
        check(helper, taken.is(Items.COBBLESTONE) && taken.getCount() == 64, "Extracted " + taken);
        helper.succeed();
    }

    private static CondenserBlockEntity lockedCondenser(GameTestHelper helper, BlockPos pos, Item lock, String name) {
        helper.setBlock(pos, PEBlocks.CONDENSER.getBlock());
        CondenserBlockEntity condenser = blockEntity(helper, pos, CondenserBlockEntity.class);
        ServerPlayer player = PEGameTestSupport.player(helper, name);
        player.containerMenu.setCarried(new ItemStack(lock));
        check(helper, condenser.attemptCondenserSet(player), "The condenser lock was not set");
        player.containerMenu.setCarried(ItemStack.EMPTY);
        return condenser;
    }

    private static IItemHandler itemHandler(net.minecraft.world.level.block.entity.BlockEntity blockEntity) {
        return blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, null)
              .orElseThrow(() -> new IllegalStateException(blockEntity + " exposes no item handler"));
    }

    private static int count(IItemHandler handler, Item item) {
        int count = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    static <T> T blockEntity(GameTestHelper helper, BlockPos pos, Class<T> type) {
        Object blockEntity = helper.getBlockEntity(pos);
        if (!type.isInstance(blockEntity)) {
            throw new IllegalStateException("Expected a " + type.getSimpleName() + " at " + pos + ", found " + blockEntity);
        }
        return type.cast(blockEntity);
    }
}
