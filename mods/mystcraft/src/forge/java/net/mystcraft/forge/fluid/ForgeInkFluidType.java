package net.mystcraft.forge.fluid;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fluids.FluidType;
/** Native water-like movement/air rules and original tint/art; source ink is not drinkable water. */
public final class ForgeInkFluidType extends FluidType {
    public ForgeInkFluidType(){super(Properties.create().descriptionId("fluid.mystcraft.black_ink").density(1000).viscosity(1000).canSwim(true).canDrown(true).supportsBoating(true).canConvertToSource(false));}
    @Override public boolean isVaporizedOnPlacement(net.minecraft.world.level.Level level,net.minecraft.core.BlockPos pos,net.minecraftforge.fluids.FluidStack stack){return false;}
    @Override public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions> consumer){
        consumer.accept(new net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions(){
            @Override public ResourceLocation getStillTexture(){return new ResourceLocation("mystcraft:block/fluid");}
            @Override public ResourceLocation getFlowingTexture(){return new ResourceLocation("mystcraft:block/fluid_flow");}
            @Override public int getTintColor(){return 0xFF191919;}
            @Override public com.mojang.math.Vector3f modifyFogColor(net.minecraft.client.Camera camera,float partialTick,net.minecraft.client.multiplayer.ClientLevel level,int renderDistance,float darken,com.mojang.math.Vector3f original){return new com.mojang.math.Vector3f(25F/255F,25F/255F,25F/255F);}
        });
    }
}
