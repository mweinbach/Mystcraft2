package ports.jurassicraft.jei;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Collection;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.handlers.IGuiClickableArea;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.transfer.IRecipeTransferInfo;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraftforge.common.crafting.StrictNBTIngredient;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.assembly.AssemblyRegistry;
import ports.jurassicraft.assembly.SkeletonDisplayItem;
import ports.jurassicraft.assembly.SkeletonAssemblyMenu;
import ports.jurassicraft.assembly.client.SkeletonAssemblyScreen;
import ports.jurassicraft.bugs.BugCrateScreen;
import ports.jurassicraft.bugs.BugCrateMenu;
import ports.jurassicraft.bugs.BugRegistry;
import ports.jurassicraft.cleaning.client.CleaningStationScreen;
import ports.jurassicraft.cleaning.CleaningStationMenu;
import ports.jurassicraft.cleaning.CleaningStationRegistry;
import ports.jurassicraft.cultivator.CultivatorScreen;
import ports.jurassicraft.cultivator.CultivatorMenu;
import ports.jurassicraft.cultivator.CultivatorRegistry;
import ports.jurassicraft.genetics.GeneticsRegistry;
import ports.jurassicraft.genetics.Genetics;
import ports.jurassicraft.genetics.DNAData;
import ports.jurassicraft.content.JurassicContent;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.entity.DinosaurRegistry;
import ports.jurassicraft.figure.ActionFigureRegistry;
import ports.jurassicraft.plants.PlantSpecies;
import ports.jurassicraft.grinder.FossilGrinderScreen;
import ports.jurassicraft.grinder.FossilGrinderMenu;
import ports.jurassicraft.grinder.FossilGrinderRegistry;
import ports.jurassicraft.hatching.HatcheryScreen;
import ports.jurassicraft.hatching.HatcheryMenu;
import ports.jurassicraft.hatching.HatcheryRegistry;
import ports.jurassicraft.hatching.HatcheryRecipes;
import ports.jurassicraft.machine.DnaMachineScreen;
import ports.jurassicraft.machine.DnaMachineMenu;
import ports.jurassicraft.machine.DnaMachineKind;
import ports.jurassicraft.machine.DnaMachineRegistry;

/** Discovered by JEI only; common mod initialization never loads JEI classes. */
@JeiPlugin
public final class JurassiCraftJeiPlugin implements IModPlugin {
    private static final Map<RecipePreviews.Kind, RecipeType<RecipePreviews.Preview>> TYPES = new EnumMap<>(RecipePreviews.Kind.class);
    static {
        for (RecipePreviews.Kind kind : RecipePreviews.Kind.values())
            TYPES.put(kind, RecipeType.create(PortMod.MOD_ID, kind.id, RecipePreviews.Preview.class));
    }
    @Override public ResourceLocation getPluginUid() { return new ResourceLocation(PortMod.MOD_ID, "recipes"); }
    @Override public void registerItemSubtypes(ISubtypeRegistration registration) {
        registration.registerSubtypeInterpreter(GeneticsRegistry.STORAGE_DISC.get(), (stack, context) -> RecipePreviews.dnaSubtype(stack));
        registration.registerSubtypeInterpreter(ActionFigureRegistry.ITEM.get(), (stack, context) -> RecipePreviews.figureSubtype(stack));
        registration.registerSubtypeInterpreter(AssemblyRegistry.DISPLAY_ITEM.get(), (stack, context) -> {
            if (net.minecraft.world.item.BlockItem.getBlockEntityData(stack) == null) return "blank";
            return SkeletonDisplayItem.species(stack).id() + ":" + SkeletonDisplayItem.fresh(stack);
        });
        // Discard cosmetic metadata but keep distinctions that decide whether machines accept a stack.
        var extractorSubtype = (mezz.jei.api.ingredients.subtypes.IIngredientSubtypeInterpreter<ItemStack>)
                (stack, context) -> Genetics.extract(stack, RandomSource.create(0)).isEmpty() ? "invalid" : "valid";
        registration.registerSubtypeInterpreter(DinosaurRegistry.DINOSAUR_MEAT.get(), (stack, context) -> Genetics.read(stack.getTag())
                .filter(dna -> dna.kind() == DNAData.Kind.DINOSAUR).map(dna -> dna.species()).orElse("invalid"));
        for (var item : List.of(JurassicContent.AMBER.get(), JurassicContent.AMBER_APHID.get(), JurassicContent.SEA_LAMPREY.get()))
            registration.registerSubtypeInterpreter(item, extractorSubtype);
        var tissueSubtype = (mezz.jei.api.ingredients.subtypes.IIngredientSubtypeInterpreter<ItemStack>)
                (stack, context) -> Genetics.sequence(stack, RandomSource.create(0)).isEmpty() ? "invalid" : "valid";
        for (Species species : Species.values()) {
            registration.registerSubtypeInterpreter(DinosaurRegistry.rawMeat(species).get(), extractorSubtype);
            registration.registerSubtypeInterpreter(GeneticsRegistry.softTissue(species).get(), tissueSubtype);
            registration.registerSubtypeInterpreter(GeneticsRegistry.dinosaurDna(species).get(),
                    (stack, context) -> validDna(stack, species) ? "valid" : "invalid");
            registration.registerSubtypeInterpreter(HatcheryRegistry.syringe(species),
                    (stack, context) -> validDna(stack, species) ? "valid" : "invalid");
            if (HatcheryRegistry.egg(species) != null)
                registration.registerSubtypeInterpreter(HatcheryRegistry.egg(species),
                        (stack, context) -> HatcheryRecipes.incubate(stack, 0).isEmpty() ? "invalid" : "valid");
            registration.registerSubtypeInterpreter(HatcheryRegistry.hatchedEgg(species), (stack, context) -> {
                if (!validDna(stack, species) || !stack.getTag().contains("Gender")) return "invalid";
                return stack.getTag().getBoolean("Gender") ? "male" : "female";
            });
        }
        for (PlantSpecies species : PlantSpecies.values()) if (species.isPrehistoric()) {
            registration.registerSubtypeInterpreter(GeneticsRegistry.plantTissue(species).get(), tissueSubtype);
            registration.registerSubtypeInterpreter(GeneticsRegistry.plantDna(species).get(), (stack, context) -> Genetics.read(stack.getTag())
                    .filter(dna -> dna.kind() == DNAData.Kind.PLANT && dna.plant() == species && dna.quality() == 100)
                    .isPresent() ? "complete" : "invalid");
        }
    }
    private static boolean validDna(ItemStack stack, Species species) {
        return Genetics.read(stack.getTag()).filter(dna -> dna.kind() == DNAData.Kind.DINOSAUR && dna.dinosaur() == species).isPresent();
    }
    @Override public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper gui = registration.getJeiHelpers().getGuiHelper();
        for (RecipePreviews.Kind kind : RecipePreviews.Kind.values()) registration.addRecipeCategories(new Category(kind, gui));
    }
    @Override public void registerRecipes(IRecipeRegistration registration) {
        RecipePreviews.build().forEach((kind, previews) -> registration.addRecipes(TYPES.get(kind), previews));
        List<CraftingRecipe> figures = new ArrayList<>();
        for (var cycle : RecipePreviews.figureCycles()) {
            NonNullList<Ingredient> ingredients = NonNullList.create();
            ingredients.add(StrictNBTIngredient.of(cycle.input()));
            figures.add(new ShapelessRecipe(new ResourceLocation(PortMod.MOD_ID,
                    "jei/action_figure_cycle/" + RecipePreviews.figureSubtype(cycle.input())), "", cycle.output(), ingredients));
        }
        registration.addRecipes(RecipeTypes.CRAFTING, figures);
        registration.addItemStackInfo(RecipePreviews.figureCycles().stream().map(RecipePreviews.FigureCycle::input).toList(),
                Component.translatable("jei.jurassicraft.note.figure_cycle"));
    }
    @Override public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        for (RecipePreviews.Kind kind : RecipePreviews.Kind.values()) registration.addRecipeCatalyst(RecipePreviews.catalyst(kind), TYPES.get(kind));
    }
    @Override public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        transfer(registration, FossilGrinderMenu.class, FossilGrinderRegistry.FOSSIL_GRINDER_MENU.get(), RecipePreviews.Kind.GRINDING, m -> true, 12, 0);
        transfer(registration, CleaningStationMenu.class, CleaningStationRegistry.MENU.get(), RecipePreviews.Kind.CLEANING, m -> true, 8, 0);
        transfer(registration, BugCrateMenu.class, BugRegistry.MENU.get(), RecipePreviews.Kind.BREEDING, m -> true, 9, 0, 3);
        int[] grid = new int[25];
        for (int i = 0; i < grid.length; i++) grid[i] = i + 1;
        transfer(registration, SkeletonAssemblyMenu.class, AssemblyRegistry.MENU.get(), RecipePreviews.Kind.SKELETON, m -> true, 26, grid);
        transfer(registration, DnaMachineMenu.class, DnaMachineRegistry.MENU.get(), RecipePreviews.Kind.EXTRACTING,
                m -> m.kind() == DnaMachineKind.EXTRACTOR, DnaMachineKind.EXTRACTOR.slots, 0, 1);
        transfer(registration, DnaMachineMenu.class, DnaMachineRegistry.MENU.get(), RecipePreviews.Kind.SEQUENCING,
                m -> m.kind() == DnaMachineKind.SEQUENCER, DnaMachineKind.SEQUENCER.slots, 0, 1);
        transfer(registration, DnaMachineMenu.class, DnaMachineRegistry.MENU.get(), RecipePreviews.Kind.COMBINING,
                m -> m.kind() == DnaMachineKind.COMBINATOR && !m.hybridizerMode(), DnaMachineKind.COMBINATOR.slots, 0, 1);
        transfer(registration, DnaMachineMenu.class, DnaMachineRegistry.MENU.get(), RecipePreviews.Kind.SYNTHESIZING,
                m -> m.kind() == DnaMachineKind.SYNTHESIZER, DnaMachineKind.SYNTHESIZER.slots, 0, 1, 2);
        transfer(registration, HatcheryMenu.class, HatcheryRegistry.menu(HatcheryRegistry.Machine.EMBRYONIC), RecipePreviews.Kind.EMBRYONIC,
                m -> m.machine() == HatcheryRegistry.Machine.EMBRYONIC, 7, 0, 1, 2);
        transfer(registration, HatcheryMenu.class, HatcheryRegistry.menu(HatcheryRegistry.Machine.CALCIFIER), RecipePreviews.Kind.CALCIFYING,
                m -> m.machine() == HatcheryRegistry.Machine.CALCIFIER, 3, 0, 1);
        transfer(registration, HatcheryMenu.class, HatcheryRegistry.menu(HatcheryRegistry.Machine.INCUBATOR), RecipePreviews.Kind.INCUBATING,
                m -> m.machine() == HatcheryRegistry.Machine.INCUBATOR, 6, 0, 5);
        registerCultivatorTransfer(registration);
    }
    private static void registerCultivatorTransfer(IRecipeTransferRegistration registration) {
        var helper = registration.getTransferHelper();
        var info = helper.createBasicRecipeTransferInfo(CultivatorMenu.class, CultivatorRegistry.MENU.get(),
                TYPES.get(RecipePreviews.Kind.CULTIVATING), 0, 1, 4, 36);
        var delegate = helper.createUnregisteredRecipeTransferHandler(info);
        registration.addRecipeTransferHandler(new IRecipeTransferHandler<CultivatorMenu, RecipePreviews.Preview>() {
            @Override public Class<? extends CultivatorMenu> getContainerClass() { return CultivatorMenu.class; }
            @Override public Optional<MenuType<CultivatorMenu>> getMenuType() { return Optional.of(CultivatorRegistry.MENU.get()); }
            @Override public RecipeType<RecipePreviews.Preview> getRecipeType() { return TYPES.get(RecipePreviews.Kind.CULTIVATING); }
            @Override public IRecipeTransferError transferRecipe(CultivatorMenu menu, RecipePreviews.Preview recipe,
                    IRecipeSlotsView slots, Player player, boolean maxTransfer, boolean doTransfer) {
                var inputs = slots.getSlotViews(RecipeIngredientRole.INPUT);
                if (inputs.isEmpty()) return helper.createInternalError();
                // The water and nutrient tanks must be filled over multiple ticks and are never recipe-grid slots.
                var syringe = helper.createRecipeSlotsView(List.of(inputs.get(0)));
                return delegate.transferRecipe(menu, recipe, syringe, player, maxTransfer, doTransfer);
            }
        }, TYPES.get(RecipePreviews.Kind.CULTIVATING));
    }
    private static <C extends AbstractContainerMenu> void transfer(IRecipeTransferRegistration registration, Class<C> cls,
            MenuType<C> menu, RecipePreviews.Kind kind, Predicate<C> active, int inventoryStart, int... recipeSlots) {
        registration.addRecipeTransferHandler(new IRecipeTransferInfo<C, RecipePreviews.Preview>() {
            @Override public Class<? extends C> getContainerClass() { return cls; }
            @Override public Optional<MenuType<C>> getMenuType() { return Optional.of(menu); }
            @Override public RecipeType<RecipePreviews.Preview> getRecipeType() { return TYPES.get(kind); }
            @Override public boolean canHandle(C container, RecipePreviews.Preview recipe) { return active.test(container); }
            @Override public List<Slot> getRecipeSlots(C container, RecipePreviews.Preview recipe) {
                List<Slot> slots = new ArrayList<>();
                for (int index : recipeSlots) slots.add(container.slots.get(index));
                return slots;
            }
            @Override public List<Slot> getInventorySlots(C container, RecipePreviews.Preview recipe) {
                return container.slots.subList(inventoryStart, inventoryStart + 36);
            }
        });
    }
    @Override public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiContainerHandler(DnaMachineScreen.class, new IGuiContainerHandler<DnaMachineScreen>() {
            @Override public Collection<IGuiClickableArea> getGuiClickableAreas(DnaMachineScreen screen, double x, double y) {
                if (screen.getMenu().hybridizerMode()) return List.of();
                RecipePreviews.Kind kind = switch (screen.getMenu().kind()) {
                    case EXTRACTOR -> RecipePreviews.Kind.EXTRACTING;
                    case SEQUENCER -> RecipePreviews.Kind.SEQUENCING;
                    case COMBINATOR -> RecipePreviews.Kind.COMBINING;
                    case SYNTHESIZER -> RecipePreviews.Kind.SYNTHESIZING;
                };
                return switch (screen.getMenu().kind()) {
                    case EXTRACTOR -> List.of(IGuiClickableArea.createBasic(77, 36, 24, 16, TYPES.get(kind)));
                    case SEQUENCER -> List.of(IGuiClickableArea.createBasic(87, 21, 22, 6, TYPES.get(kind)),
                            IGuiClickableArea.createBasic(87, 41, 22, 6, TYPES.get(kind)),
                            IGuiClickableArea.createBasic(87, 61, 22, 6, TYPES.get(kind)));
                    case COMBINATOR -> List.of(IGuiClickableArea.createBasic(93, 30, 8, 24, TYPES.get(kind)));
                    case SYNTHESIZER -> List.of(IGuiClickableArea.createBasic(79, 34, 24, 16, TYPES.get(kind)));
                };
            }
        });
        registration.addGuiContainerHandler(HatcheryScreen.class, new IGuiContainerHandler<HatcheryScreen>() {
            @Override public Collection<IGuiClickableArea> getGuiClickableAreas(HatcheryScreen screen, double x, double y) {
                RecipePreviews.Kind kind = switch (screen.getMenu().machine()) {
                    case EMBRYONIC -> RecipePreviews.Kind.EMBRYONIC;
                    case CALCIFIER -> RecipePreviews.Kind.CALCIFYING;
                    case INCUBATOR -> RecipePreviews.Kind.INCUBATING;
                };
                return switch (screen.getMenu().machine()) {
                    case EMBRYONIC -> List.of(IGuiClickableArea.createBasic(79, 34, 24, 16, TYPES.get(kind)));
                    case CALCIFIER -> List.of(IGuiClickableArea.createBasic(67, 31, 24, 16, TYPES.get(kind)));
                    // Leave the five egg slots, their temperature sliders and the peat slot interactive.
                    case INCUBATOR -> List.of(IGuiClickableArea.createBasic(5, 49, 16, 16, TYPES.get(kind)));
                };
            }
        });
        registration.addRecipeClickArea(FossilGrinderScreen.class, 80, 32, 24, 17, TYPES.get(RecipePreviews.Kind.GRINDING));
        registration.addRecipeClickArea(CleaningStationScreen.class, 79, 34, 24, 16, TYPES.get(RecipePreviews.Kind.CLEANING));
        registration.addRecipeClickArea(SkeletonAssemblyScreen.class, 110, 49, 22, 18, TYPES.get(RecipePreviews.Kind.SKELETON));
        registration.addRecipeClickArea(BugCrateScreen.class, 88, 35, 24, 17, TYPES.get(RecipePreviews.Kind.BREEDING));
        registration.addRecipeClickArea(CultivatorScreen.class, 95, 49, 22, 16, TYPES.get(RecipePreviews.Kind.CULTIVATING));
    }

    private static final class Category implements IRecipeCategory<RecipePreviews.Preview> {
        private final RecipePreviews.Kind kind;
        private final IDrawable background, icon, slot;
        Category(RecipePreviews.Kind kind, IGuiHelper gui) {
            this.kind = kind;
            background = gui.createBlankDrawable(160, kind == RecipePreviews.Kind.SKELETON ? 132 : 116);
            icon = gui.createDrawableItemStack(RecipePreviews.catalyst(kind));
            slot = gui.getSlotDrawable();
        }
        @Override public RecipeType<RecipePreviews.Preview> getRecipeType() { return TYPES.get(kind); }
        @Override public Component getTitle() { return Component.translatable("jei.jurassicraft.category." + kind.id); }
        @Override public IDrawable getBackground() { return background; }
        @Override public IDrawable getIcon() { return icon; }
        @Override public void setRecipe(IRecipeLayoutBuilder builder, RecipePreviews.Preview recipe, IFocusGroup focuses) {
            for (int i = 0; i < recipe.inputs().size(); i++) {
                List<ItemStack> options = recipe.inputs().get(i);
                int x = kind == RecipePreviews.Kind.SKELETON ? 1 + i % 5 * 18 : 1 + i * 22;
                int y = kind == RecipePreviews.Kind.SKELETON ? 1 + i / 5 * 18 : 9;
                var input = builder.addSlot(RecipeIngredientRole.INPUT, x, y).setBackground(slot, -1, -1);
                if (!options.isEmpty()) input.addItemStacks(options);
            }
            for (int i = 0; i < recipe.outputs().size(); i++) {
                var output = builder.addSlot(RecipeIngredientRole.OUTPUT, 132, (kind == RecipePreviews.Kind.SKELETON ? 37 : 9) + i * 20)
                        .setBackground(slot, -1, -1).addItemStacks(recipe.outputs().get(i));
                if (kind == RecipePreviews.Kind.INCUBATING || kind == RecipePreviews.Kind.CULTIVATING)
                    output.addTooltipCallback((view, tooltip) -> view.getDisplayedItemStack().ifPresent(stack -> {
                        if (stack.hasTag() && stack.getTag().contains("Gender")) tooltip.add(Component.translatable(
                                stack.getTag().getBoolean("Gender") ? "tooltip.jurassicraft.incubator_male" : "tooltip.jurassicraft.incubator_female"));
                    }));
            }
        }
        @Override public void draw(RecipePreviews.Preview recipe, IRecipeSlotsView slots, PoseStack pose, double mouseX, double mouseY) {
            var font = Minecraft.getInstance().font;
            int y = kind == RecipePreviews.Kind.SKELETON ? 96 : 36;
            font.draw(pose, "\u2192", 108, kind == RecipePreviews.Kind.SKELETON ? 40 : 12, 0x404040);
            for (var line : font.split(Component.translatable("jei.jurassicraft.note." + recipe.note()), 156)) {
                font.draw(pose, line, 1, y, 0x404040);
                y += 10;
            }
        }
    }
}
