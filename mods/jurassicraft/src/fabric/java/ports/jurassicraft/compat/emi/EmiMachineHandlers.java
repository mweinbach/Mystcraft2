package ports.jurassicraft.compat.emi;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.recipe.handler.StandardRecipeHandler;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import ports.jurassicraft.assembly.AssemblyRegistry;
import ports.jurassicraft.assembly.SkeletonAssemblyMenu;
import ports.jurassicraft.bugs.BugCrateMenu;
import ports.jurassicraft.bugs.BugRegistry;
import ports.jurassicraft.cleaning.CleaningStationMenu;
import ports.jurassicraft.cleaning.CleaningStationRegistry;
import ports.jurassicraft.compat.emi.RecipePreviews.Kind;
import ports.jurassicraft.cultivator.CultivatorMenu;
import ports.jurassicraft.cultivator.CultivatorRegistry;
import ports.jurassicraft.grinder.FossilGrinderMenu;
import ports.jurassicraft.grinder.FossilGrinderRegistry;
import ports.jurassicraft.hatching.HatcheryMenu;
import ports.jurassicraft.hatching.HatcheryRegistry;
import ports.jurassicraft.machine.DnaMachineMenu;
import ports.jurassicraft.machine.DnaMachineRegistry;

/** EMI's normal server-validated menu filling, limited to each machine's actual input slots. */
final class EmiMachineHandlers {
    private EmiMachineHandlers() {}
    static void register(EmiRegistry registry) {
        registry.addRecipeHandler(FossilGrinderRegistry.FOSSIL_GRINDER_MENU.get(),
                new Handler<FossilGrinderMenu>(Set.of(Kind.GRINDING), menu -> Kind.GRINDING));
        registry.addRecipeHandler(CleaningStationRegistry.MENU.get(),
                new Handler<CleaningStationMenu>(Set.of(Kind.CLEANING), menu -> Kind.CLEANING));
        registry.addRecipeHandler(BugRegistry.MENU.get(),
                new Handler<BugCrateMenu>(Set.of(Kind.BREEDING), menu -> Kind.BREEDING));
        registry.addRecipeHandler(AssemblyRegistry.MENU.get(),
                new Handler<SkeletonAssemblyMenu>(Set.of(Kind.SKELETON), menu -> Kind.SKELETON));
        registry.addRecipeHandler(CultivatorRegistry.MENU.get(),
                new Handler<CultivatorMenu>(Set.of(Kind.CULTIVATING), menu -> Kind.CULTIVATING));
        registry.addRecipeHandler(DnaMachineRegistry.MENU.get(),
                new Handler<DnaMachineMenu>(Set.of(Kind.EXTRACTING, Kind.SEQUENCING, Kind.COMBINING, Kind.SYNTHESIZING),
                        menu -> EmiRouting.dna(menu.kind(), menu.hybridizerMode())));
        for (HatcheryRegistry.Machine machine : HatcheryRegistry.Machine.values()) {
            Kind kind = EmiRouting.hatchery(machine);
            registry.addRecipeHandler(HatcheryRegistry.menu(machine),
                    new Handler<HatcheryMenu>(Set.of(kind), menu -> EmiRouting.hatchery(menu.machine())));
        }
    }

    private static final class Handler<T extends AbstractContainerMenu> implements StandardRecipeHandler<T> {
        private final Set<Kind> supported;
        private final Function<T, Kind> active;
        Handler(Set<Kind> supported, Function<T, Kind> active) { this.supported = supported; this.active = active; }
        @Override public boolean supportsRecipe(EmiRecipe recipe) {
            return recipe instanceof JurassicEmiPlugin.MachinePreview preview && supported.contains(preview.kind());
        }
        private boolean matches(EmiRecipe recipe, T menu) {
            return recipe instanceof JurassicEmiPlugin.MachinePreview preview && active.apply(menu) == preview.kind();
        }
        @Override public List<Slot> getInputSources(T menu) {
            Kind kind = active.apply(menu);
            EmiRouting.Plan plan = kind == null ? null : EmiRouting.plan(kind);
            if (plan == null || menu.slots.size() < plan.inventoryStart() + 36) return List.of();
            List<Slot> sources = new ArrayList<>(menu.slots.subList(plan.inventoryStart(), plan.inventoryStart() + 36));
            sources.addAll(getCraftingSlots(menu));
            return sources;
        }
        @Override public List<Slot> getCraftingSlots(T menu) {
            Kind kind = active.apply(menu);
            EmiRouting.Plan plan = kind == null ? null : EmiRouting.plan(kind);
            if (plan == null) return List.of();
            List<Slot> slots = new ArrayList<>(plan.targetSlots().size());
            for (int index : plan.targetSlots()) {
                if (index < 0 || index >= plan.inventoryStart() || index >= menu.slots.size()) return List.of();
                slots.add(menu.slots.get(index));
            }
            return slots;
        }
        @Override public boolean canCraft(EmiRecipe recipe, EmiCraftContext<T> context) {
            return matches(recipe, context.getScreenHandler()) && context.getInventory().canCraft(transferView(recipe));
        }
        @Override public boolean craft(EmiRecipe recipe, EmiCraftContext<T> context) {
            return matches(recipe, context.getScreenHandler())
                    && StandardRecipeHandler.super.craft(transferView(recipe), context);
        }
        private EmiRecipe transferView(EmiRecipe recipe) {
            if (!(recipe instanceof JurassicEmiPlugin.MachinePreview preview) || preview.kind() != Kind.CULTIVATING)
                return recipe;
            // Water and nutrients fill internal tanks over time; only the syringe is a recipe input slot.
            return new SyringeOnly(recipe);
        }
    }

    private record SyringeOnly(EmiRecipe original) implements EmiRecipe {
        @Override public EmiRecipeCategory getCategory() { return original.getCategory(); }
        @Override public ResourceLocation getId() { return original.getId(); }
        @Override public List<EmiIngredient> getInputs() { return original.getInputs().subList(0, 1); }
        @Override public List<EmiStack> getOutputs() { return original.getOutputs(); }
        @Override public int getDisplayWidth() { return original.getDisplayWidth(); }
        @Override public int getDisplayHeight() { return original.getDisplayHeight(); }
        @Override public void addWidgets(WidgetHolder widgets) { original.addWidgets(widgets); }
        @Override public boolean supportsRecipeTree() { return false; }
    }
}
