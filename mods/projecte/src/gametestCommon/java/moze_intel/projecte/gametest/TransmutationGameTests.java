/*
 * ProjectE 1.19.2 port - in-world tests shared by the Forge and Fabric builds.
 * Copyright (c) 2026 ProjectE 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package moze_intel.projecte.gametest;

import static moze_intel.projecte.gametest.PEGameTestSupport.TEMPLATE;
import static moze_intel.projecte.gametest.PEGameTestSupport.check;

import java.math.BigInteger;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.gameObjs.container.TransmutationContainer;
import moze_intel.projecte.gameObjs.container.inventory.TransmutationInventory;
import moze_intel.projecte.gameObjs.registries.PEItems;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The transmutation table's server side, driven through its menu as a player's clicks would: burning items teaches
 * them and adds their EMC, taking an output costs its EMC, and unlearning forgets the item.
 */
public class TransmutationGameTests {

    /** Slot indices in {@link TransmutationContainer}: the burn slot, the unlearn slot and the first output. */
    private static final int CONSUME_SLOT = 9;
    private static final int UNLEARN_SLOT = 10;
    private static final int FIRST_OUTPUT_SLOT = 11;

    @GameTest(template = TEMPLATE)
    public static void burningAnItemLearnsItAndAddsItsEmc(GameTestHelper helper) {
        ServerPlayer player = PEGameTestSupport.player(helper, "burn");
        IKnowledgeProvider knowledge = PEGameTestSupport.knowledge(player);
        TransmutationContainer menu = new TransmutationContainer(1, player.getInventory());
        menu.getSlot(CONSUME_SLOT).set(new ItemStack(Items.IRON_INGOT, 3));
        check(helper, knowledge.hasKnowledge(new ItemStack(Items.IRON_INGOT)), "Burning iron did not teach it");
        check(helper, knowledge.getEmc().equals(BigInteger.valueOf(3 * 256)), "Burning 3 iron gave " + knowledge.getEmc() + " EMC, not 768");
        menu.getSlot(CONSUME_SLOT).set(new ItemStack(Items.DIAMOND));
        check(helper, knowledge.getEmc().equals(BigInteger.valueOf(3 * 256 + 8_192)), "Burning a diamond added the wrong EMC: " + knowledge.getEmc());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void withdrawingAnItemCostsItsEmc(GameTestHelper helper) {
        ServerPlayer player = PEGameTestSupport.player(helper, "withdraw");
        IKnowledgeProvider knowledge = PEGameTestSupport.knowledge(player);
        knowledge.addKnowledge(new ItemStack(Items.GOLD_INGOT));
        knowledge.setEmc(BigInteger.valueOf(5_000));
        TransmutationContainer menu = new TransmutationContainer(1, player.getInventory());
        TransmutationInventory inventory = menu.transmutationInventory;
        inventory.writeIntoOutputSlot(0, new ItemStack(Items.GOLD_INGOT));
        ItemStack taken = menu.getSlot(FIRST_OUTPUT_SLOT).remove(2);
        check(helper, taken.is(Items.GOLD_INGOT) && taken.getCount() == 2, "Expected 2 gold ingots, took " + taken);
        check(helper, knowledge.getEmc().equals(BigInteger.valueOf(5_000 - 2 * 2_048)), "Taking 2 gold left " + knowledge.getEmc() + " EMC, not 904");
        // 904 EMC is less than one more gold ingot.
        ItemStack refused = menu.getSlot(FIRST_OUTPUT_SLOT).remove(1);
        check(helper, refused.isEmpty(), "Took " + refused + " without enough EMC");
        check(helper, knowledge.getEmc().equals(BigInteger.valueOf(904)), "A refused withdrawal changed EMC to " + knowledge.getEmc());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void unknownItemsCannotBeWithdrawn(GameTestHelper helper) {
        ServerPlayer player = PEGameTestSupport.player(helper, "unknown");
        IKnowledgeProvider knowledge = PEGameTestSupport.knowledge(player);
        knowledge.setEmc(BigInteger.valueOf(100_000));
        TransmutationContainer menu = new TransmutationContainer(1, player.getInventory());
        menu.transmutationInventory.writeIntoOutputSlot(0, new ItemStack(Items.DIAMOND));
        check(helper, menu.getSlot(FIRST_OUTPUT_SLOT).getItem().isEmpty(), "An unlearned diamond was offered as an output");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void unlearningForgetsTheItem(GameTestHelper helper) {
        ServerPlayer player = PEGameTestSupport.player(helper, "unlearn");
        IKnowledgeProvider knowledge = PEGameTestSupport.knowledge(player);
        knowledge.addKnowledge(new ItemStack(Items.REDSTONE));
        TransmutationContainer menu = new TransmutationContainer(1, player.getInventory());
        menu.getSlot(UNLEARN_SLOT).set(new ItemStack(Items.REDSTONE));
        check(helper, !knowledge.hasKnowledge(new ItemStack(Items.REDSTONE)), "Unlearning redstone left it known");
        helper.succeed();
    }

    /** The tablet opens the same menu from the hand it is held in. */
    @GameTest(template = TEMPLATE)
    public static void tabletOpensTheTransmutationMenu(GameTestHelper helper) {
        ServerPlayer player = PEGameTestSupport.player(helper, "tablet");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PEItems.TRANSMUTATION_TABLET.get()));
        player.getMainHandItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        check(helper, player.containerMenu instanceof TransmutationContainer, "Using the tablet opened " + player.containerMenu);
        player.closeContainer();
        helper.succeed();
    }
}
