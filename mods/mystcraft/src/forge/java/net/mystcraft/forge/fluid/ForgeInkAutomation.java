package net.mystcraft.forge.fluid;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.mystcraft.integration.registry.InkRegistry;
import net.mystcraft.minecraft.fluid.InkContainerAccess;
import net.mystcraft.minecraft.fluid.InkInteractions;
import net.mystcraft.minecraft.fluid.InkTank;
/** Forge capabilities are adapters around the shared container/tank accounting. */
public final class ForgeInkAutomation {
    public static void initialize(){
        net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus().addListener((net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event)->event.enqueueWork(net.mystcraft.minecraft.fluid.InkDispensers::register));
        InkContainerAccess.install(ForgeInkAutomation::drain);
        net.mystcraft.minecraft.fluid.InkAutomationChecks.install(ForgeInkAutomation::checkNative);
        MinecraftForge.EVENT_BUS.addGenericListener(ItemStack.class,ForgeInkAutomation::attachItem);
        MinecraftForge.EVENT_BUS.addGenericListener(BlockEntity.class,ForgeInkAutomation::attachBlock);
        MinecraftForge.EVENT_BUS.addListener(ForgeInkAutomation::useBottle);
    }
    private static void attachItem(AttachCapabilitiesEvent<ItemStack> event){
        if(!net.minecraft.core.Registry.ITEM.getKey(event.getObject().getItem()).equals(new ResourceLocation("mystcraft:ink_vial")))return;
        var handler=new ForgeInkVialHandler(event.getObject());event.addCapability(new ResourceLocation("mystcraft:ink_vial"),handler);event.addListener(handler::invalidate);
    }
    private static void attachBlock(AttachCapabilitiesEvent<BlockEntity> event){
        if(!(event.getObject() instanceof InkTank tank))return;
        IFluidHandler handler=new IFluidHandler(){
            public int getTanks(){return 1;}
            public FluidStack getFluidInTank(int index){return index==0&&tank.inkAmount()>0?new FluidStack(InkRegistry.BLACK_INK.get(),tank.inkAmount()):FluidStack.EMPTY;}
            public int getTankCapacity(int index){return index==0?tank.inkCapacity():0;}
            public boolean isFluidValid(int index,FluidStack fluid){return index==0&&fluid.getFluid()==InkRegistry.BLACK_INK.get();}
            public int fill(FluidStack fluid,FluidAction action){return fluid.getFluid()==InkRegistry.BLACK_INK.get()&&!fluid.hasTag()?tank.fillInk(fluid.getAmount(),action.execute()):0;}
            public FluidStack drain(FluidStack fluid,FluidAction action){return fluid.getFluid()==InkRegistry.BLACK_INK.get()?drain(fluid.getAmount(),action):FluidStack.EMPTY;}
            public FluidStack drain(int maximum,FluidAction action){int amount=tank.drainInk(maximum,action.execute());return amount>0?new FluidStack(InkRegistry.BLACK_INK.get(),amount):FluidStack.EMPTY;}
        };
        LazyOptional<IFluidHandler> capability=LazyOptional.of(()->handler);
        event.addCapability(new ResourceLocation("mystcraft:inkwell"),new ICapabilityProvider(){
            public <T> LazyOptional<T> getCapability(net.minecraftforge.common.capabilities.Capability<T> key,net.minecraft.core.Direction face){return key==ForgeCapabilities.FLUID_HANDLER?capability.cast():LazyOptional.empty();}
        });event.addListener(capability::invalidate);
    }
    private static Optional<InkContainerAccess.Drain> drain(ItemStack one,int maximum,boolean wholeBucketOnly){
        var capability=FluidUtil.getFluidHandler(one);if(!capability.isPresent())return Optional.empty();var handler=capability.orElseThrow(IllegalStateException::new);
        if(wholeBucketOnly){int stored=0;for(int i=0;i<handler.getTanks();i++){var fluid=handler.getFluidInTank(i);if(!fluid.isEmpty()&&fluid.getFluid()!=InkRegistry.BLACK_INK.get())return Optional.empty();stored+=fluid.getAmount();}if(stored!=1000||maximum<1000)return Optional.empty();}
        FluidStack drained=handler.drain(new FluidStack(InkRegistry.BLACK_INK.get(),maximum),IFluidHandler.FluidAction.EXECUTE);
        return drained.isEmpty()||drained.hasTag()||wholeBucketOnly&&drained.getAmount()!=1000?Optional.empty():Optional.of(new InkContainerAccess.Drain(drained.getAmount(),handler.getContainer()));
    }
    private static void checkNative(net.minecraft.server.level.ServerLevel level,net.minecraft.core.BlockPos pos){
        BlockEntity block=level.getBlockEntity(pos);var tank=(InkTank)block;
        var handler=block.getCapability(ForgeCapabilities.FLUID_HANDLER).orElseThrow(()->new AssertionError("inkwell Forge fluid capability unavailable"));
        var ink=new FluidStack(InkRegistry.BLACK_INK.get(),137);
        if(handler.fill(ink,IFluidHandler.FluidAction.SIMULATE)!=137||tank.inkAmount()!=0)throw new AssertionError("Forge inkwell fill simulation changed state");
        if(handler.fill(ink,IFluidHandler.FluidAction.EXECUTE)!=137||tank.inkAmount()!=137)throw new AssertionError("Forge inkwell fill conservation");
        if(handler.drain(50,IFluidHandler.FluidAction.SIMULATE).getAmount()!=50||tank.inkAmount()!=137)throw new AssertionError("Forge inkwell drain simulation changed state");
        if(handler.drain(50,IFluidHandler.FluidAction.EXECUTE).getAmount()!=50||tank.inkAmount()!=87)throw new AssertionError("Forge inkwell partial drain conservation");
    }
    private static void useBottle(PlayerInteractEvent.RightClickItem event){if(InkInteractions.denyWaterBottle(event.getLevel(),event.getEntity(),event.getItemStack())){event.setCancellationResult(InteractionResult.FAIL);event.setCanceled(true);}}
    private ForgeInkAutomation(){}
}
