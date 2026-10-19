package ports.jurassicraft.plants;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Eating a date has a 50% chance to return a plantable seed. */
final class PhoenixFruitItem extends Item {
    PhoenixFruitItem(Properties properties) { super(properties); }
    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity eater) {
        ItemStack result = super.finishUsingItem(stack, level, eater);
        if (!level.isClientSide && eater instanceof Player player && level.random.nextBoolean()) {
            ItemStack seed = new ItemStack(PlantRegistry.PHOENIX_SEEDS.get());
            if (!player.addItem(seed)) player.drop(seed, false);
        }
        return result;
    }
    @Override
    public int getUseDuration(ItemStack stack) { return 14; }
}
