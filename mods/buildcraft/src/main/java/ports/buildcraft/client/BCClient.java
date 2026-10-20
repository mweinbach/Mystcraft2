package ports.buildcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.item.ClampedItemPropertyFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.builders.BCBuilders;
import ports.buildcraft.builders.ElectronicLibraryBlockEntity;
import ports.buildcraft.builders.SnapshotTooltip;
import ports.buildcraft.builders.snapshot.Snapshot;
import ports.buildcraft.builders.snapshot.SnapshotPackets;
import ports.buildcraft.core.BCCore;
import ports.buildcraft.core.FragileFluidShardItem;
import ports.buildcraft.core.marker.volume.ClientVolumeBoxes;
import ports.buildcraft.core.marker.volume.VolumeBoxes;
import ports.buildcraft.energy.BCEnergy;
import ports.buildcraft.energy.BCFluidDefinition;
import ports.buildcraft.factory.BCFactory;
import ports.buildcraft.lib.FluidAmount;
import ports.buildcraft.platform.Platform;
import ports.buildcraft.transport.BCTransport;
import ports.buildcraft.transport.PipeDefinition;

/** Shared client registration through vanilla APIs; loaders add render layers and atlas sprites. */
public final class BCClient {
    private BCClient() {}

    public interface RendererSink {
        <T extends BlockEntity> void register(BlockEntityType<? extends T> type, BlockEntityRendererProvider<T> provider);
    }

    public static void registerRenderers(RendererSink sink) {
        sink.register(BCEnergy.REDSTONE_ENGINE_TYPE.get(), context -> new EngineRenderer<>("wood"));
        sink.register(BCEnergy.STIRLING_ENGINE_TYPE.get(), context -> new EngineRenderer<>("stone"));
        sink.register(BCEnergy.COMBUSTION_ENGINE_TYPE.get(), context -> new EngineRenderer<>("iron"));
        sink.register(BCFactory.TANK_TYPE.get(), context -> new TankRenderer());
        sink.register(BCFactory.PUMP_TYPE.get(), context -> new MinerRenderer<>(MinerRenderer.PUMP_TUBE));
        sink.register(BCFactory.MINING_WELL_TYPE.get(), context -> new MinerRenderer<>(MinerRenderer.MINING_WELL_TUBE));
        sink.register(BCFactory.DISTILLER_TYPE.get(), context -> new DistillerRenderer());
        sink.register(BCFactory.HEAT_EXCHANGER_TYPE.get(), context -> new HeatExchangerRenderer());
        sink.register(BCBuilders.QUARRY_TYPE.get(), context -> new QuarryRenderer());
        sink.register(BCBuilders.FILLER_TYPE.get(), context -> new FillerRenderer());
        sink.register(BCBuilders.ARCHITECT_TABLE_TYPE.get(), context -> new ArchitectTableRenderer());
        sink.register(BCBuilders.BUILDER_TYPE.get(), context -> new BuilderRenderer());
        sink.register(BCTransport.PIPE_TYPE.get(), context -> new PipeRenderer());
        sink.register(BCCore.MARKER_VOLUME_TYPE.get(), context -> new MarkerRenderer.Volume());
        sink.register(BCCore.MARKER_PATH_TYPE.get(), context -> new MarkerRenderer.Path());
    }

    public interface ItemColourSink {
        void register(ItemColor colour, ItemLike item);
    }

    public interface ItemPropertySink {
        void register(Item item, ResourceLocation name, ClampedItemPropertyFunction property);
    }

    /** The shard's fluid layer is tinted with the average colour of the fluid it holds. */
    public static void registerItemColours(ItemColourSink sink) {
        sink.register((stack, layer) -> layer == 1 ? FluidColours.average(FragileFluidShardItem.getFluid(stack).fluid()) : -1,
                BCCore.FRAGILE_FLUID_SHARD.get());
    }

    /** Upstream's shard model sets {@code flipGas}: shards of gaseous fluids are drawn upside down. */
    public static void registerItemProperties(ItemPropertySink sink) {
        sink.register(BCCore.FRAGILE_FLUID_SHARD.get(), BuildCraft.id("gaseous"), (stack, level, entity, seed) -> {
            FluidAmount fluid = FragileFluidShardItem.getFluid(stack);
            return !fluid.isEmpty() && Platform.get().isGaseous(fluid.fluid()) ? 1 : 0;
        });
    }

    /** Called on the client thread once registries are populated. */
    public static void registerScreens() {
        MenuScreens.register(BCEnergy.STIRLING_ENGINE_MENU.get(), StirlingEngineScreen::new);
        MenuScreens.register(BCEnergy.COMBUSTION_ENGINE_MENU.get(), CombustionEngineScreen::new);
        MenuScreens.register(BCFactory.DISTILLER_MENU.get(), DistillerScreen::new);
        MenuScreens.register(BCTransport.DIAMOND_PIPE_MENU.get(), DiamondPipeScreen::new);
        MenuScreens.register(BCBuilders.FILLER_MENU.get(), FillerScreen::new);
        MenuScreens.register(BCBuilders.ARCHITECT_TABLE_MENU.get(), ArchitectTableScreen::new);
        MenuScreens.register(BCBuilders.BUILDER_MENU.get(), BuilderScreen::new);
        MenuScreens.register(BCBuilders.ELECTRONIC_LIBRARY_MENU.get(), ElectronicLibraryScreen::new);
        MenuScreens.register(BCBuilders.REPLACER_MENU.get(), ReplacerScreen::new);
    }

    /** Server-to-client messages by channel; loaders hand each payload over on the client thread. */
    public static Map<ResourceLocation, Consumer<FriendlyByteBuf>> receivers() {
        return Map.of(VolumeBoxes.CHANNEL, ClientVolumeBoxes::receive,
                SnapshotPackets.RESPONSE_CHANNEL, ClientSnapshots::receive,
                ElectronicLibraryBlockEntity.DOWN_CHANNEL, ElectronicLibraryBlockEntity::receiveDown,
                ElectronicLibraryBlockEntity.UP_REQUEST_CHANNEL, BCClient::receiveUploadRequest);
    }

    /** The server asks for the snapshot selected in a library; only a client holding it answers. */
    private static void receiveUploadRequest(FriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !(minecraft.level.getBlockEntity(pos) instanceof ElectronicLibraryBlockEntity library)) return;
        Snapshot.Key selected = library.getSelected();
        if (selected == null) return;
        ElectronicLibraryBlockEntity.upload(pos, selected, ElectronicLibraryBlockEntity.UPLOAD_PART_BYTES,
                payload -> ClientPlatform.get().sendToServer(ElectronicLibraryBlockEntity.UP_CHANNEL, payload));
    }

    /** The client half of the snapshot drawn in template, blueprint and single schematic tooltips. */
    public static ClientTooltipComponent tooltipComponent(SnapshotTooltip tooltip) {
        return new SnapshotTooltipComponent(tooltip);
    }

    /** Called after translucent blocks with the level's pose stack, which carries the camera's rotation but not its position. */
    public static void renderLevel(PoseStack poseStack, Camera camera) {
        VolumeBoxRenderer.render(poseStack, camera);
        ArchitectTablesRenderer.render(poseStack, camera);
    }

    /** Blocks whose models have transparent texels; Forge reads the same layer from the models' {@code render_type}. */
    public static List<Block> cutoutBlocks() {
        List<Block> blocks = new ArrayList<>(List.of(BCFactory.TANK.get(), BCFactory.TUBE.get(), BCFactory.DISTILLER.get(),
                BCFactory.HEAT_EXCHANGER.get(), BCBuilders.FRAME.get(), BCCore.MARKER_VOLUME.get(), BCCore.MARKER_PATH.get()));
        for (PipeDefinition pipe : BCTransport.PIPES) blocks.add(pipe.block());
        return blocks;
    }

    /** Sprites drawn only by renderers, which no block model would otherwise put on the block atlas. */
    public static List<ResourceLocation> extraSprites() {
        List<ResourceLocation> sprites = new ArrayList<>(List.of(PipeRenderer.POWER_FLOW, QuarryRenderer.DRILL, QuarryRenderer.FRAME,
                MinerRenderer.PUMP_TUBE, MinerRenderer.MINING_WELL_TUBE, SnapshotBuilderRenderer.ROBOT));
        sprites.addAll(EngineRenderer.sprites());
        sprites.addAll(DistillerRenderer.sprites());
        sprites.addAll(LaserRenderer.sprites());
        for (BCFluidDefinition fluid : BCEnergy.FLUIDS) {
            sprites.add(fluid.stillTexture());
            sprites.add(fluid.flowingTexture());
        }
        return sprites;
    }
}
