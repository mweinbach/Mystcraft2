/*
 *
 * FluidizerSolidRecipe.java
 *
 * This file is part of Extreme Reactors 2 by ZeroNoRyouki, a Minecraft mod.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NON INFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 *
 * DO NOT REMOVE OR EDIT THIS HEADER
 *
 */

package it.zerono.mods.extremereactors.gamecontent.multiblock.fluidizer.recipe;

import it.zerono.mods.extremereactors.gamecontent.Content;
import ports.extremereactors.corelib.data.stack.OperationMode;
import ports.extremereactors.corelib.datagen.provider.recipe.OneToOneRecipeBuilder;
import ports.extremereactors.corelib.recipe.AbstractOneToOneRecipe;
import ports.extremereactors.corelib.recipe.ModRecipe;
import ports.extremereactors.corelib.recipe.holder.AbstractHeldRecipe;
import ports.extremereactors.corelib.recipe.holder.IRecipeHolder;
import ports.extremereactors.corelib.recipe.ingredient.IRecipeIngredient;
import ports.extremereactors.corelib.recipe.ingredient.IRecipeIngredientSource;
import ports.extremereactors.corelib.recipe.ingredient.ItemStackRecipeIngredient;
import ports.extremereactors.corelib.recipe.result.FluidStackRecipeResult;
import ports.extremereactors.corelib.recipe.result.IRecipeResult;
import ports.extremereactors.corelib.recipe.result.IRecipeResultTarget;
import ports.extremereactors.corelib.recipe.serializer.OneToOneRecipeSerializer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import ports.extremereactors.corelib.fluid.FluidStack;

public class FluidizerSolidRecipe
        extends AbstractOneToOneRecipe<ItemStack, FluidStack, ItemStackRecipeIngredient, FluidStackRecipeResult>
        implements IFluidizerRecipe {

    protected FluidizerSolidRecipe(final ResourceLocation id, final ItemStackRecipeIngredient ingredient,
                                   final FluidStackRecipeResult result) {

        super(id, ingredient, result);
        s_maxResultAmount = Math.max(s_maxResultAmount, result.getAmount());
    }

    public static boolean lookup(final ModRecipe recipe, final IRecipeIngredientSource<ItemStack> source) {
        return recipe instanceof FluidizerSolidRecipe && ((FluidizerSolidRecipe)recipe).test(source.getIngredient());
    }

    public static long getMaxResultAmount() {
        return s_maxResultAmount;
    }

    @Override
    public Type getRecipeType() {
        return Type.Solid;
    }

    public boolean match(final ItemStack stack) {
        return this.getIngredient().test(stack);
    }

    public boolean matchIgnoreAmount(final ItemStack stack) {
        return this.getIngredient().testIgnoreAmount(stack);
    }

    public static RecipeSerializer<FluidizerSolidRecipe> serializer() {
        return new OneToOneRecipeSerializer<>(FluidizerSolidRecipe::new,
                ItemStackRecipeIngredient::from, ItemStackRecipeIngredient::from,
                FluidStackRecipeResult::from, FluidStackRecipeResult::from);
    }

    public static OneToOneRecipeBuilder<ItemStack, FluidStack> builder(final IRecipeIngredient<ItemStack> ingredient,
                                                                       final IRecipeResult<FluidStack> result) {
        return new OneToOneRecipeBuilder<>(Type.Solid.getRecipeId(), ingredient, result);
    }

    //region IFluidizerRecipe

    @Override
    public int getEnergyUsageMultiplier() {
        return this.getEnergyUsageMultiplier(this.getResult().getResult());
    }

    //endregion
    //region AbstractOneToOneRecipe

    @Override
    public RecipeSerializer<FluidizerSolidRecipe> getSerializer() {
        return Content.Recipes.FLUIDIZER_SOLID_RECIPE_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return Content.Recipes.FLUIDIZER_RECIPE_TYPE;
    }

    //endregion
    //region held recipe

    public static class HeldRecipe
            extends AbstractHeldRecipe<FluidizerSolidRecipe>
            implements IFluidizerRecipe.Held<FluidizerSolidRecipe> {

        public <Holder extends IRecipeHolder<FluidizerSolidRecipe>>
        HeldRecipe(final FluidizerSolidRecipe recipe, final Holder holder,
                   final IRecipeIngredientSource<ItemStack> itemSource,
                   final IRecipeResultTarget<FluidStackRecipeResult> outputTarget) {

            super(recipe, holder);
            this._itemSource = itemSource;
            this._outputTarget = outputTarget;
        }

        //region AbstractHeldRecipe

        /**
         * Called after the recipe was completely processed.
         */
        @Override
        public void onRecipeProcessed() {

            final FluidizerSolidRecipe recipe = this.getRecipe();
            final ItemStack item = this._itemSource.getMatchFrom(recipe.getIngredient());

            if (!item.isEmpty()) {

                this._itemSource.consumeIngredient(item);
                this._outputTarget.setResult(recipe.getResult(), OperationMode.Execute);
            }
        }

        //endregion
        //region internals

        private final IRecipeIngredientSource<ItemStack> _itemSource;
        private final IRecipeResultTarget<FluidStackRecipeResult> _outputTarget;

        //endregion
    }

    //endregion
    //region internals

    private static long s_maxResultAmount = 0;

    //endregion
}
