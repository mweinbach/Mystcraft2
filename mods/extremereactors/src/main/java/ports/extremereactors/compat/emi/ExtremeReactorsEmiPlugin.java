/*
 * Extreme Reactors 1.19.2 port - EMI integration
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.compat.emi;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import it.zerono.mods.extremereactors.ExtremeReactors;
import it.zerono.mods.extremereactors.api.IMapping;
import it.zerono.mods.extremereactors.api.coolant.FluidMappingsRegistry;
import it.zerono.mods.extremereactors.api.coolant.FluidsRegistry;
import it.zerono.mods.extremereactors.api.coolant.TransitionsRegistry;
import it.zerono.mods.extremereactors.api.internal.AbstractNamedValue;
import it.zerono.mods.extremereactors.api.reactor.ReactantMappingsRegistry;
import it.zerono.mods.extremereactors.api.reactor.ReactantsRegistry;
import it.zerono.mods.extremereactors.api.reactor.Reaction;
import it.zerono.mods.extremereactors.api.reactor.ReactionsRegistry;
import it.zerono.mods.extremereactors.gamecontent.Content;
import it.zerono.mods.extremereactors.gamecontent.multiblock.fluidizer.recipe.FluidizerFluidMixingRecipe;
import it.zerono.mods.extremereactors.gamecontent.multiblock.fluidizer.recipe.FluidizerSolidMixingRecipe;
import it.zerono.mods.extremereactors.gamecontent.multiblock.fluidizer.recipe.FluidizerSolidRecipe;
import it.zerono.mods.extremereactors.gamecontent.multiblock.reprocessor.recipe.ReprocessorRecipe;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import ports.extremereactors.corelib.fluid.FluidStack;
import ports.extremereactors.corelib.tag.TagsHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * EMI's view of Extreme Reactors: the categories, workstations and reactant, coolant and vapor stacks of the JEI
 * plugin. ER2 has no EMI plugin; with JEI installed too, EMI skips the JEI categories of a mod that has its own plugin.
 */
@EmiEntrypoint
public final class ExtremeReactorsEmiPlugin implements EmiPlugin {

    @Override
    public void register(final EmiRegistry registry) {

        final EmiRecipeCategory reprocessor = category(registry, "reprocessor",
                "compat.bigreactors.jei.reprocessor.recipecategory.title", Content.Blocks.REPROCESSOR_WASTEINJECTOR);
        final EmiRecipeCategory fluidizerSolid = category(registry, "fluidizer_solid",
                "compat.bigreactors.jei.fluidizer.recipecategory.solid.title", Content.Blocks.FLUIDIZER_SOLIDINJECTOR);
        final EmiRecipeCategory fluidizerSolidMixing = category(registry, "fluidizer_solidmixing",
                "compat.bigreactors.jei.fluidizer.recipecategory.solidmixing.title", Content.Blocks.FLUIDIZER_SOLIDINJECTOR);
        final EmiRecipeCategory fluidizerFluidMixing = category(registry, "fluidizer_fluidmixing",
                "compat.bigreactors.jei.fluidizer.recipecategory.fluidmixing.title", Content.Blocks.FLUIDIZER_FLUIDINJECTOR);
        final EmiRecipeCategory reactions = category(registry, "reactantsreactions",
                "compat.bigreactors.jei.reactantsreactions.recipecategory.title", Content.Blocks.REACTOR_FUELROD_BASIC);
        final EmiRecipeCategory reactantsFromSolid = category(registry, "reactantsmappings_solid",
                "compat.bigreactors.jei.reactantsmappings.solid.recipecategory.title", Content.Blocks.REACTOR_FUELROD_BASIC);
        final EmiRecipeCategory reactantsFromFluid = category(registry, "reactantsmappings_fluid",
                "compat.bigreactors.jei.reactantsmappings.fluid.recipecategory.title", Content.Blocks.REACTOR_FUELROD_BASIC);
        final EmiRecipeCategory vaporizations = category(registry, "vaporizations",
                "compat.bigreactors.jei.vaporization.recipecategory.title", Content.Blocks.REACTOR_FLUID_ACCESSPORT_REINFORCED);
        final EmiRecipeCategory condensations = category(registry, "condensations",
                "compat.bigreactors.jei.condensation.recipecategory.title", Content.Blocks.REACTOR_FLUID_ACCESSPORT_REINFORCED);
        final EmiRecipeCategory coolants = category(registry, "coolantsmappings",
                "compat.bigreactors.jei.coolantmappings.recipecategory.title", Content.Blocks.REACTOR_FLUID_ACCESSPORT_REINFORCED);
        final EmiRecipeCategory vapors = category(registry, "vaporsmappings",
                "compat.bigreactors.jei.vapormappings.recipecategory.title", Content.Blocks.REACTOR_FLUID_ACCESSPORT_REINFORCED);

        ReactantsRegistry.getReactants().forEach(reactant -> registry.addEmiStack(NamedValueEmiStack.of(reactant)));
        FluidsRegistry.getCoolants().forEach(coolant -> registry.addEmiStack(NamedValueEmiStack.of(coolant)));
        FluidsRegistry.getVapors().forEach(vapor -> registry.addEmiStack(NamedValueEmiStack.of(vapor)));

        for (final ReprocessorRecipe recipe : Content.Recipes.REPROCESSOR_RECIPE_TYPE.getRecipes()) {
            registry.addRecipe(new ReprocessorEmiRecipe(reprocessor, recipe));
        }

        Content.Recipes.FLUIDIZER_RECIPE_TYPE.getRecipes().forEach(recipe -> {

            if (recipe instanceof FluidizerSolidRecipe solid) {
                registry.addRecipe(FluidizerEmiRecipe.solid(fluidizerSolid, solid));
            } else if (recipe instanceof FluidizerSolidMixingRecipe solidMixing) {
                registry.addRecipe(FluidizerEmiRecipe.solidMixing(fluidizerSolidMixing, solidMixing));
            } else if (recipe instanceof FluidizerFluidMixingRecipe fluidMixing) {
                registry.addRecipe(FluidizerEmiRecipe.fluidMixing(fluidizerFluidMixing, fluidMixing));
            }
        });

        for (final Reaction reaction : ReactionsRegistry.getReactions()) {
            registry.addRecipe(new MappingEmiRecipe(reactions, reaction.getSource().getName(),
                    NamedValueEmiStack.of(reaction.getSource()), NamedValueEmiStack.of(reaction.getProduct())));
        }

        addTagMappings(registry, reactantsFromSolid, ReactantMappingsRegistry.getToSolidMap(),
                tag -> TagsHelper.ITEMS.getObjects(tag).stream().map(item -> EmiStack.of(new ItemStack(item))).toList(),
                NamedValueEmiStack::of);
        addTagMappings(registry, reactantsFromFluid, ReactantMappingsRegistry.getToFluidMap(),
                tag -> TagsHelper.FLUIDS.getObjects(tag).stream().map(fluid -> EmiWidgets.fluid(new FluidStack(fluid, 1000))).toList(),
                NamedValueEmiStack::of);
        addTagMappings(registry, coolants, FluidMappingsRegistry.getCoolantToFluidMap(),
                tag -> TagsHelper.FLUIDS.getObjects(tag).stream().map(fluid -> EmiWidgets.fluid(new FluidStack(fluid, 1000))).toList(),
                NamedValueEmiStack::of);
        addTagMappings(registry, vapors, FluidMappingsRegistry.getVaporToFluidMap(),
                tag -> TagsHelper.FLUIDS.getObjects(tag).stream().map(fluid -> EmiWidgets.fluid(new FluidStack(fluid, 1000))).toList(),
                NamedValueEmiStack::of);

        TransitionsRegistry.getVaporizations().values().forEach(mapping -> registry.addRecipe(new MappingEmiRecipe(
                vaporizations, mapping.getSource().getName(),
                NamedValueEmiStack.of(mapping.getSource()), NamedValueEmiStack.of(mapping.getProduct()))));
        TransitionsRegistry.getCondensations().values().forEach(mapping -> registry.addRecipe(new MappingEmiRecipe(
                condensations, mapping.getSource().getName(),
                NamedValueEmiStack.of(mapping.getSource()), NamedValueEmiStack.of(mapping.getProduct()))));

        workstation(registry, Content.Blocks.REPROCESSOR_CONTROLLER, reprocessor);
        workstation(registry, Content.Blocks.FLUIDIZER_CONTROLLER, fluidizerSolid, fluidizerSolidMixing, fluidizerFluidMixing);
        workstation(registry, Content.Blocks.REACTOR_CONTROLLER_BASIC, reactions);
        workstation(registry, Content.Blocks.REACTOR_CONTROLLER_REINFORCED, reactions, vaporizations, condensations);
        workstation(registry, Content.Blocks.REACTOR_SOLID_ACCESSPORT_BASIC, reactantsFromSolid);
        workstation(registry, Content.Blocks.REACTOR_SOLID_ACCESSPORT_REINFORCED, reactantsFromSolid);
        workstation(registry, Content.Blocks.REACTOR_FLUID_ACCESSPORT_REINFORCED, reactantsFromFluid);
        workstation(registry, Content.Blocks.REACTOR_FLUIDPORT_FORGE_PASSIVE_REINFORCED, vapors, coolants);
        workstation(registry, Content.Blocks.REACTOR_FLUIDTPORT_FORGE_ACTIVE_REINFORCED, vapors, coolants);
        workstation(registry, Content.Blocks.TURBINE_FLUIDPORT_FORGE_ACTIVE_BASIC, vapors, coolants);
        workstation(registry, Content.Blocks.TURBINE_FLUIDPORT_FORGE_PASSIVE_BASIC, vapors, coolants);
        workstation(registry, Content.Blocks.TURBINE_FLUIDPORT_FORGE_ACTIVE_REINFORCED, vapors, coolants);
        workstation(registry, Content.Blocks.TURBINE_FLUIDPORT_FORGE_PASSIVE_REINFORCED, vapors, coolants);
    }

    //region internals

    private static EmiRecipeCategory category(final EmiRegistry registry, final String name, final String titleKey,
                                              final Supplier<? extends ItemLike> icon) {

        final EmiRecipeCategory category = new EmiRecipeCategory(ExtremeReactors.newID(name), EmiStack.of(icon.get())) {

            @Override
            public Component getName() {
                return Component.translatable(titleKey);
            }
        };

        registry.addCategory(category);
        return category;
    }

    private static void workstation(final EmiRegistry registry, final Supplier<? extends ItemLike> block,
                                    final EmiRecipeCategory... categories) {

        final EmiStack stack = EmiStack.of(block.get());

        for (final EmiRecipeCategory category : categories) {
            registry.addWorkstation(category, stack);
        }
    }

    /**
     * One recipe per value, taking anything in the tags that map to it, as the JEI categories do.
     */
    private static <V extends AbstractNamedValue, T> void addTagMappings(final EmiRegistry registry,
                                                                        final EmiRecipeCategory category,
                                                                        final Map<V, List<IMapping<V, TagKey<T>>>> mappings,
                                                                        final Function<TagKey<T>, List<EmiStack>> tagContents,
                                                                        final Function<V, EmiStack> output) {

        mappings.forEach((value, valueMappings) -> {

            final List<EmiStack> inputs = new ArrayList<>();

            valueMappings.forEach(mapping -> inputs.addAll(tagContents.apply(mapping.getProduct())));

            if (!inputs.isEmpty()) {
                registry.addRecipe(new MappingEmiRecipe(category, value.getName(), EmiIngredient.of(inputs),
                        output.apply(value)));
            }
        });
    }

    //endregion
}
