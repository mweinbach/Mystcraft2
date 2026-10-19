package ports.jurassicraft.decor;

import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import ports.jurassicraft.content.JurassicContent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import ports.jurassicraft.decor.client.PaddockSignScreen;

public final class ParkSignItem extends Item {
    private final boolean attraction;
    public ParkSignItem(boolean attraction) {
        super(new Item.Properties().tab(JurassicContent.TAB));
        this.attraction = attraction;
    }
    @Override public InteractionResult useOn(UseOnContext context) {
        Direction side = context.getClickedFace();
        Player player = context.getPlayer();
        if (side.getAxis().isVertical() || player == null || player.isSpectator()) return InteractionResult.PASS;
        var pos = context.getClickedPos().relative(side);
        if (!player.mayUseItemAt(pos, side, context.getItemInHand())) return InteractionResult.FAIL;
        Level level = context.getLevel();
        if (!attraction) {
            if (level.isClientSide) {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> PaddockSignScreen.open(pos, side, context.getHand()));
            } else {
                ParkSignNetwork.begin(player, pos, side, context.getHand());
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        ParkSignEntity sign = new ParkSignEntity(DecorRegistry.ATTRACTION_SIGN_ENTITY.get(), level, pos, side);
        String artwork = context.getItemInHand().hasTag() ? context.getItemInHand().getTag().getString("Artwork") : "";
        sign.setArtwork(artwork);
        if (!sign.survives()) return InteractionResult.PASS;
        if (!level.isClientSide) {
            if (!level.addFreshEntity(sign)) return InteractionResult.FAIL;
            if (!player.getAbilities().instabuild) context.getItemInHand().shrink(1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public Component getName(ItemStack stack) {
        if (!attraction) return super.getName(stack);
        String id = ParkSigns.validate(attraction, stack.hasTag() ? stack.getTag().getString("Artwork") : "");
        return Component.translatable("item.jurassicraft.sign_with_artwork", super.getName(stack), Component.translatable("jurassicraft.sign." + id));
    }
    @Override public void fillItemCategory(CreativeModeTab tab, NonNullList<ItemStack> items) {
        if (!allowedIn(tab)) return;
        if (!attraction) { items.add(new ItemStack(this)); return; }
        for (String artwork : ParkSigns.ATTRACTIONS) {
            ItemStack stack = new ItemStack(this);
            stack.getOrCreateTag().putString("Artwork", artwork);
            items.add(stack);
        }
    }
}
