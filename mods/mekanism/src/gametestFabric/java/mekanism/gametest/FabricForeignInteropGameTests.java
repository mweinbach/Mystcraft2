/*
 * Mekanism 1.19.2 port - development-only Fabric GameTests.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import static mekanism.gametest.CoreMachineGameTests.TEMPLATE;
import static mekanism.gametest.CoreMachineGameTests.check;

import mekanism.additions.common.MekanismAdditions;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.chemical.gas.GasStack;
import mekanism.api.chemical.gas.IGasHandler;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.registries.MekanismGases;
import mekanism.common.registries.MekanismItems;
import mekanism.common.tile.TileEntityChemicalTank;
import mekanism.common.tile.base.TileEntityMekanism;
import mekanism.common.tile.base.WrenchResult;
import mekanism.common.util.MekanismUtils;
import mekanism.generators.common.registries.GeneratorsFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import ports.mekanism.lib.common.capabilities.ForgeCapabilities;
import ports.mekanism.lib.items.IItemHandler;

/**
 * Fabric only: other mods' items and tags reach Mekanism through Fabric's conventions. A foreign item's gas comes from
 * {@code MekanismChemicalLookups.GAS_ITEM} and its items from the {@code fabric:item_storage} lookup, as a foreign
 * block's already do; {@code c:} tags feed the {@code forge:} tags Mekanism's recipes and wrench check use. The items are
 * {@link FabricTestItems}, registered under the test mod's namespace. Forge reaches other mods through capabilities and
 * its own {@code forge:} tags, so these checks have no Forge counterpart.
 */
public class FabricForeignInteropGameTests {

    @GameTest(template = TEMPLATE)
    public static void foreignGasItemAnswersTheGasCapability(GameTestHelper helper) {
        ItemStack stack = new ItemStack(FabricTestItems.FOREIGN_GAS_TANK);
        IGasHandler handler = stack.getCapability(Capabilities.GAS_HANDLER).resolve().orElse(null);
        check(handler != null, "Another mod's gas tank item has no Mekanism gas handler");
        check(handler.getTanks() == 1 && handler.getTankCapacity(0) == FabricTestItems.GAS_CAPACITY,
              "The foreign gas handler reports " + handler.getTanks() + " tanks of " + handler.getTankCapacity(0));
        GasStack simulated = handler.insertChemical(new GasStack(MekanismGases.HYDROGEN, 400), Action.SIMULATE);
        check(simulated.isEmpty(), "A simulated fill was refused: " + simulated.getAmount());
        check(FabricTestItems.storedGas(stack).isEmpty(), "A simulated fill stored " + FabricTestItems.storedGas(stack).getAmount());
        GasStack remainder = handler.insertChemical(new GasStack(MekanismGases.HYDROGEN, 400), Action.EXECUTE);
        check(remainder.isEmpty(), "The foreign item refused " + remainder.getAmount() + " hydrogen");
        GasStack stored = FabricTestItems.storedGas(stack);
        check(stored.getAmount() == 400 && stored.getType() == MekanismGases.HYDROGEN.get(),
              "The foreign item holds " + stored.getAmount() + " of " + stored.getType());
        GasStack drained = handler.extractChemical(150, Action.EXECUTE);
        check(drained.getAmount() == 150 && FabricTestItems.storedGas(stack).getAmount() == 250,
              "Draining 150 moved " + drained.getAmount() + " and left " + FabricTestItems.storedGas(stack).getAmount());
        check(!stack.getCapability(Capabilities.INFUSION_HANDLER).isPresent(), "A gas-only item exposed an infusion handler");
        check(!new ItemStack(Items.IRON_INGOT).getCapability(Capabilities.GAS_HANDLER).isPresent(), "An iron ingot exposed a gas handler");
        helper.succeed();
    }

    /**
     * A chemical tank's drain slot accepts another mod's gas item and fills it, the way it fills Mekanism's own.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void chemicalTankFillsAForeignGasItem(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, MekanismBlocks.BASIC_CHEMICAL_TANK.getBlock());
        TileEntityChemicalTank tank = (TileEntityChemicalTank) helper.getBlockEntity(pos);
        long amount = 600;
        GasStack refused = tank.getGasTank().insert(new GasStack(MekanismGases.HYDROGEN, amount), Action.EXECUTE, AutomationType.MANUAL);
        check(refused.isEmpty(), "The chemical tank refused hydrogen: " + refused.getAmount());
        IInventorySlot drainSlot = tank.getInventorySlots(null).get(0);
        ItemStack foreign = new ItemStack(FabricTestItems.FOREIGN_GAS_TANK);
        check(drainSlot.isItemValid(foreign), "The chemical tank's drain slot rejects another mod's gas item");
        drainSlot.setStack(foreign);
        helper.succeedWhen(() -> {
            GasStack stored = FabricTestItems.storedGas(drainSlot.getStack());
            check(stored.getAmount() == amount && stored.getType() == MekanismGases.HYDROGEN.get(),
                  "The foreign item in the drain slot holds " + stored.getAmount() + " of " + stored.getType());
            check(tank.getGasTank().isEmpty(), "The chemical tank still holds " + tank.getGasTank().getStored());
        });
    }

    @GameTest(template = TEMPLATE)
    public static void foreignItemStorageAnswersTheItemCapability(GameTestHelper helper) {
        ItemStack pouch = new ItemStack(FabricTestItems.FOREIGN_POUCH);
        IItemHandler handler = pouch.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElse(null);
        check(handler != null, "Another mod's item-storage item has no item handler");
        check(handler.getSlots() == 1, "The pouch's handler has " + handler.getSlots() + " slots");
        ItemStack simulated = handler.insertItem(0, new ItemStack(Items.COBBLESTONE, 16), true);
        check(simulated.isEmpty(), "A simulated insert was refused: " + simulated);
        check(FabricTestItems.pouchContents(pouch).isEmpty(), "A simulated insert stored " + FabricTestItems.pouchContents(pouch));
        ItemStack remainder = handler.insertItem(0, new ItemStack(Items.COBBLESTONE, 16), false);
        check(remainder.isEmpty(), "The pouch refused " + remainder);
        ItemStack held = FabricTestItems.pouchContents(pouch);
        check(held.is(Items.COBBLESTONE) && held.getCount() == 16, "The pouch holds " + held + " after the insert");
        ItemStack inSlot = handler.getStackInSlot(0);
        check(inSlot.is(Items.COBBLESTONE) && inSlot.getCount() == 16, "The handler reports " + inSlot);
        ItemStack extracted = handler.extractItem(0, 4, false);
        check(extracted.is(Items.COBBLESTONE) && extracted.getCount() == 4, "Extracting 4 returned " + extracted);
        check(FabricTestItems.pouchContents(pouch).getCount() == 12, "The pouch holds " + FabricTestItems.pouchContents(pouch) + " after the extract");
        ItemStack dirt = handler.insertItem(0, new ItemStack(Items.DIRT), false);
        check(dirt.getCount() == 1, "A pouch holding cobblestone took dirt");
        check(!new ItemStack(Items.IRON_INGOT).getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent(), "An iron ingot exposed an item handler");
        helper.succeed();
    }

    /**
     * The configurator is in {@code c:wrenches}, and a {@code c:wrenches} item from another mod rotates and dismantles
     * Mekanism machines.
     */
    @GameTest(template = TEMPLATE)
    public static void fabricWrenchesWorkOnMekanismBlocks(GameTestHelper helper) {
        TagKey<Item> wrenches = itemTag("c", "wrenches");
        check(new ItemStack(MekanismItems.CONFIGURATOR.get()).is(wrenches), "The configurator is not in c:wrenches");
        ItemStack wrench = new ItemStack(FabricTestItems.FOREIGN_WRENCH);
        check(wrench.is(itemTag("forge", "wrenches")), "forge:wrenches does not include c:wrenches");
        check(MekanismUtils.canUseAsWrench(wrench), "Mekanism does not accept a c:wrenches item as a wrench");
        check(!MekanismUtils.canUseAsWrench(new ItemStack(Items.STICK)), "Mekanism accepts a stick as a wrench");

        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, MekanismBlocks.ENRICHMENT_CHAMBER.getBlock());
        TileEntityMekanism chamber = (TileEntityMekanism) helper.getBlockEntity(pos);
        Player player = helper.makeMockPlayer();
        player.setItemInHand(InteractionHand.MAIN_HAND, wrench);
        BlockPos absolute = helper.absolutePos(pos);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false);
        Direction facing = chamber.getDirection();
        WrenchResult rotated = chamber.tryWrench(helper.getBlockState(pos), player, InteractionHand.MAIN_HAND, hit);
        check(rotated == WrenchResult.SUCCESS, "Using the foreign wrench returned " + rotated);
        check(chamber.getDirection() == facing.getClockWise(), "The foreign wrench left the chamber facing " + chamber.getDirection());
        player.setShiftKeyDown(true);
        WrenchResult dismantled = chamber.tryWrench(helper.getBlockState(pos), player, InteractionHand.MAIN_HAND, hit);
        check(dismantled == WrenchResult.DISMANTLED, "Sneaking with the foreign wrench returned " + dismantled);
        check(helper.getBlockState(pos).isAir(), "The dismantled chamber is still " + helper.getBlockState(pos));
        helper.succeed();
    }

    /**
     * Items in the {@code c:} tags fill the {@code forge:} tags Mekanism's recipes use, and an Additions recipe takes
     * them. Normal and deepslate cobblestone stay apart. Generators' fluids are in their {@code c:} tags.
     */
    @GameTest(template = TEMPLATE)
    public static void recipeTagsReachFabricConventionTags(GameTestHelper helper) {
        ItemStack material = new ItemStack(FabricTestItems.FOREIGN_MATERIAL);
        check(material.is(itemTag("forge", "slimeballs")), "forge:slimeballs does not include c:slime_balls");
        check(material.is(itemTag("forge", "cobblestone/normal")), "forge:cobblestone/normal does not include c:cobblestone");
        check(material.is(itemTag("forge", "glass/red")), "forge:glass/red does not include c:red_glass_blocks");
        check(!material.is(itemTag("forge", "cobblestone/deepslate")), "c:cobblestone reached forge:cobblestone/deepslate");
        boolean additionsRecipe = helper.getLevel().getRecipeManager().getRecipes().stream()
              .filter(recipe -> recipe.getId().getNamespace().equals(MekanismAdditions.MODID))
              .anyMatch(recipe -> recipe.getIngredients().stream().anyMatch(ingredient -> ingredient.test(material)));
        check(additionsRecipe, "No Additions recipe accepts a c:slime_balls item");
        Fluid tritium = GeneratorsFluids.TRITIUM.getStillFluid();
        check(tritium.defaultFluidState().is(TagKey.create(Registry.FLUID_REGISTRY, new ResourceLocation("c", "tritium"))),
              "Generators' tritium is not in c:tritium");
        check(tritium.defaultFluidState().is(TagKey.create(Registry.FLUID_REGISTRY, new ResourceLocation("forge", "tritium"))),
              "Generators' tritium is not in forge:tritium");
        helper.succeed();
    }

    private static TagKey<Item> itemTag(String namespace, String path) {
        return TagKey.create(Registry.ITEM_REGISTRY, new ResourceLocation(namespace, path));
    }
}
