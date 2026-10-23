/*
 * Mekanism 1.19.2 port - in-world tests shared by the Forge and Fabric builds.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import static mekanism.gametest.CoreMachineGameTests.TEMPLATE;
import static mekanism.gametest.CoreMachineGameTests.check;
import static mekanism.gametest.CoreMachineGameTests.poweredMachine;

import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import java.util.List;
import mekanism.api.chemical.ChemicalTags;
import mekanism.api.chemical.gas.Gas;
import mekanism.api.chemical.gas.GasStack;
import mekanism.api.chemical.infuse.InfusionStack;
import mekanism.api.chemical.merged.BoxedChemicalStack;
import mekanism.api.chemical.pigment.PigmentStack;
import mekanism.api.chemical.slurry.Slurry;
import mekanism.api.chemical.slurry.SlurryStack;
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.math.FloatingLong;
import mekanism.api.recipes.ChemicalDissolutionRecipe;
import mekanism.api.recipes.ElectrolysisRecipe.ElectrolysisRecipeOutput;
import mekanism.api.recipes.MekanismRecipe;
import mekanism.api.recipes.PressurizedReactionRecipe.PressurizedReactionRecipeOutput;
import mekanism.api.text.EnumColor;
import mekanism.common.Mekanism;
import mekanism.common.recipe.MekanismRecipeType;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.registries.MekanismFluids;
import mekanism.common.registries.MekanismGases;
import mekanism.common.registries.MekanismInfuseTypes;
import mekanism.common.registries.MekanismPigments;
import mekanism.common.registries.MekanismSlurries;
import mekanism.common.resource.PrimaryResource;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * CraftTweaker integration, imported from upstream {@code mekanism.common.integration.crafttweaker}. With
 * {@code -PwithCraftTweaker} the run installs CraftTweaker and {@code src/gametestCommon/crafttweaker/mekanism_gametest.zs},
 * which adds and removes one recipe per representative machine type. Every test then checks that the machine's recipe
 * lookup returns the script's recipe, or no longer finds the removed one. Without the flag the same lookups must show
 * Mekanism's own recipes only, which is the "builds and runs unchanged" half of the optional integration.
 * {@code ForgeCraftTweakerGameTests} covers the overloads that need CraftTweaker's Forge-only fluid stacks, and content scripts.
 */
public class CraftTweakerGameTests {

    /**
     * Set by the GameTest runs on both loaders when Gradle is given {@code -PwithCraftTweaker}.
     */
    static final boolean EXPECTED = Boolean.getBoolean("mekanism.gametest.crafttweaker");

    @GameTest(template = TEMPLATE)
    public static void craftTweakerLoadsOnlyWhenInstalled(GameTestHelper helper) {
        check(Mekanism.hooks.CraftTweakerLoaded == EXPECTED, "CraftTweakerLoaded is " + Mekanism.hooks.CraftTweakerLoaded + ", expected " + EXPECTED);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void mutableStacksConvertToDetachedImmutableStacks(GameTestHelper helper) {
        if (EXPECTED) {
            //Kept in a nested class so this class still loads when CraftTweaker is absent
            CrTStackChecks.asImmutableIsDetached();
        }
        helper.succeed();
    }

    private static class CrTStackChecks {

        static void asImmutableIsDetached() {
            var mutable = new mekanism.common.integration.crafttweaker.chemical.CrTMutableChemicalStack.CrTMutableGasStack(
                  MekanismGases.HYDROGEN.getStack(100));
            var immutable = mutable.asImmutable();
            check(immutable instanceof mekanism.common.integration.crafttweaker.chemical.CrTChemicalStack.CrTGasStack,
                  "asImmutable() returned " + immutable.getClass().getName());
            mutable.setAmount(5);
            check(immutable.getAmount() == 100, "asImmutable() shares the mutable stack, amount is now " + immutable.getAmount());
            immutable.setAmount(7);
            check(immutable.getAmount() == 100 && mutable.getAmount() == 5, "setAmount on the immutable stack changed a stack");
        }
    }

    /**
     * CraftTweaker registers its own ingredient types ({@code crafttweaker:any} and others; on Fabric through
     * IngredientExtensionAPI). The port's ingredient reader and writer must hand those to CraftTweaker rather than
     * rejecting them or reading them as plain item ingredients, on their own and inside an array.
     */
    @GameTest(template = TEMPLATE)
    public static void craftTweakerIngredientTypesReadAndSync(GameTestHelper helper) {
        if (EXPECTED) {
            ItemStack stick = new ItemStack(Items.STICK);
            Ingredient any = Ingredient.fromJson(JsonParser.parseString("{\"type\":\"crafttweaker:any\"}"));
            check(any.getClass() != Ingredient.class, "crafttweaker:any was read as a plain ingredient");
            check(any.test(stick), "crafttweaker:any does not match a stick");
            check(roundTrip(any).test(stick), "crafttweaker:any does not match a stick after a network round trip");
            Ingredient mixed = Ingredient.fromJson(JsonParser.parseString("[{\"type\":\"crafttweaker:any\"},{\"item\":\"minecraft:stone\"}]"));
            check(mixed.test(stick), "An array holding crafttweaker:any does not match a stick");
            check(roundTrip(mixed).test(stick), "An array holding crafttweaker:any does not match a stick after a network round trip");
        }
        helper.succeed();
    }

    private static Ingredient roundTrip(Ingredient ingredient) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        ingredient.toNetwork(buffer);
        Ingredient read = Ingredient.fromNetwork(buffer);
        check(buffer.readableBytes() == 0, buffer.readableBytes() + " bytes left after reading " + ingredient);
        return read;
    }

    @GameTest(template = TEMPLATE)
    public static void itemRecipesAreAddedAndRemoved(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ItemStack deadBush = new ItemStack(Items.DEAD_BUSH);
        ItemStack sponge = new ItemStack(Items.SPONGE);

        var enriching = MekanismRecipeType.ENRICHING.getInputCache().findFirstRecipe(level, deadBush);
        if (expectAdded(enriching, "enriching")) {
            check(enriching.getOutput(deadBush).is(Items.DIAMOND), "The enriching recipe gives " + enriching.getOutput(deadBush));
        }
        expectRemoved(MekanismRecipeType.CRUSHING.getInputCache().findFirstRecipe(level, new ItemStack(Items.FLINT)), "crushing/flint_to_gunpowder");

        var energy = MekanismRecipeType.ENERGY_CONVERSION.getInputCache().findFirstRecipe(level, deadBush);
        if (expectAdded(energy, "energy_conversion")) {
            check(energy.getOutput(deadBush).equals(FloatingLong.createConst(12_345)), "The energy conversion recipe gives " + energy.getOutput(deadBush));
        }

        ItemStack twoBushes = new ItemStack(Items.DEAD_BUSH, 2);
        var combining = MekanismRecipeType.COMBINING.getInputCache().findFirstRecipe(level, twoBushes, sponge);
        if (expectAdded(combining, "combining")) {
            check(combining.getOutput(twoBushes, sponge).is(Items.DIAMOND_BLOCK), "The combining recipe gives " + combining.getOutput(twoBushes, sponge));
        }

        var sawing = MekanismRecipeType.SAWING.getInputCache().findFirstRecipe(level, deadBush);
        if (expectAdded(sawing, "sawing")) {
            check(sawing.getSecondaryChance() == 0.25, "The sawing recipe's secondary chance is " + sawing.getSecondaryChance());
            check(sawing.getOutput(deadBush).getMainOutput().is(Items.STICK), "The sawing recipe's main output is " + sawing.getOutput(deadBush).getMainOutput());
        }
        expectRemoved(MekanismRecipeType.SAWING.getInputCache().findFirstRecipe(level, new ItemStack(Items.PURPLE_BED)), "sawing/bed/purple");
        helper.succeed();
    }

    /**
     * The infusing recipe's left input is {@code <tag:mekanism/gas:mekanism:gametest_crt>}, a gas tag the script creates with the
     * chemical tag manager, so this also covers the tag manager and tag-based gas ingredients.
     */
    @GameTest(template = TEMPLATE)
    public static void gasRecipesAndGasTagsAreScripted(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Gas ethene = MekanismGases.ETHENE.get();
        boolean tagged = ethene.is(ChemicalTags.GAS.tag(new ResourceLocation(Mekanism.MODID, "gametest_crt")));
        check(tagged == EXPECTED, "Ethene in mekanism:gametest_crt is " + tagged + ", expected " + EXPECTED);

        GasStack twoEthene = new GasStack(ethene, 2);
        GasStack chlorine = new GasStack(MekanismGases.CHLORINE.get(), 1);
        var infusing = MekanismRecipeType.CHEMICAL_INFUSING.getInputCache().findFirstRecipe(level, twoEthene, chlorine);
        if (expectAdded(infusing, "chemical_infusing")) {
            expectGas(infusing.getOutput(twoEthene, chlorine), MekanismGases.OXYGEN.get(), 3);
        }
        expectRemoved(MekanismRecipeType.CHEMICAL_INFUSING.getInputCache().findFirstRecipe(level, new GasStack(MekanismGases.OXYGEN.get(), 1),
              new GasStack(MekanismGases.SULFUR_DIOXIDE.get(), 2)), "chemical_infusing/sulfur_trioxide");

        GasStack fiveEthene = new GasStack(ethene, 5);
        var activating = MekanismRecipeType.ACTIVATING.getInputCache().findFirstRecipe(level, fiveEthene);
        if (expectAdded(activating, "activating")) {
            expectGas(activating.getOutput(fiveEthene), MekanismGases.OXYGEN.get(), 5);
        }

        ItemStack deadBush = new ItemStack(Items.DEAD_BUSH);
        var oxidizing = MekanismRecipeType.OXIDIZING.getInputCache().findFirstRecipe(level, deadBush);
        if (expectAdded(oxidizing, "oxidizing")) {
            expectGas(oxidizing.getOutput(deadBush), ethene, 50);
        }

        GasStack antimatter = new GasStack(MekanismGases.ANTIMATTER.get(), 2);
        var nucleosynthesizing = MekanismRecipeType.NUCLEOSYNTHESIZING.getInputCache().findFirstRecipe(level, deadBush, antimatter);
        if (expectAdded(nucleosynthesizing, "nucleosynthesizing")) {
            check(nucleosynthesizing.getDuration() == 100, "The nucleosynthesizing recipe takes " + nucleosynthesizing.getDuration() + " ticks");
            check(nucleosynthesizing.getOutput(deadBush, antimatter).is(Items.NETHER_STAR), "The nucleosynthesizing recipe gives "
                                                                                          + nucleosynthesizing.getOutput(deadBush, antimatter));
        }

        GasStack hundredEthene = new GasStack(ethene, 100);
        var crystallizing = MekanismRecipeType.CRYSTALLIZING.getInputCache().findFirstRecipe(level, BoxedChemicalStack.box(hundredEthene));
        if (expectAdded(crystallizing, "crystallizing")) {
            check(crystallizing.getOutput(BoxedChemicalStack.box(hundredEthene)).is(Items.SLIME_BALL), "The crystallizing recipe gives "
                                                                                                         + crystallizing.getOutput(BoxedChemicalStack.box(hundredEthene)));
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void infusePigmentAndSlurryRecipesAreScripted(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ItemStack sponge = new ItemStack(Items.SPONGE);
        ItemStack deadBush = new ItemStack(Items.DEAD_BUSH);
        ItemStack boneBlock = new ItemStack(Items.BONE_BLOCK);

        InfusionStack carbon = new InfusionStack(MekanismInfuseTypes.CARBON.get(), 10);
        var infusing = MekanismRecipeType.METALLURGIC_INFUSING.getInputCache().findFirstRecipe(level, sponge, carbon);
        if (expectAdded(infusing, "metallurgic_infusing")) {
            check(infusing.getOutput(sponge, carbon).is(Items.WET_SPONGE), "The metallurgic infusing recipe gives " + infusing.getOutput(sponge, carbon));
        }
        expectRemoved(MekanismRecipeType.METALLURGIC_INFUSING.getInputCache().findFirstRecipe(level, new ItemStack(Items.NETHERRACK),
              new InfusionStack(MekanismInfuseTypes.FUNGI.get(), 10)), "metallurgic_infusing/netherrack_to_crimson_nylium");

        var infusionConversion = MekanismRecipeType.INFUSION_CONVERSION.getInputCache().findFirstRecipe(level, deadBush);
        if (expectAdded(infusionConversion, "infusion_conversion")) {
            InfusionStack bio = infusionConversion.getOutput(deadBush);
            check(bio.getType() == MekanismInfuseTypes.BIO.get() && bio.getAmount() == 7, "The infusion conversion recipe gives " + bio.getAmount() + " " + bio.getType());
        }

        PigmentStack red = new PigmentStack(MekanismPigments.PIGMENT_COLOR_LOOKUP.get(EnumColor.RED).get(), 128);
        var painting = MekanismRecipeType.PAINTING.getInputCache().findFirstRecipe(level, boneBlock, red);
        if (expectAdded(painting, "painting")) {
            check(painting.getOutput(boneBlock, red).is(Items.RED_CONCRETE), "The painting recipe gives " + painting.getOutput(boneBlock, red));
        }

        var extracting = MekanismRecipeType.PIGMENT_EXTRACTING.getInputCache().findFirstRecipe(level, boneBlock);
        if (expectAdded(extracting, "pigment_extracting")) {
            PigmentStack white = extracting.getOutput(boneBlock);
            check(white.getType() == MekanismPigments.PIGMENT_COLOR_LOOKUP.get(EnumColor.WHITE).get() && white.getAmount() == 256,
                  "The pigment extracting recipe gives " + white.getAmount() + " " + white.getType());
        }

        GasStack acid = new GasStack(MekanismGases.SULFURIC_ACID.get(), 1);
        ChemicalDissolutionRecipe dissolution = MekanismRecipeType.DISSOLUTION.getInputCache().findFirstRecipe(level, sponge, acid);
        if (expectAdded(dissolution, "dissolution")) {
            Slurry dirtyTin = MekanismSlurries.PROCESSED_RESOURCES.get(PrimaryResource.TIN).getDirtySlurry();
            BoxedChemicalStack output = dissolution.getOutput(sponge, acid);
            check(output.getChemicalStack().getType() == dirtyTin && output.getChemicalStack().getAmount() == 500,
                  "The dissolution recipe gives " + output.getChemicalStack().getAmount() + " " + output.getChemicalStack().getType());
        }
        helper.succeed();
    }

    /**
     * CraftTweaker for Fabric 1.19.2 has no fluid stacks or {@code <fluid:>} bracket, so the script builds these fluid
     * inputs from {@code <tag:fluids:minecraft:lava>}, which both loaders support. The removals go through
     * CraftTweaker's {@code <recipetype:...>} manager.
     */
    @GameTest(template = TEMPLATE)
    public static void fluidTagRecipesAreAddedAndRemoved(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FluidStack brine = new FluidStack(MekanismFluids.BRINE.getStillFluid(), 10);
        expectRemoved(MekanismRecipeType.SEPARATING.getInputCache().findFirstRecipe(level, brine), "separator/brine");
        expectRemoved(MekanismRecipeType.ROTARY.getInputCache().findFirstRecipe(level, new GasStack(MekanismGases.HYDROFLUORIC_ACID.get(), 1)),
              "rotary/hydrofluoric_acid");

        FluidStack twoLava = new FluidStack(Fluids.LAVA, 2);
        var separating = MekanismRecipeType.SEPARATING.getInputCache().findFirstRecipe(level, twoLava);
        if (expectAdded(separating, "separating")) {
            ElectrolysisRecipeOutput output = separating.getOutput(twoLava);
            check(output.left().getType() == MekanismGases.OXYGEN.get() && output.right().getType() == MekanismGases.ETHENE.get() && output.right().getAmount() == 2,
                  "The separating recipe gives " + output.left().getType() + " and " + output.right().getAmount() + " " + output.right().getType());
        }

        FluidStack fiveLava = new FluidStack(Fluids.LAVA, 5);
        SlurryStack dirtyTin = new SlurryStack(MekanismSlurries.PROCESSED_RESOURCES.get(PrimaryResource.TIN).getDirtySlurry(), 1);
        var washing = MekanismRecipeType.WASHING.getInputCache().findFirstRecipe(level, fiveLava, dirtyTin);
        if (expectAdded(washing, "washing")) {
            SlurryStack output = washing.getOutput(fiveLava, dirtyTin);
            check(output.getType() == MekanismSlurries.PROCESSED_RESOURCES.get(PrimaryResource.COPPER).getCleanSlurry(), "The washing recipe gives " + output.getType());
        }

        ItemStack deadBush = new ItemStack(Items.DEAD_BUSH);
        FluidStack lava = new FluidStack(Fluids.LAVA, 100);
        GasStack ethene = new GasStack(MekanismGases.ETHENE.get(), 10);
        var reaction = MekanismRecipeType.REACTION.getInputCache().findFirstRecipe(level, deadBush, lava, ethene);
        if (expectAdded(reaction, "reaction")) {
            check(reaction.getDuration() == 60, "The reaction recipe takes " + reaction.getDuration() + " ticks");
            PressurizedReactionRecipeOutput output = reaction.getOutput(deadBush, lava, ethene);
            check(output.item().is(Items.DIAMOND) && output.gas().isEmpty(), "The reaction recipe gives " + output.item() + " and " + output.gas().getType());
        }

        FluidStack tenLava = new FluidStack(Fluids.LAVA, 10);
        var rotary = MekanismRecipeType.ROTARY.getInputCache().findFirstRecipe(level, tenLava);
        if (expectAdded(rotary, "rotary")) {
            check(rotary.hasFluidToGas() && !rotary.hasGasToFluid(), "The rotary recipe is not fluid to gas only");
            expectGas(rotary.getGasOutput(tenLava), MekanismGases.ETHENE.get(), 10);
        }
        helper.succeed();
    }

    /**
     * A scripted recipe reaches the machine: the enrichment chamber's input slot accepts dead bushes only when the script
     * added a recipe for them, and then enriches them into diamonds.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void enrichmentChamberRunsTheScriptedRecipe(GameTestHelper helper) {
        TileEntityMekanism chamber = poweredMachine(helper, MekanismBlocks.ENRICHMENT_CHAMBER);
        List<IInventorySlot> slots = chamber.getInventorySlots(null);
        ItemStack deadBushes = new ItemStack(Items.DEAD_BUSH, 2);
        boolean accepted = slots.get(0).isItemValid(deadBushes);
        check(accepted == EXPECTED, "The enrichment chamber accepting dead bushes is " + accepted + ", expected " + EXPECTED);
        if (!EXPECTED) {
            helper.succeed();
            return;
        }
        slots.get(0).setStack(deadBushes);
        helper.succeedWhen(() -> {
            ItemStack output = slots.get(1).getStack();
            if (!output.is(Items.DIAMOND) || output.getCount() < 2) {
                throw new GameTestAssertException("Expected 2 diamonds, found " + output);
            }
        });
    }

    /**
     * With CraftTweaker the lookup has to find {@code crafttweaker:gametest/<name>}; without it, nothing.
     *
     * @return whether the recipe is there, so the caller can check its outputs.
     */
    static boolean expectAdded(@Nullable MekanismRecipe recipe, String name) {
        ResourceLocation id = new ResourceLocation("crafttweaker", "gametest/" + name);
        if (EXPECTED) {
            check(recipe != null, "The " + name + " lookup found no recipe; expected " + id);
            check(recipe.getId().equals(id), "The " + name + " lookup found " + recipe.getId() + ", expected " + id);
            return true;
        }
        check(recipe == null, "The " + name + " lookup found " + (recipe == null ? null : recipe.getId()) + " without CraftTweaker");
        return false;
    }

    /**
     * With CraftTweaker the script removed {@code mekanism:<path>}, so the lookup finds nothing; without it, that recipe.
     */
    static void expectRemoved(@Nullable MekanismRecipe recipe, String path) {
        ResourceLocation id = new ResourceLocation(Mekanism.MODID, path);
        if (EXPECTED) {
            check(recipe == null, "The script removed " + id + ", but the lookup found " + (recipe == null ? null : recipe.getId()));
        } else {
            check(recipe != null && recipe.getId().equals(id), "Expected the lookup to find " + id + ", found " + (recipe == null ? null : recipe.getId()));
        }
    }

    private static void expectGas(GasStack stack, Gas gas, long amount) {
        check(stack.getType() == gas && stack.getAmount() == amount, "Expected " + amount + " " + gas + ", found " + stack.getAmount() + " " + stack.getType());
    }
}
