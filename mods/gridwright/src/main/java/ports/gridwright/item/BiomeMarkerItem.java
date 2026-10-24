package ports.gridwright.item;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.ChatFormatting;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Records the biome its user stands in when used. In a quantum quarry's marker slot it picks the
 * virtual world the quarry mines; using it again somewhere else overwrites the biome.
 */
public class BiomeMarkerItem extends Item {

    public static final String BIOME = "Biome";

    public BiomeMarkerItem(Properties properties) {
        super(properties);
    }

    /** The biome recorded on {@code stack}, or null if it is blank. */
    @Nullable
    public static ResourceLocation biome(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(BIOME)) return null;
        return ResourceLocation.tryParse(tag.getString(BIOME));
    }

    public static ItemStack withBiome(ItemStack stack, ResourceLocation biome) {
        stack.getOrCreateTag().putString(BIOME, biome.toString());
        return stack;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            BlockPos pos = player.blockPosition();
            level.getBiome(pos).unwrapKey().ifPresent(key -> {
                withBiome(stack, key.location());
                player.displayClientMessage(Component.translatable("message.gridwright.biome_marker.recorded", name(key.location())), true);
            });
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return biome(stack) != null;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        ResourceLocation biome = biome(stack);
        tooltip.add(biome == null ? Component.translatable("item.gridwright.biome_marker.blank").withStyle(ChatFormatting.GRAY)
                : Component.translatable("item.gridwright.biome_marker.biome", name(biome)).withStyle(ChatFormatting.GRAY));
    }

    private static Component name(ResourceLocation biome) {
        return Component.translatable("biome." + biome.getNamespace() + "." + biome.getPath());
    }
}
