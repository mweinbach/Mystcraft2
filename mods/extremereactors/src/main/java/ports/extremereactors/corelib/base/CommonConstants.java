/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.base;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Style;

/**
 * Names of the block entity commands shared by the generic machine parts, and the tooltip styles their screens use.
 */
public final class CommonConstants {

    /**
     * Ask a charging port to eject the items it holds.
     */
    public static final String COMMAND_EJECT = "eject";

    /** Switch a machine on. */
    public static final String COMMAND_ACTIVATE = "activate";

    /** Switch a machine off. */
    public static final String COMMAND_DEACTIVATE = "deactivate";

    /** The first line of a tooltip. */
    public static final Style STYLE_TOOLTIP_TITLE = Style.EMPTY
            .withColor(ChatFormatting.YELLOW)
            .withBold(true);

    /** A value shown in a tooltip. */
    public static final Style STYLE_TOOLTIP_VALUE = Style.EMPTY
            .withColor(ChatFormatting.DARK_AQUA)
            .withBold(true);

    /** A remark shown in a tooltip. */
    public static final Style STYLE_TOOLTIP_INFO = Style.EMPTY
            .withColor(ChatFormatting.DARK_PURPLE)
            .withItalic(true);

    private CommonConstants() {
    }
}
