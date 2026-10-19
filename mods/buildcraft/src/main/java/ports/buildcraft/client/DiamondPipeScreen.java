package ports.buildcraft.client;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.transport.DiamondPipeMenu;

/** Upstream {@code GuiDiamondPipe}: one row of nine ghost filter slots per side. */
public final class DiamondPipeScreen extends BCScreen<DiamondPipeMenu> {
    public DiamondPipeScreen(DiamondPipeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, BuildCraft.id("textures/gui/diamond_pipe.png"), 175, 225);
        titleLabelY = 6;
    }
}
