package ports.jurassicraft.supplies;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import ports.jurassicraft.entity.DinosaurEntity;

/** Select two compatible adults to begin a pregnancy, bypassing the normal cooldown. */
public final class BreedingWandItem extends Item {
    private static final String SELECTED = "SelectedDinosaur";
    public BreedingWandItem(Properties properties) { super(properties); }
    @Override public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof DinosaurEntity dinosaur)) return InteractionResult.PASS;
        if (player.level instanceof ServerLevel level) {
            var tag = stack.getOrCreateTag();
            var selected = tag.hasUUID(SELECTED) ? level.getEntity(tag.getUUID(SELECTED)) : null;
            String result;
            if (!(selected instanceof DinosaurEntity first) || !first.isAlive()) {
                tag.putUUID(SELECTED, dinosaur.getUUID());
                result = "selected";
            } else {
                result = first.forceBreedWith(dinosaur) ? "success" : "invalid";
                tag.remove(SELECTED);
            }
            player.displayClientMessage(Component.translatable("message.jurassicraft.breeding_wand." + result), true);
        }
        return InteractionResult.sidedSuccess(player.level.isClientSide);
    }
}
