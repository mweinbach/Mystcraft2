package ports.jurassicraft.supplies;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import ports.jurassicraft.entity.DinosaurEntity;

/** Accelerates an existing pregnancy without discarding blocked young. */
public final class BirthingWandItem extends Item {
    public BirthingWandItem(Properties properties) { super(properties); }
    @Override public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof DinosaurEntity dinosaur)) return InteractionResult.PASS;
        if (!player.level.isClientSide) {
            String result = dinosaur.deliverPregnancyNow() ? "success" : "invalid";
            player.displayClientMessage(Component.translatable("message.jurassicraft.birthing_wand." + result), true);
        }
        return InteractionResult.sidedSuccess(player.level.isClientSide);
    }
}
