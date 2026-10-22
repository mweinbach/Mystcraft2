package ports.jurassicraft.forge;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColor;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.settings.IKeyConflictContext;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import ports.jurassicraft.client.JurassicClient;
import ports.jurassicraft.forge.client.VehicleKeyScopes;
import ports.jurassicraft.platform.client.ClientPlatform;

/** Drives the shared client phases from Forge's client events. */
final class ForgeClient implements ClientPlatform.Backend {
    private final Map<BooleanSupplier, IKeyConflictContext> contexts = new IdentityHashMap<>();
    private RegisterKeyMappingsEvent keyEvent;

    private ForgeClient() {}

    static void init(IEventBus modBus, IEventBus forgeBus) {
        ForgeClient client = new ForgeClient();
        ClientPlatform.install(client);
        modBus.addListener((RegisterKeyMappingsEvent event) -> {
            client.keyEvent = event;
            try {
                JurassicClient.keys();
            } finally {
                client.keyEvent = null;
            }
        });
        modBus.addListener((EntityRenderersEvent.RegisterRenderers event) -> JurassicClient.renderers(new ClientPlatform.Renderers() {
            @Override public <E extends Entity> void entity(EntityType<? extends E> type, EntityRendererProvider<E> provider) {
                event.registerEntityRenderer(type, provider);
            }
            @Override public <T extends BlockEntity> void blockEntity(BlockEntityType<? extends T> type, BlockEntityRendererProvider<T> provider) {
                event.registerBlockEntityRenderer(type, provider);
            }
        }));
        modBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(JurassicClient::setup));
        modBus.addListener((RegisterColorHandlersEvent.Block event) -> JurassicClient.colors(new ClientPlatform.Colors() {
            @Override public void block(BlockColor color, Block... blocks) { event.register(color, blocks); }
            @Override public void item(ItemColor color, ItemLike... items) {}
        }));
        modBus.addListener((RegisterColorHandlersEvent.Item event) -> JurassicClient.colors(new ClientPlatform.Colors() {
            @Override public void block(BlockColor color, Block... blocks) {}
            @Override public void item(ItemColor color, ItemLike... items) { event.register(color, items); }
        }));
        modBus.addListener((RegisterGuiOverlaysEvent event) ->
                event.registerAboveAll("helicopter_hud", (gui, pose, partialTick, width, height) -> JurassicClient.hud(pose, partialTick)));
        forgeBus.addListener((ViewportEvent.ComputeCameraAngles event) ->
                JurassicClient.camera(event.getCamera(), (float) event.getPartialTick()));
        forgeBus.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.START) JurassicClient.beforeTick(Minecraft.getInstance());
            else if (event.phase == TickEvent.Phase.END) JurassicClient.tick(Minecraft.getInstance());
        });
    }

    @Override @SuppressWarnings("removal")
    public void renderLayer(Block block, RenderType type) { ItemBlockRenderTypes.setRenderLayer(block, type); }

    @Override public KeyMapping keyMapping(ClientPlatform.Key key) {
        if (keyEvent == null) throw new IllegalStateException("Key mappings must be created while key mappings register: " + key.name());
        IKeyConflictContext context = key.activeWhen() == null ? KeyConflictContext.IN_GAME : context(key.activeWhen());
        InputConstants.Key code = InputConstants.Type.KEYSYM.getOrCreate(key.key());
        // Left Ctrl is a modifier. Forge treats an unmodified binding as inactive while Ctrl is
        // held when the context does not overlap in-game play, which would swallow descend.
        boolean unmodifiedModifier = !key.control() && key.activeWhen() != null && KeyModifier.isKeyCodeModifier(code);
        KeyMapping mapping = key.control()
                ? new KeyMapping(key.name(), context, KeyModifier.CONTROL, code, key.category())
                : unmodifiedModifier
                ? new HeldModifierKey(key.name(), context, code, key.category())
                : new KeyMapping(key.name(), context, code, key.category());
        keyEvent.register(mapping);
        if (key.activeWhen() != null) VehicleKeyScopes.mark(mapping);
        return mapping;
    }

    /**
     * Active only while riding. A context overlaps only itself, not in-game play and not the other
     * vehicle context, so a seat chord is not a warning against the hotbar or against Left Ctrl.
     * {@code same} still compares the physical key when contexts do not overlap. The controls-screen
     * mixin hides that warning against an ordinary binding, so a held flight key still matches jump
     * or sprint. Helicopter bindings share this context, so Left Ctrl stays a gold modifier conflict
     * with Ctrl+A and Ctrl+D.
     */
    private IKeyConflictContext context(BooleanSupplier active) {
        return contexts.computeIfAbsent(active, supplier -> new IKeyConflictContext() {
            @Override public boolean isActive() { return supplier.getAsBoolean(); }
            @Override public boolean conflicts(IKeyConflictContext other) { return other == this; }
        });
    }

    /**
     * A vehicle binding whose key is itself Ctrl, Shift or Alt. Forge's unmodified-binding check
     * returns inactive while that modifier is held, because this context does not overlap in-game
     * play. The key is still held.
     */
    private static final class HeldModifierKey extends KeyMapping {
        private HeldModifierKey(String name, IKeyConflictContext context, InputConstants.Key key, String category) {
            super(name, context, key, category);
        }

        @Override public boolean isConflictContextAndModifierActive() {
            if (getKeyConflictContext().isActive() && getKeyModifier() == KeyModifier.NONE
                    && KeyModifier.isKeyCodeModifier(getKey())) return true;
            return super.isConflictContextAndModifierActive();
        }
    }
}
