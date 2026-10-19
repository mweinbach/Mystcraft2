package ports.jurassicraft.darts;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import java.util.List;
import javax.annotation.Nullable;

/** First use loads up to twelve darts; subsequent uses fire the loaded darts. */
public final class DartGunItem extends Item {
    private static final String AMMO = "LoadedDarts";
    public DartGunItem(Properties properties) { super(properties); }

    @Override public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.jurassicraft.dart_gun").withStyle(ChatFormatting.GRAY));
    }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack gun = player.getItemInHand(hand);
        if (!level.isClientSide) {
            ItemStack dart = gun.hasTag() && gun.getTag().contains(AMMO) ? ItemStack.of(gun.getTag().getCompound(AMMO)) : ItemStack.EMPTY;
            if (!(dart.getItem() instanceof DartItem)) dart = ItemStack.EMPTY;
            if (dart.isEmpty()) {
                for (int i = player.getInventory().getContainerSize() - 1; i >= 0; i--) {
                    ItemStack candidate = player.getInventory().getItem(i);
                    if (candidate.getItem() instanceof DartItem) {
                        dart = candidate.split(Math.min(12, candidate.getCount()));
                        player.getInventory().setChanged();
                        break;
                    }
                }
                level.playSound(null, player.blockPosition(), dart.isEmpty() ? SoundEvents.COMPARATOR_CLICK : SoundEvents.ITEM_PICKUP,
                        SoundSource.PLAYERS, .5F, 1F);
            } else {
                dart.setCount(Math.min(12, dart.getCount()));
                DartProjectile projectile = new DartProjectile(level, player, dart);
                projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0, 2.5F, .5F);
                if (level.addFreshEntity(projectile)) dart.shrink(1);
                level.playSound(null, player.blockPosition(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, .5F, 1F);
            }
            if (dart.isEmpty()) gun.getOrCreateTag().remove(AMMO);
            else gun.getOrCreateTag().put(AMMO, dart.save(new CompoundTag()));
            player.awardStat(Stats.ITEM_USED.get(this));
            player.getCooldowns().addCooldown(this, 5);
        }
        return InteractionResultHolder.sidedSuccess(gun, level.isClientSide);
    }
}
