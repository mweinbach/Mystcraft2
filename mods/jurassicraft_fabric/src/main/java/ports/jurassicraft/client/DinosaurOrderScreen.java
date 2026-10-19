package ports.jurassicraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Locale;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import ports.jurassicraft.entity.DinosaurEntity;
import ports.jurassicraft.entity.DinosaurOrderMenu;

/** The original three-button order selection for an imprinted dinosaur. */
public final class DinosaurOrderScreen extends AbstractContainerScreen<DinosaurOrderMenu> {
    public DinosaurOrderScreen(DinosaurOrderMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    @Override protected void init() {
        super.init();
        for (DinosaurEntity.Order order : DinosaurEntity.Order.values()) {
            Button button = new Button(width / 2 - 100, height / 4 + order.ordinal() * 30, 200, 20,
                    Component.translatable("order.jurassicraft." + order.name().toLowerCase(Locale.ROOT)), pressed -> {
                        if (minecraft != null && minecraft.gameMode != null && minecraft.player != null) {
                            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, order.ordinal());
                            minecraft.player.closeContainer();
                        }
                    });
            button.active = menu.dinosaur() != null && menu.dinosaur().getOrder() != order;
            addRenderableWidget(button);
        }
    }

    @Override public void render(PoseStack pose, int mouseX, int mouseY, float partialTick) {
        renderBackground(pose);
        super.render(pose, mouseX, mouseY, partialTick);
        drawCenteredString(pose, font, title, width / 2, height / 16, 0xffffff);
    }

    @Override protected void renderLabels(PoseStack pose, int mouseX, int mouseY) {}
    @Override protected void renderBg(PoseStack pose, float partialTick, int mouseX, int mouseY) {}
}
