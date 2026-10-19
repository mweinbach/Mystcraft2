package ports.jurassicraft.fieldguide;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;
import ports.jurassicraft.entity.DinosaurEntity;

/** Opens the field guide normally or examines a dinosaur when used on it. */
public final class FieldGuideItem extends Item {
    private static Runnable manualOpener = () -> {};
    public FieldGuideItem(Properties properties) { super(properties); }
    /** Installed by the client; the manual is purely local, as in the original guide. */
    public static void setManualOpener(Runnable opener) { manualOpener = opener; }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) manualOpener.run();
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof DinosaurEntity dinosaur)) return InteractionResult.PASS;
        if (!player.level.isClientSide && player instanceof ServerPlayer server) FieldGuideNetwork.inspect(server, dinosaur);
        return InteractionResult.sidedSuccess(player.level.isClientSide);
    }
}
