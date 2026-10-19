package net.mystcraft.fabric.mixin;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.mystcraft.integration.registry.InkRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Classic ink's own dark fog, rather than the biome's water fog, only while eyes are submerged. */
@Mixin(FogRenderer.class)
public abstract class InkFogMixin {
    @Shadow private static float fogRed;
    @Shadow private static float fogGreen;
    @Shadow private static float fogBlue;
    @Inject(method="setupColor",at=@At("TAIL"))
    private static void mystcraft$inkFog(Camera camera,float partialTick,ClientLevel level,int distance,float darken,CallbackInfo ci){
        var pos=camera.getBlockPosition();var fluid=level.getFluidState(pos);
        if(!fluid.getType().isSame(InkRegistry.BLACK_INK.get())||camera.getPosition().y>=pos.getY()+fluid.getHeight(level,pos))return;
        fogRed=fogGreen=fogBlue=25F/255F;com.mojang.blaze3d.systems.RenderSystem.setShaderFogColor(fogRed,fogGreen,fogBlue);
    }
}
