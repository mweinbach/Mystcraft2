package ports.jurassicraft.fabric;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.color.block.BlockColor;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import ports.jurassicraft.client.JurassicClient;
import ports.jurassicraft.fabric.client.MachineModels;
import ports.jurassicraft.platform.Network;
import ports.jurassicraft.platform.client.ClientPlatform;

/** Drives the shared client phases from Fabric's client initializer and events. */
public final class FabricClientMod implements ClientModInitializer, ClientPlatform.Backend {
    @Override public void onInitializeClient() {
        ClientPlatform.install(this);
        FabricConfigBackend.loadClient();
        FabricPlatform.serverbound = ClientPlayNetworking::createC2SPacket;

        JurassicClient.keys();
        JurassicClient.renderers(new ClientPlatform.Renderers() {
            @Override public <E extends Entity> void entity(EntityType<? extends E> type, EntityRendererProvider<E> provider) {
                EntityRendererRegistry.register(type, provider);
            }
            @Override public <T extends BlockEntity> void blockEntity(BlockEntityType<? extends T> type, BlockEntityRendererProvider<T> provider) {
                BlockEntityRendererRegistry.register(type, provider);
            }
        });
        JurassicClient.setup();
        JurassicClient.colors(new ClientPlatform.Colors() {
            @Override public void block(BlockColor color, Block... blocks) { ColorProviderRegistry.BLOCK.register(color, blocks); }
            @Override public void item(ItemColor color, ItemLike... items) { ColorProviderRegistry.ITEM.register(color, items); }
        });
        MachineModels.init();

        ClientTickEvents.END_CLIENT_TICK.register(JurassicClient::tick);
        HudRenderCallback.EVENT.register(JurassicClient::hud);
        for (ResourceLocation id : Network.clientIds()) {
            ClientPlayNetworking.registerGlobalReceiver(id, (client, handler, buffer, sender) -> {
                FriendlyByteBuf copy = new FriendlyByteBuf(buffer.copy());
                client.execute(() -> Network.receiveOnClient(id, copy));
            });
        }
    }

    @Override public void renderLayer(Block block, RenderType type) { BlockRenderLayerMap.INSTANCE.putBlock(block, type); }

    /** Vanilla gives each physical key to one mapping, so control chords bind their fallback key. */
    @Override public KeyMapping keyMapping(ClientPlatform.Key key) {
        return KeyBindingHelper.registerKeyBinding(new KeyMapping(key.name(), InputConstants.Type.KEYSYM,
                key.control() ? key.fallback() : key.key(), key.category()));
    }
}
