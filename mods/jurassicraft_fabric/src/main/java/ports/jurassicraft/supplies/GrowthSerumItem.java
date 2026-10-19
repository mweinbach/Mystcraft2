package ports.jurassicraft.supplies;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import ports.jurassicraft.components.ComponentsRegistry;
import ports.jurassicraft.config.JurassicConfig;
import ports.jurassicraft.entity.DinosaurEntity;

/** Instantly grows a living dinosaur and returns its syringe. */
public final class GrowthSerumItem extends Item {
    public GrowthSerumItem(Properties properties) { super(properties); }

    @Override public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof DinosaurEntity dinosaur) || !dinosaur.isAlive()) return InteractionResult.PASS;
        if (!player.level.isClientSide) {
            if (JurassicConfig.DISABLE_GROWTH_SERUM.get()) {
                player.displayClientMessage(Component.translatable("item.growth_serum.denied").withStyle(ChatFormatting.RED), true);
                return InteractionResult.PASS;
            }
            dinosaur.setBaby(false);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
                ItemStack syringe = new ItemStack(ComponentsRegistry.EMPTY_SYRINGE.get());
                if (!player.addItem(syringe)) player.drop(syringe, false);
            }
        }
        return InteractionResult.sidedSuccess(player.level.isClientSide);
    }
}
