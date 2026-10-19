package ports.jurassicraft.skull.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Vector3f;
import net.minecraft.core.Direction;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.skull.SkullBlock;
import ports.jurassicraft.skull.SkullBlockEntity;

public final class SkullRenderer implements BlockEntityRenderer<SkullBlockEntity> {
    private final SkullModel wall=new SkullModel(true,true),floor=new SkullModel(false,true),floorNoStand=new SkullModel(false,false);
    @Override public void render(SkullBlockEntity entity,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
        if (entity.skull().isEmpty()) return;
        boolean isWall=entity.getBlockState().getValue(SkullBlock.FACING)!=Direction.UP;
        SkullModel model=isWall ? wall : entity.stand() ? floor : floorNoStand;
        String texture="textures/skull_display/tyrannosaurus_"+(entity.fresh() ? "fresh" : "fossilized")+"_"+(isWall ? "horizontal" : "vertical")+".png";
        pose.pushPose(); pose.translate(.5,.5,.5); pose.mulPose(Vector3f.YP.rotationDegrees(entity.angle())); pose.scale(2,-2,2);
        if (isWall) pose.translate(0,.2,-.25);
        pose.translate(0,-1.26,0);
        model.render(pose,buffers.getBuffer(RenderType.entityCutoutNoCull(new ResourceLocation(PortMod.MOD_ID,texture))),light,overlay);
        pose.popPose();
    }
    @Override public boolean shouldRenderOffScreen(SkullBlockEntity entity) { return true; }
    @Override public int getViewDistance() { return 96; }
}
