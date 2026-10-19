package net.mystcraft.minecraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.mystcraft.minecraft.presentation.BookPresentationOptions;
import net.mystcraft.minecraft.linking.LinkDestination;

/** Original optional billboard title at a strict 25-block camera distance. */
final class ClassicBookLabels {
    private ClassicBookLabels() {}
    static void render(ItemStack book, Vec3 origin, float x, float y, float z, PoseStack pose, MultiBufferSource buffers, int light) {
        if (book.isEmpty() || !BookPresentationOptions.labelsEnabled()) return;
        Minecraft client = Minecraft.getInstance();
        var camera = client.gameRenderer.getMainCamera();
        if (camera.getPosition().distanceToSqr(origin) > 25 * 25) return;
        Component title = book.hasCustomHoverName() ? book.getHoverName() : LinkDestination.read(book)
                .map(destination -> Component.literal(destination.dimensionId().toString())).orElse(Component.empty());
        if (title.getString().isEmpty()) return;
        pose.pushPose(); pose.translate(x, y, z);
        pose.mulPose(camera.rotation()); pose.scale(-0.02666667F, -0.02666667F, 0.02666667F);
        float left = -client.font.width(title) / 2F;
        client.font.drawInBatch(title, left, 0, 0xFFFFFFFF, false, pose.last().pose(), buffers, true, 0x40000000, light);
        client.font.drawInBatch(title, left, 0, 0xFFFFFFFF, false, pose.last().pose(), buffers, false, 0, light);
        pose.popPose();
    }
}
