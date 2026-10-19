package net.mystcraft.forge.exploration;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;
import net.mystcraft.minecraft.exploration.MystLecternBlockEntity;

@Mod.EventBusSubscriber(modid = "mystcraft")
public final class ForgeLecternInventory {
    private ForgeLecternInventory() {}
    @SubscribeEvent public static void attach(AttachCapabilitiesEvent<BlockEntity> event) {
        if (!(event.getObject() instanceof MystLecternBlockEntity lectern)) return;
        LazyOptional<IItemHandler> handler = LazyOptional.of(() -> new InvWrapper(lectern));
        event.addCapability(new net.minecraft.resources.ResourceLocation("mystcraft", "lectern_inventory"), new ICapabilityProvider() {
            @Override public <T> LazyOptional<T> getCapability(net.minecraftforge.common.capabilities.Capability<T> capability, net.minecraft.core.Direction side) {
                return capability == ForgeCapabilities.ITEM_HANDLER ? handler.cast() : LazyOptional.empty();
            }
        });
        event.addListener(handler::invalidate);
    }
}
