package ports.jurassicraft.darts;

import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
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
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import ports.jurassicraft.PortMod;

@GameTestHolder(PortMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PotionDartGameTests {
    private PotionDartGameTests() {}
    @GameTest(template = "dna_machine_test", templateNamespace = PortMod.MOD_ID)
    public static void potionDartsRetainEffects(GameTestHelper helper) {
        AbstractContainerMenu menu = new AbstractContainerMenu(null, 0) {
            @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
            @Override public boolean stillValid(Player player) { return true; }
        };
        CraftingContainer grid = new CraftingContainer(menu, 3, 3);
        for (int i = 0; i < 9; i++) grid.setItem(i, new ItemStack(DartRegistry.TRANQUILIZER.get()));
        ItemStack potion = PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.SWIFTNESS);
        PotionUtils.setCustomEffects(potion, List.of(new MobEffectInstance(MobEffects.POISON, 120, 1)));
        potion.getOrCreateTag().putInt(PotionUtils.TAG_CUSTOM_POTION_COLOR, 0x123456);
        grid.setItem(4, potion);
        PotionDartRecipe recipe = new PotionDartRecipe(new ResourceLocation(PortMod.MOD_ID, "test"));
        if (!recipe.matches(grid, helper.getLevel())) helper.fail("valid potion recipe was rejected");
        ItemStack result = recipe.assemble(grid);
        if (!result.is(DartRegistry.POTION.get()) || result.getCount() != 8 || PotionUtils.getPotion(result) != Potions.SWIFTNESS
                || PotionUtils.getCustomEffects(result).stream().noneMatch(effect -> effect.getEffect() == MobEffects.POISON && effect.getAmplifier() == 1)
                || PotionUtils.getColor(result) != 0x123456) helper.fail("potion dart lost its effects or color");
        grid.setItem(0, new ItemStack(Items.ARROW));
        if (recipe.matches(grid, helper.getLevel())) helper.fail("non-dart ingredient was accepted");
        helper.succeed();
    }
}
