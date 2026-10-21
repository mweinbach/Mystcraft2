/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.compat.patchouli;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import ports.extremereactors.corelib.client.gui.IControl;
import ports.extremereactors.corelib.client.gui.ModContainerScreen;
import ports.extremereactors.corelib.client.gui.control.Button;
import ports.extremereactors.corelib.client.model.data.ModelData;
import ports.extremereactors.corelib.compat.patchouli.component.CraftingPageComponent;
import ports.extremereactors.corelib.compat.patchouli.component.MultiblockPageComponent;
import ports.extremereactors.corelib.compat.patchouli.component.SmeltingPageComponent;
import ports.extremereactors.corelib.compat.patchouli.component.SpotlightPageComponent;
import ports.extremereactors.corelib.item.inventory.container.ModContainer;
import vazkii.patchouli.api.IMultiblock;
import vazkii.patchouli.api.PatchouliAPI;
import vazkii.patchouli.client.book.template.BookTemplate;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Patchouli support for a mod's handbook. Only call into this class when Patchouli is loaded.
 * <p>
 * {@link #initialize} adds template components that put Patchouli's crafting, smelting, spotlight and multiblock
 * pages inside a template, named {@code <namespace>:crafting_page}, {@code smelting_page}, {@code spotlight_page}
 * and {@code multiblock_page}, and lets the machine screens open handbook entries from their help button.
 */
public final class Patchouli {

    public static void initialize(final String componentsNamespace) {

        BookTemplate.registerComponent(new ResourceLocation(componentsNamespace, "crafting_page"), CraftingPageComponent.class);
        BookTemplate.registerComponent(new ResourceLocation(componentsNamespace, "smelting_page"), SmeltingPageComponent.class);
        BookTemplate.registerComponent(new ResourceLocation(componentsNamespace, "spotlight_page"), SpotlightPageComponent.class);
        BookTemplate.registerComponent(new ResourceLocation(componentsNamespace, "multiblock_page"), MultiblockPageComponent.class);

        ModContainerScreen.setHelpButtonFactory(Patchouli::createHelpButton);
    }

    /**
     * Register a multiblock for the handbook's multiblock pages.
     *
     * @param previewModelData the model data to draw each block state of the preview with; may return null to use
     *                         the data of the preview's own block entity
     */
    public static void registerMultiblock(final ResourceLocation id, final IMultiblock multiblock,
                                          final Function<BlockState, ModelData> previewModelData) {

        PatchouliAPI.get().registerMultiblock(id, multiblock);
        s_previewModelData.put(id, previewModelData);
    }

    /**
     * @return the preview model data registered with the multiblock, or a mapper that keeps every block's own data
     */
    public static Function<BlockState, ModelData> getPreviewModelData(@Nullable final ResourceLocation multiblockId) {
        return null != multiblockId ? s_previewModelData.getOrDefault(multiblockId, NO_PREVIEW_DATA) : NO_PREVIEW_DATA;
    }

    public static void openBookEntry(final ResourceLocation bookId, final ResourceLocation entryId,
                                     final int pageNumber) {
        PatchouliAPI.get().openBookEntry(bookId, entryId, pageNumber);
    }

    //region internals

    private Patchouli() {
    }

    private static IControl createHelpButton(final ModContainerScreen<? extends ModContainer> screen,
                                             final ResourceLocation bookId, final ResourceLocation entryId,
                                             final int pageNumber) {

        final Button button = new Button(screen, "help", "?");

        button.setTooltips(Component.translatable("gui.bigreactors.generic.help.tooltip"));
        button.Clicked.subscribe((b, mouseButton) -> openBookEntry(bookId, entryId, pageNumber));
        return button;
    }

    private static final Function<BlockState, ModelData> NO_PREVIEW_DATA = state -> null;
    private static final Map<ResourceLocation, Function<BlockState, ModelData>> s_previewModelData = new ConcurrentHashMap<>();

    //endregion
}
