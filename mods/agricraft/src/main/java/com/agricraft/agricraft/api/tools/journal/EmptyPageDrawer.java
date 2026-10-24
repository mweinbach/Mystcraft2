package com.agricraft.agricraft.api.tools.journal;

import net.minecraft.client.Minecraft;
import com.agricraft.agricraft.client.gui.AgriGuiGraphics;
import net.minecraft.network.chat.Component;

public final class EmptyPageDrawer implements JournalPageDrawer<JournalPage> {

	public static final EmptyPageDrawer INSTANCE = new EmptyPageDrawer();

	@Override
	public void drawLeftSheet(AgriGuiGraphics guiGraphics, JournalPage page, int pageX, int pageY, JournalData journalData) {
		guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("missing journal page drawer: " + page.getDrawerId().toString()), pageX, pageY, 0xFFFFFFFF);
	}

	@Override
	public void drawRightSheet(AgriGuiGraphics guiGraphics, JournalPage page, int pageX, int pageY, JournalData journalData) {
		guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("missing journal page drawer: " + page.getDrawerId().toString()), pageX, pageY, 0xFFFFFFFF);
	}

}
