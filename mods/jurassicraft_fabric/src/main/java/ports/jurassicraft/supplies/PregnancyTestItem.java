package ports.jurassicraft.supplies;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import ports.jurassicraft.entity.DinosaurEntity;

public final class PregnancyTestItem extends Item {
    public PregnancyTestItem(Properties properties) { super(properties); }

    @Override public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof DinosaurEntity dinosaur)) return InteractionResult.PASS;
        if (!player.level.isClientSide) {
            String result = dinosaur.isMale() ? "male" : dinosaur.isPregnant() ? "pregnant" : "not_pregnant";
            player.displayClientMessage(Component.translatable("message.jurassicraft.pregnancy_test." + result), false);
        }
        return InteractionResult.sidedSuccess(player.level.isClientSide);
    }
}
