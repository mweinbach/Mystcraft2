/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.patched;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;

/**
 * {@link AbstractContainerScreen} with Forge's getters as class methods. They are also injected into every container
 * screen as interface defaults, but a screen that implements an interface declaring one of them abstractly, as
 * shared screen code does, needs the class method Forge has.
 */
public abstract class ForgeAbstractContainerScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {

    protected ForgeAbstractContainerScreen(T menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Nullable
    @Override
    public Slot projecte$getSlotUnderMouse() {
        return hoveredSlot;
    }

    @Override
    public int projecte$getGuiLeft() {
        return leftPos;
    }

    @Override
    public int projecte$getGuiTop() {
        return topPos;
    }

    @Override
    public int projecte$getXSize() {
        return imageWidth;
    }

    @Override
    public int projecte$getYSize() {
        return imageHeight;
    }
}
