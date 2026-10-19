package ports.jurassicraft.validation;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.RecipeManager;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.darts.DartRegistry;
import ports.jurassicraft.darts.PotionDartRecipe;

/** Exercise the packaged custom recipe with initialized Forge registries during runData. */
final class PotionDartValidation {
    private PotionDartValidation() {}

    static void validate(Path recipePath, List<String> errors) {
        try (var reader = Files.newBufferedReader(recipePath)) {
            var decoded = RecipeManager.fromJson(new ResourceLocation(PortMod.MOD_ID, "dart_tipped_potion"),
                    JsonParser.parseReader(reader).getAsJsonObject());
            if (!(decoded instanceof PotionDartRecipe recipe)) {
                errors.add(recipePath + ": did not deserialize as PotionDartRecipe");
                return;
            }
            AbstractContainerMenu menu = new AbstractContainerMenu(null, 0) {
                @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
                @Override public boolean stillValid(Player player) { return true; }
            };
            CraftingContainer grid = new CraftingContainer(menu, 3, 3);
            for (int i = 0; i < 9; i++) grid.setItem(i, new ItemStack(DartRegistry.TRANQUILIZER.get()));
            ItemStack potion = PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.SWIFTNESS);
            List<MobEffectInstance> effects = List.of(
                    new MobEffectInstance(MobEffects.POISON, 120, 1, true, false, true),
                    new MobEffectInstance(MobEffects.REGENERATION, 75, 0, false, true, false));
            PotionUtils.setCustomEffects(potion, effects);
            potion.getOrCreateTag().putInt(PotionUtils.TAG_CUSTOM_POTION_COLOR, 0x123456);
            CompoundTag original = potion.getTag().copy();
            grid.setItem(4, potion);
            require(recipe.matches(grid, null), recipePath, errors, "valid potion and eight darts do not match");
            ItemStack result = recipe.assemble(grid);
            require(result.is(DartRegistry.POTION.get()) && result.getCount() == 8, recipePath, errors, "wrong result item or count");
            require(PotionUtils.getPotion(result) == Potions.SWIFTNESS, recipePath, errors, "base potion was not preserved");
            List<MobEffectInstance> copied = PotionUtils.getCustomEffects(result);
            boolean sameEffects = copied.size() == effects.size();
            for (int i = 0; sameEffects && i < effects.size(); i++)
                sameEffects = copied.get(i).save(new CompoundTag()).equals(effects.get(i).save(new CompoundTag()));
            require(sameEffects, recipePath, errors, "custom effects were not preserved");
            require(PotionUtils.getColor(result) == 0x123456, recipePath, errors, "custom potion color was not preserved");
            require(original.equals(potion.getTag()), recipePath, errors, "assembling modified the input potion");
            require(recipe.getRemainingItems(grid).stream().allMatch(ItemStack::isEmpty), recipePath, errors,
                    "the potion bottle and darts must be consumed without remaining items");
            for (int slot = 0; slot < 9; slot++) {
                ItemStack saved = grid.getItem(slot);
                grid.setItem(slot, new ItemStack(Items.ARROW));
                require(!recipe.matches(grid, null), recipePath, errors, "accepted a wrong ingredient in slot " + slot);
                grid.setItem(slot, ItemStack.EMPTY);
                require(!recipe.matches(grid, null), recipePath, errors, "accepted an empty slot " + slot);
                grid.setItem(slot, saved);
            }
            grid.setItem(4, new ItemStack(Items.SPLASH_POTION));
            require(!recipe.matches(grid, null), recipePath, errors, "accepted a splash potion");
            require(recipe.assemble(grid).isEmpty(), recipePath, errors, "assembled without a normal potion");
            require(!recipe.canCraftInDimensions(2, 2) && recipe.canCraftInDimensions(3, 3), recipePath, errors,
                    "incorrect crafting dimensions");
            CraftingContainer oversized = new CraftingContainer(menu, 4, 4);
            for (int i = 0; i < 16; i++) oversized.setItem(i, new ItemStack(DartRegistry.TRANQUILIZER.get()));
            oversized.setItem(5, potion);
            require(!recipe.matches(oversized, null), recipePath, errors, "accepted a grid larger than 3x3");
        } catch (Exception ex) {
            errors.add(recipePath + ": potion dart behavior validation failed: " + ex);
        }
    }

    private static void require(boolean condition, Path path, List<String> errors, String message) {
        if (!condition) errors.add(path + ": " + message);
    }
}
