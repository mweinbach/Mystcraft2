package net.mystcraft.minecraft.client;

import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.mystcraft.minecraft.portal.PortalController;

/** Classic DimensionUtils.getLinkColor: three bytes seeded by the link display name. */
public final class ClassicPortalColor {
    private ClassicPortalColor() {}
    public static int color(BlockAndTintGetter view, BlockPos pos) {
        if (view == null || pos == null) return 0x3333FF;
        var level = Minecraft.getInstance().level;
        if (level == null || !level.hasChunkAt(pos)) return 0xFFFFFF;
        var source = PortalController.source(level, pos);
        if (source == null || source.getBook().isEmpty()) return 0xFFFFFF;
        return bookColor(source.getBook());
    }
    public static int bookColor(ItemStack book) {
        CompoundTag tag = book.getTag();
        String name = tag != null && tag.contains("DisplayName", 8) ? tag.getString("DisplayName")
                : tag != null && tag.contains("agename", 8) ? tag.getString("agename")
                : book.hasCustomHoverName() ? book.getHoverName().getString() : "???";
        Random random = new Random(name.hashCode());
        return random.nextInt(256) + (random.nextInt(256) << 8) + (random.nextInt(256) << 16);
    }
}
