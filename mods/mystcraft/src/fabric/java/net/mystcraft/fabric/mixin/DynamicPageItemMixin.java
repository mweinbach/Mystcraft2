package net.mystcraft.fabric.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemStack;
import net.mystcraft.minecraft.client.DynamicPageRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Dynamic registry pages use original composed glyphs in every vanilla item render context. */
@Mixin(ItemRenderer.class)
public abstract class DynamicPageItemMixin {
    @Inject(method="render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/client/renderer/block/model/ItemTransforms$TransformType;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V",at=@At("HEAD"),cancellable=true)
    private void mystcraft$dynamicPage(ItemStack stack,ItemTransforms.TransformType type,boolean leftHand,PoseStack pose,
            MultiBufferSource buffers,int light,int overlay,BakedModel model,CallbackInfo callback) {
        if(DynamicPageRenderer.render(stack,type,leftHand,pose,buffers,light,overlay,model))callback.cancel();
    }
}
