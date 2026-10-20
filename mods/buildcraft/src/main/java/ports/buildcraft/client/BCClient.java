package ports.buildcraft.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import ports.buildcraft.builders.BCBuilders;
import ports.buildcraft.energy.BCEnergy;
import ports.buildcraft.energy.BCFluidDefinition;
import ports.buildcraft.factory.BCFactory;
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
        sink.register(BCBuilders.QUARRY_TYPE.get(), context -> new QuarryRenderer());
        sink.register(BCTransport.PIPE_TYPE.get(), context -> new PipeRenderer());
    }

    /** Called on the client thread once registries are populated. */
    public static void registerScreens() {
        MenuScreens.register(BCEnergy.STIRLING_ENGINE_MENU.get(), StirlingEngineScreen::new);
        MenuScreens.register(BCEnergy.COMBUSTION_ENGINE_MENU.get(), CombustionEngineScreen::new);
        MenuScreens.register(BCTransport.DIAMOND_PIPE_MENU.get(), DiamondPipeScreen::new);
    }

    /** Blocks whose models have transparent texels; Forge reads the same layer from the models' {@code render_type}. */
    public static List<Block> cutoutBlocks() {
        List<Block> blocks = new ArrayList<>(List.of(BCFactory.TANK.get(), BCFactory.TUBE.get(), BCBuilders.FRAME.get()));
        for (PipeDefinition pipe : BCTransport.PIPES) blocks.add(pipe.block());
        return blocks;
    }

    /** Sprites drawn only by renderers, which no block model would otherwise put on the block atlas. */
    public static List<ResourceLocation> extraSprites() {
        List<ResourceLocation> sprites = new ArrayList<>(List.of(PipeRenderer.POWER_FLOW, QuarryRenderer.DRILL, QuarryRenderer.FRAME,
                MinerRenderer.PUMP_TUBE, MinerRenderer.MINING_WELL_TUBE));
        sprites.addAll(EngineRenderer.sprites());
        for (BCFluidDefinition fluid : BCEnergy.FLUIDS) {
            sprites.add(fluid.stillTexture());
            sprites.add(fluid.flowingTexture());
        }
        return sprites;
    }
}
