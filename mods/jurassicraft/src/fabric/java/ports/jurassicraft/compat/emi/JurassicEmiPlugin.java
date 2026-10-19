package ports.jurassicraft.compat.emi;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.recipe.EmiCraftingRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.Comparison;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.assembly.AssemblyRegistry;
import ports.jurassicraft.assembly.SkeletonDisplayItem;
import ports.jurassicraft.content.JurassicContent;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.entity.DinosaurRegistry;
import ports.jurassicraft.figure.ActionFigureRegistry;
import ports.jurassicraft.genetics.DNAData;
import ports.jurassicraft.genetics.Genetics;
import ports.jurassicraft.genetics.GeneticsRegistry;
import ports.jurassicraft.hatching.HatcheryRecipes;
import ports.jurassicraft.hatching.HatcheryRegistry;
import ports.jurassicraft.plants.PlantSpecies;

/** Loaded only by EMI. Machine previews describe dynamic processes, not exact crafting recipes. */
public final class JurassicEmiPlugin implements EmiPlugin {
    @Override public void register(EmiRegistry registry) {
        registerComparisons(registry);
        Map<RecipePreviews.Kind, EmiRecipeCategory> categories = new EnumMap<>(RecipePreviews.Kind.class);
        for (RecipePreviews.Kind kind : RecipePreviews.Kind.values()) {
            EmiStack workstation = EmiStack.of(RecipePreviews.catalyst(kind));
            EmiRecipeCategory category = new EmiRecipeCategory(id(kind.id), workstation) {
                @Override public Component getName() { return Component.translatable("jei.jurassicraft.category." + kind.id); }
            };
            categories.put(kind, category);
            registry.addCategory(category);
            registry.addWorkstation(category, workstation);
        }
        EmiMachineHandlers.register(registry);
        EmiMachineScreens.register(categories);
        RecipePreviews.build().forEach((kind, previews) -> {
            int index = 0;
            for (RecipePreviews.Preview preview : previews)
                registry.addRecipe(new MachinePreview(categories.get(kind), id("emi/" + kind.id + "/" + index++), preview));
        });
        for (RecipePreviews.FigureCycle cycle : RecipePreviews.figureCycles()) {
            // Other figure data, such as pose and gender, is carried through by the real recipe.
            registry.addRecipe(new EmiCraftingRecipe(List.of(EmiStack.of(cycle.input())), EmiStack.of(cycle.output()),
                    id("emi/figure_cycle/" + RecipePreviews.figureSubtype(cycle.input())), true) {
                @Override public boolean supportsRecipeTree() { return false; }
            });
        }
    }

    private static ResourceLocation id(String path) { return new ResourceLocation(PortMod.MOD_ID, path); }

    private static void registerComparisons(EmiRegistry registry) {
        registry.setDefaultComparison(GeneticsRegistry.STORAGE_DISC.get(),
                Comparison.compareData(stack -> RecipePreviews.dnaSubtype(stack.getItemStack())));
        registry.setDefaultComparison(ActionFigureRegistry.ITEM.get(),
                Comparison.compareData(stack -> RecipePreviews.figureSubtype(stack.getItemStack())));
        registry.setDefaultComparison(AssemblyRegistry.DISPLAY_ITEM.get(), Comparison.compareData(stack -> {
            ItemStack item = stack.getItemStack();
            return BlockItem.getBlockEntityData(item) == null ? "blank"
                    : SkeletonDisplayItem.species(item).id() + ":" + SkeletonDisplayItem.fresh(item);
        }));
        Comparison extract = Comparison.compareData(stack -> !Genetics.extract(stack.getItemStack(), RandomSource.create(0)).isEmpty());
        Comparison tissue = Comparison.compareData(stack -> !Genetics.sequence(stack.getItemStack(), RandomSource.create(0)).isEmpty());
        registry.setDefaultComparison(DinosaurRegistry.DINOSAUR_MEAT.get(), Comparison.compareData(stack ->
                Genetics.read(stack.getItemStack().getTag()).filter(dna -> dna.kind() == DNAData.Kind.DINOSAUR)
                        .map(DNAData::species).orElse("invalid")));
        for (var item : List.of(JurassicContent.AMBER.get(), JurassicContent.AMBER_APHID.get(), JurassicContent.SEA_LAMPREY.get()))
            registry.setDefaultComparison(item, extract);
        for (Species species : Species.values()) {
            registry.setDefaultComparison(DinosaurRegistry.rawMeat(species).get(), extract);
            registry.setDefaultComparison(GeneticsRegistry.softTissue(species).get(), tissue);
            registry.setDefaultComparison(GeneticsRegistry.dinosaurDna(species).get(),
                    Comparison.compareData(stack -> validDna(stack.getItemStack(), species)));
            registry.setDefaultComparison(HatcheryRegistry.syringe(species),
                    Comparison.compareData(stack -> validDna(stack.getItemStack(), species)));
            if (HatcheryRegistry.egg(species) != null)
                registry.setDefaultComparison(HatcheryRegistry.egg(species), Comparison.compareData(stack ->
                        !HatcheryRecipes.incubate(stack.getItemStack(), 0).isEmpty()));
            registry.setDefaultComparison(HatcheryRegistry.hatchedEgg(species), Comparison.compareData(stack -> {
                ItemStack item = stack.getItemStack();
                if (!validDna(item, species) || !item.getTag().contains("Gender")) return "invalid";
                return item.getTag().getBoolean("Gender") ? "male" : "female";
            }));
        }
        for (PlantSpecies species : PlantSpecies.values()) if (species.isPrehistoric()) {
            registry.setDefaultComparison(GeneticsRegistry.plantTissue(species).get(), tissue);
            registry.setDefaultComparison(GeneticsRegistry.plantDna(species).get(), Comparison.compareData(stack ->
                    Genetics.read(stack.getItemStack().getTag()).filter(dna -> dna.kind() == DNAData.Kind.PLANT
                            && dna.plant() == species && dna.quality() == 100).isPresent()));
        }
    }
    private static boolean validDna(ItemStack item, Species species) {
        return Genetics.read(item.getTag()).filter(dna -> dna.kind() == DNAData.Kind.DINOSAUR && dna.dinosaur() == species).isPresent();
    }

    static final class MachinePreview extends BasicEmiRecipe {
        private final RecipePreviews.Preview preview;
        private final List<EmiIngredient> outputSlots = new ArrayList<>();
        MachinePreview(EmiRecipeCategory category, ResourceLocation id, RecipePreviews.Preview preview) {
            super(category, id, 160, preview.kind() == RecipePreviews.Kind.SKELETON ? 132 : 116);
            this.preview = preview;
            for (List<ItemStack> options : preview.inputs()) inputs.add(ingredient(options));
            for (List<ItemStack> options : preview.outputs()) {
                outputSlots.add(ingredient(options));
                for (ItemStack option : options) if (!option.isEmpty()) outputs.add(EmiStack.of(option));
            }
        }
        RecipePreviews.Kind kind() { return preview.kind(); }
        private static EmiIngredient ingredient(List<ItemStack> options) {
            List<EmiStack> choices = options.stream().filter(stack -> !stack.isEmpty()).map(EmiStack::of).toList();
            return choices.isEmpty() ? EmiStack.EMPTY : EmiIngredient.of(choices);
        }
        @Override public boolean supportsRecipeTree() { return false; }
        @Override public void addWidgets(WidgetHolder widgets) {
            boolean skeleton = preview.kind() == RecipePreviews.Kind.SKELETON;
            for (int i = 0; i < inputs.size(); i++) {
                int x = skeleton ? 1 + i % 5 * 18 : 1 + i * 22;
                int y = skeleton ? 1 + i / 5 * 18 : 9;
                widgets.addSlot(inputs.get(i), x, y);
            }
            for (int i = 0; i < outputSlots.size(); i++)
                widgets.addSlot(outputSlots.get(i), 132, (skeleton ? 37 : 9) + i * 20).recipeContext(this);
            widgets.addText(Component.literal("\u2192"), 108, skeleton ? 40 : 12, 0x404040, false);
            int y = skeleton ? 96 : 36;
            for (var line : Minecraft.getInstance().font.split(Component.translatable("jei.jurassicraft.note." + preview.note()), 156)) {
                widgets.addText(line, 1, y, 0x404040, false);
                y += 10;
            }
        }
    }
}
