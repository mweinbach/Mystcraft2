package ports.buildcraft.statement;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Upstream {@code ColourUtil.getTextFullTooltip(EnumFacing)}: a face's name in its colour, readable on tooltips. */
public final class SideText {
    private static final ChatFormatting[] COLOURS = {
        ChatFormatting.GRAY, ChatFormatting.WHITE, ChatFormatting.RED, ChatFormatting.BLUE, ChatFormatting.GREEN, ChatFormatting.YELLOW
    };

    private SideText() {}

    public static MutableComponent of(Direction face) {
        return Component.translatable("direction." + face.getName()).withStyle(COLOURS[face.ordinal()]);
    }
}
