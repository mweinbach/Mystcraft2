package net.mystcraft.minecraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.BookModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Vanilla articulated book mesh, as used by the original classic renderers. */
public final class ClassicBookVisuals {
    public static final ResourceLocation LINK_BOOK = new ResourceLocation("mystcraft", "textures/entity/linkbook.png");
    private static final ResourceLocation AGE_BOOK = new ResourceLocation("mystcraft", "textures/entity/agebook.png");
    private final BookModel model = new BookModel(BookModel.createBodyLayer().bakeRoot());
    public static ResourceLocation texture(ItemStack stack) {
        String id = Registry.ITEM.getKey(stack.getItem()).getPath();
        return id.equals("descriptive_book") || id.equals("agebook") ? AGE_BOOK : LINK_BOOK;
    }
    public void render(ItemStack stack, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay, float openness) {
        // Legacy RenderLinkbook/RenderBookstand pass time/page flips of zero: the
        // articulated covers and pages stay open rather than using enchanting-table animation.
        model.setupAnim(0, 0, 0, openness);
        model.render(pose, buffers.getBuffer(RenderType.entitySolid(texture(stack))),
                light, overlay, 1, 1, 1, 1);
    }
}
