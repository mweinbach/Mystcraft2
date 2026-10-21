package ports.buildcraft.statement;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.DyeColor;

/**
 * Upstream {@code ColourUtil.getTextFullTooltip(EnumDyeColor)}: a dye colour's name in a text colour readable on
 * a dark background, with upstream's default (not high contrast) replacements, and {@code getLightHex}.
 */
public final class ColourText {
    private static final ChatFormatting[] FORMATS = new ChatFormatting[16];
    /** Upstream {@code LIGHT_HEX}, by dye damage: black first, white last. */
    private static final int[] LIGHT_HEX = {
        0x181414, 0xBE2B27, 0x007F0E, 0x89502D,
        0x253193, 0x7E34BF, 0x299799, 0xA0A7A7,
        0x7A7A7A, 0xD97199, 0x39D52E, 0xFFD91C,
        0x66AAFF, 0xD943C6, 0xEA7835, 0xE4E4E4,
    };

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

    /** Upstream {@code getLightHex}: the RGB colour lenses and painted items are drawn in. */
    public static int lightHex(DyeColor colour) {
        return LIGHT_HEX[15 - colour.getId()];
    }

    /** The name upstream's 1.12 textures use for a colour, where light grey was silver. */
    public static String upstreamName(DyeColor colour) {
        return colour == DyeColor.LIGHT_GRAY ? "silver" : colour.getName();
    }
}
