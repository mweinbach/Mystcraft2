package net.mystcraft.fabric.fluid;

import java.util.Optional;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.base.FullItemFluidStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleVariantStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.mystcraft.integration.registry.InkRegistry;
import net.mystcraft.integration.registry.InkMixerRegistry;
import net.mystcraft.integration.registry.WritingDeskRegistry;
import net.mystcraft.minecraft.fluid.InkContainerAccess;
import net.mystcraft.minecraft.fluid.InkInteractions;
import net.mystcraft.minecraft.fluid.InkTank;
/** Fabric droplets convert exactly: one millibucket is 81 native units. Transactions roll back shared state. */
public final class FabricInkAutomation {
    private static final long UNIT=FluidConstants.BUCKET/1000;
    public static void initialize(){
        net.mystcraft.minecraft.fluid.InkDispensers.register();
        InkContainerAccess.install(FabricInkAutomation::drain);
        net.mystcraft.minecraft.fluid.InkAutomationChecks.install(FabricInkAutomation::checkNative);
        FluidStorage.ITEM.registerForItems((stack,context)->new FullItemFluidStorage(context,ignored->ItemVariant.of(Items.GLASS_BOTTLE),FluidVariant.of(InkRegistry.BLACK_INK.get()),FluidConstants.BUCKET),InkMixerRegistry.INK_VIAL.get());
        FluidStorage.SIDED.registerForBlockEntity((desk,side)->new TankStorage(desk),WritingDeskRegistry.WRITING_DESK_ENTITY.get());
        UseItemCallback.EVENT.register((player,level,hand)->InkInteractions.denyWaterBottle(level,player,player.getItemInHand(hand))?InteractionResultHolder.fail(player.getItemInHand(hand)):InteractionResultHolder.pass(player.getItemInHand(hand)));
    }
    private static Optional<InkContainerAccess.Drain> drain(ItemStack one,int maximum,boolean wholeBucketOnly){
        var slot=new SingleVariantStorage<ItemVariant>(){
            @Override protected ItemVariant getBlankVariant(){return ItemVariant.blank();}
            @Override protected long getCapacity(ItemVariant item){return 1;}
        };slot.variant=ItemVariant.of(one);slot.amount=1;
        var context=ContainerItemContext.ofSingleSlot(slot);var storage=FluidStorage.ITEM.find(one,context);if(storage==null)return Optional.empty();
        var ink=FluidVariant.of(InkRegistry.BLACK_INK.get());
        if(wholeBucketOnly){long stored=0;for(var view:storage){if(!view.isResourceBlank()&&!view.getResource().equals(ink))return Optional.empty();stored+=view.getAmount();}if(stored!=FluidConstants.BUCKET||maximum<1000)return Optional.empty();}
        try(var transaction=Transaction.openOuter()){
            long removed=storage.extract(ink,(long)maximum*UNIT,transaction);
            if(removed<=0||removed%UNIT!=0||wholeBucketOnly&&removed!=FluidConstants.BUCKET)return Optional.empty();
            var result=new InkContainerAccess.Drain((int)(removed/UNIT),slot.variant.toStack((int)slot.amount));transaction.commit();return Optional.of(result);
        }
    }
    private static void checkNative(net.minecraft.server.level.ServerLevel level,net.minecraft.core.BlockPos pos){
        var storage=FluidStorage.SIDED.find(level,pos,null);var tank=(InkTank)level.getBlockEntity(pos);var ink=FluidVariant.of(InkRegistry.BLACK_INK.get());
        if(storage==null)throw new AssertionError("inkwell Fabric fluid storage unavailable");
        try(var tx=Transaction.openOuter()){if(storage.insert(ink,137*UNIT,tx)!=137*UNIT||tank.inkAmount()!=137)throw new AssertionError("Fabric provisional fill amount");}
        if(tank.inkAmount()!=0)throw new AssertionError("Fabric aborted fill did not roll back");
        try(var tx=Transaction.openOuter()){if(storage.insert(ink,137*UNIT,tx)!=137*UNIT)throw new AssertionError("Fabric committed fill amount");tx.commit();}
        try(var tx=Transaction.openOuter()){if(storage.extract(ink,50*UNIT,tx)!=50*UNIT)throw new AssertionError("Fabric provisional drain amount");}
        if(tank.inkAmount()!=137)throw new AssertionError("Fabric aborted drain did not roll back");
        try(var tx=Transaction.openOuter()){if(storage.extract(ink,50*UNIT,tx)!=50*UNIT)throw new AssertionError("Fabric committed drain amount");tx.commit();}
        if(tank.inkAmount()!=87)throw new AssertionError("Fabric fractional ink conservation");
    }
    private static final class TankStorage extends SnapshotParticipant<Integer> implements SingleSlotStorage<FluidVariant>{
        final InkTank tank;TankStorage(InkTank tank){this.tank=tank;}
        public long insert(FluidVariant resource,long maximum,TransactionContext transaction){
            net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions.notBlankNotNegative(resource,maximum);
            if(!resource.equals(FluidVariant.of(InkRegistry.BLACK_INK.get())))return 0;
            int amount=tank.fillInk((int)Math.min(Integer.MAX_VALUE,maximum/UNIT),false);if(amount==0)return 0;
            updateSnapshots(transaction);tank.setInkAmount(tank.inkAmount()+amount,false);return (long)amount*UNIT;
        }
        public long extract(FluidVariant resource,long maximum,TransactionContext transaction){
            net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions.notBlankNotNegative(resource,maximum);
            if(!resource.equals(FluidVariant.of(InkRegistry.BLACK_INK.get())))return 0;
            int amount=tank.drainInk((int)Math.min(Integer.MAX_VALUE,maximum/UNIT),false);if(amount==0)return 0;
            updateSnapshots(transaction);tank.setInkAmount(tank.inkAmount()-amount,false);return (long)amount*UNIT;
        }
        @Override protected Integer createSnapshot(){return tank.inkAmount();}
        @Override protected void readSnapshot(Integer amount){tank.setInkAmount(amount,false);}
        @Override protected void onFinalCommit(){tank.setInkAmount(tank.inkAmount(),true);}
        public boolean isResourceBlank(){return tank.inkAmount()==0;}
        public FluidVariant getResource(){return isResourceBlank()?FluidVariant.blank():FluidVariant.of(InkRegistry.BLACK_INK.get());}
        public long getAmount(){return (long)tank.inkAmount()*UNIT;}
        public long getCapacity(){return (long)tank.inkCapacity()*UNIT;}
    }
    private FabricInkAutomation(){}
}
