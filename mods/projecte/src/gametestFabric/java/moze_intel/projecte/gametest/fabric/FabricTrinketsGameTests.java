/*
 * ProjectE 1.19.2 port - in-world tests, Fabric side.
 * Copyright (c) 2026 ProjectE 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package moze_intel.projecte.gametest.fabric;

import static moze_intel.projecte.gametest.PEGameTestSupport.check;

import dev.emi.trinkets.api.SlotReference;
import dev.emi.trinkets.api.TrinketComponent;
import dev.emi.trinkets.api.TrinketInventory;
import dev.emi.trinkets.api.TrinketsApi;
import java.util.Map;
import moze_intel.projecte.gameObjs.items.ItemPE;
import moze_intel.projecte.gameObjs.registries.PEItems;
import moze_intel.projecte.gametest.PEGameTestSupport;
import moze_intel.projecte.utils.Constants;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Trinkets stands in for Curios on Fabric: the slots upstream registers with Curios (necklace, ring, belt and the Klein
 * Star slot) exist on players, accept ProjectE's items, tick them, and feed EMC from a Klein Star. Registered only when
 * the run has Trinkets ({@code -PwithTrinkets}); without it ProjectE runs, and is tested, with no trinket slots.
 */
public class FabricTrinketsGameTests {

    @GameTest(template = PEGameTestSupport.TEMPLATE)
    public static void projecteSlotsAcceptProjecteItems(GameTestHelper helper) {
        ServerPlayer player = PEGameTestSupport.player(helper, "trinket_slots");
        TrinketComponent component = TrinketsApi.getTrinketComponent(player).orElse(null);
        check(helper, component != null, "The player has no trinket component");
        String[][] expected = {{"chest", "necklace", "projecte:soul_stone"}, {"hand", "ring", "projecte:zero_ring"},
                               {"legs", "belt", "projecte:repair_talisman"}, {"chest", "klein_star", "projecte:klein_star_ein"}};
        for (String[] slot : expected) {
            TrinketInventory inventory = slot(component, slot[0], slot[1]);
            check(helper, inventory != null, "No trinket slot " + slot[0] + "/" + slot[1]);
            ItemStack stack = new ItemStack(net.minecraft.core.Registry.ITEM.get(new net.minecraft.resources.ResourceLocation(slot[2])));
            check(helper, TrinketsApi.evaluatePredicateSet(inventory.getSlotType().getValidatorPredicates(), stack, new SlotReference(inventory, 0), player),
                  slot[2] + " is not accepted in " + slot[0] + "/" + slot[1]);
        }
        check(helper, !TrinketsApi.evaluatePredicateSet(slot(component, "chest", "necklace").getSlotType().getValidatorPredicates(),
              new ItemStack(PEItems.ZERO_RING.get()), new SlotReference(slot(component, "chest", "necklace"), 0), player), "A ring fits the necklace slot");
        helper.succeed();
    }

    @GameTest(template = PEGameTestSupport.TEMPLATE)
    public static void trinketSlotsTickProjecteItems(GameTestHelper helper) {
        ServerPlayer player = PEGameTestSupport.player(helper, "trinket_tick");
        player.setHealth(10);
        TrinketInventory necklace = slot(TrinketsApi.getTrinketComponent(player).orElseThrow(), "chest", "necklace");
        ItemStack stone = new ItemStack(PEItems.SOUL_STONE.get());
        stone.getOrCreateTag().putBoolean(Constants.NBT_KEY_ACTIVE, true);
        ItemPE.setEmc(stone, 1_000);
        necklace.setItem(0, stone);
        TrinketsApi.getTrinket(stone.getItem()).tick(stone, new SlotReference(necklace, 0), player);
        check(helper, player.getHealth() > 10, "A Soul Stone in the necklace slot did not heal");
        necklace.setItem(0, ItemStack.EMPTY);
        helper.succeed();
    }

    @GameTest(template = PEGameTestSupport.TEMPLATE)
    public static void kleinStarSlotFeedsEmcToItems(GameTestHelper helper) {
        ServerPlayer player = PEGameTestSupport.player(helper, "trinket_star");
        TrinketInventory starSlot = slot(TrinketsApi.getTrinketComponent(player).orElseThrow(), "chest", "klein_star");
        ItemStack star = new ItemStack(PEItems.KLEIN_STAR_EIN.get());
        ItemPE.setEmc(star, 10_000);
        starSlot.setItem(0, star);
        ItemStack ring = new ItemStack(PEItems.SWIFTWOLF_RENDING_GALE.get());
        check(helper, ItemPE.consumeFuel(player, ring, 64, true), "No EMC was drawn from the trinket Klein Star");
        check(helper, ItemPE.getEmc(starSlot.getItem(0)) < 10_000, "The Klein Star in its trinket slot paid nothing");
        starSlot.setItem(0, ItemStack.EMPTY);
        helper.succeed();
    }

    private static TrinketInventory slot(TrinketComponent component, String group, String slot) {
        Map<String, TrinketInventory> slots = component.getInventory().get(group);
        return slots == null ? null : slots.get(slot);
    }
}
