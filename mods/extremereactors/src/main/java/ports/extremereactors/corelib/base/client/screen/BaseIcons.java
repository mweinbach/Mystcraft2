/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.base.client.screen;

import com.google.common.base.Suppliers;
import net.minecraft.resources.ResourceLocation;
import ports.extremereactors.corelib.client.gui.sprite.AtlasSpriteSupplier;
import ports.extremereactors.corelib.client.gui.sprite.AtlasSpriteTextureMap;
import ports.extremereactors.corelib.client.gui.sprite.ISprite;
import ports.extremereactors.corelib.client.gui.sprite.SpriteTextureMap;
import ports.extremereactors.platform.Platform;

import java.util.function.Supplier;

/**
 * The icons of the base widgets. They come from Extreme Reactors' own GUI icons and from vanilla textures.
 */
public final class BaseIcons {

    public static final Supplier<ISprite> Bucket = AtlasSpriteSupplier.create(
            new ResourceLocation("minecraft", "item/bucket"), AtlasSpriteTextureMap.BLOCKS);

    public static final Supplier<ISprite> PowerBattery = icon(128, 96, 16, 16);
    public static final Supplier<ISprite> BarBackground = icon(174, 126, 18, 66);
    public static final Supplier<ISprite> PowerBar = icon(0, 112, 16, 64);
    public static final Supplier<ISprite> MachineStatusOff = icon(32, 176, 10, 10);
    public static final Supplier<ISprite> MachineStatusOn = icon(48, 176, 10, 10);

    //region internals

    private static Supplier<ISprite> icon(final int x, final int y, final int width, final int height) {
        return Suppliers.memoize(() -> ICONS.get().sprite().from(x, y).ofSize(width, height).build());
    }

    private static final Supplier<SpriteTextureMap> ICONS = Suppliers.memoize(() -> new SpriteTextureMap(
            new ResourceLocation(Platform.MOD_ID, "textures/gui/multiblock/common_icons.png"), 192, 192));

    private BaseIcons() {
    }

    //endregion
}
