package ports.buildcraft.statement;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.DyeColor;

/**
 * Upstream {@code ColourUtil.getTextFullTooltip(EnumDyeColor)}: a dye colour's name in a text colour readable on
 * a dark background, with upstream's default (not high contrast) replacements.
 */
public final class ColourText {
    private static final ChatFormatting[] FORMATS = new ChatFormatting[16];

    static {
        set(DyeColor.WHITE, ChatFormatting.WHITE);
        set(DyeColor.ORANGE, ChatFormatting.GOLD);
        set(DyeColor.MAGENTA, ChatFormatting.LIGHT_PURPLE);
        set(DyeColor.LIGHT_BLUE, ChatFormatting.AQUA);
        set(DyeColor.YELLOW, ChatFormatting.YELLOW);
        set(DyeColor.LIME, ChatFormatting.GREEN);
        set(DyeColor.PINK, ChatFormatting.LIGHT_PURPLE);
        set(DyeColor.GRAY, ChatFormatting.GRAY);
        set(DyeColor.LIGHT_GRAY, ChatFormatting.GRAY);
        set(DyeColor.CYAN, ChatFormatting.DARK_AQUA);
        set(DyeColor.PURPLE, ChatFormatting.LIGHT_PURPLE);
        set(DyeColor.BLUE, ChatFormatting.BLUE);
        set(DyeColor.BROWN, ChatFormatting.GOLD);
        set(DyeColor.GREEN, ChatFormatting.GREEN);
        set(DyeColor.RED, ChatFormatting.RED);
        set(DyeColor.BLACK, ChatFormatting.GRAY);
    }

    private ColourText() {}

    private static void set(DyeColor colour, ChatFormatting format) {
        FORMATS[colour.getId()] = format;
    }

    public static MutableComponent of(DyeColor colour) {
        return Component.translatable("color.minecraft." + colour.getName()).withStyle(FORMATS[colour.getId()]);
    }

    /** The name upstream's 1.12 textures use for a colour, where light grey was silver. */
    public static String upstreamName(DyeColor colour) {
        return colour == DyeColor.LIGHT_GRAY ? "silver" : colour.getName();
    }
}
