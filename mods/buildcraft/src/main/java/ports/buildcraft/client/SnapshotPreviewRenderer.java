package ports.buildcraft.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.math.Matrix4f;
import com.mojang.math.Vector3f;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.lwjgl.opengl.GL11;
import ports.buildcraft.builders.snapshot.Blueprint;
import ports.buildcraft.builders.snapshot.SchematicBlock;
import ports.buildcraft.builders.snapshot.SchematicEntity;
import ports.buildcraft.builders.snapshot.Snapshot;
import ports.buildcraft.builders.snapshot.Template;
import ports.buildcraft.lib.Fluids2;

/**
 * Upstream {@code ClientSnapshots.renderSnapshot}: a snapshot drawn turning in a box of the screen, through its
 * own viewport and a 70° perspective. Upstream built each snapshot into a fake world and drew its chunk buffer;
 * here each block's model is baked once into vertex buffers, templates as quartz as upstream, fluids as boxes
 * of their height, and block entities and entities are drawn each frame from the same schematics.
 */
public final class SnapshotPreviewRenderer {
    private static final int MAX_SCENES = 16;
    /** Upstream drew block entities only for snapshots smaller than this. */
    private static final int MAX_BLOCK_ENTITY_SIZE = 32;
    private static final Map<RenderType, BufferBuilder> BUILDERS = new HashMap<>();
    private static final Map<Snapshot.Key, Scene> SCENES = new LinkedHashMap<>(16, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Snapshot.Key, Scene> eldest) {
            if (size() <= MAX_SCENES) return false;
            eldest.getValue().close();
            return true;
        }
    };
    @Nullable private static Level scenesLevel;

    private SnapshotPreviewRenderer() {}

    private record Scene(Map<RenderType, VertexBuffer> buffers, List<BlockEntity> blockEntities, List<Entity> entities) {
        void close() {
            buffers.values().forEach(VertexBuffer::close);
        }
    }

    /** Draws the snapshot in the box at {@code x, y} of the screen, in GUI pixels. */
    public static void render(Snapshot snapshot, int x, int y, int width, int height) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || width <= 0 || height <= 0) return;
        if (scenesLevel != minecraft.level) {
            SCENES.values().forEach(Scene::close);
            SCENES.clear();
            scenesLevel = minecraft.level;
        }
        Scene scene = SCENES.get(snapshot.key);
        if (scene == null) {
            scene = bake(minecraft, snapshot);
            SCENES.put(snapshot.key, scene);
        }

        Window window = minecraft.getWindow();
        double scale = window.getGuiScale();
        int viewportX = (int) (x * scale), viewportY = (int) (window.getHeight() - (y + height) * scale);
        int viewportWidth = (int) (width * scale), viewportHeight = (int) (height * scale);
        RenderSystem.enableScissor(viewportX, viewportY, viewportWidth, viewportHeight);
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.viewport(viewportX, viewportY, viewportWidth, viewportHeight);
        RenderSystem.backupProjectionMatrix();
        // Upstream scaled the projection by the GUI scale before the perspective, so the preview zooms with it.
        Matrix4f projection = Matrix4f.createScaleMatrix((float) scale, (float) scale, 1);
        projection.multiply(Matrix4f.perspective(70, (float) width / height, 0.1F, 1000));
        RenderSystem.setProjectionMatrix(projection);
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.enableDepthTest();
        Lighting.setupFor3DItems();

        PoseStack pose = new PoseStack();
        BlockPos size = snapshot.size;
        int snapshotSize = Math.max(Math.max(size.getX(), size.getY()), size.getZ());
        pose.translate(0, 0, -snapshotSize * 2F - 3);
        pose.mulPose(Vector3f.XP.rotationDegrees(20));
        pose.mulPose(Vector3f.YP.rotationDegrees((System.currentTimeMillis() % 3600) / 10F));
        pose.translate(-size.getX() / 2F, -size.getY() / 2F, -size.getZ() / 2F);
        pose.translate(0, snapshotSize * 0.1F, 0);

        for (Map.Entry<RenderType, VertexBuffer> entry : scene.buffers().entrySet()) {
            RenderType type = entry.getKey();
            type.setupRenderState();
            ShaderInstance shader = RenderSystem.getShader();
            VertexBuffer buffer = entry.getValue();
            buffer.bind();
            buffer.drawWithShader(pose.last().pose(), projection, shader);
            VertexBuffer.unbind();
            type.clearRenderState();
        }
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        BlockEntityRenderDispatcher blockEntityRenderer = minecraft.getBlockEntityRenderDispatcher();
        for (BlockEntity blockEntity : scene.blockEntities()) {
            BlockPos pos = blockEntity.getBlockPos();
            pose.pushPose();
            pose.translate(pos.getX(), pos.getY(), pos.getZ());
            blockEntityRenderer.renderItem(blockEntity, pose, buffers, RenderUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        EntityRenderDispatcher entityRenderer = minecraft.getEntityRenderDispatcher();
        entityRenderer.setRenderShadow(false);
        for (Entity entity : scene.entities()) {
            entityRenderer.render(entity, entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), 0, pose, buffers, RenderUtil.FULL_BRIGHT);
        }
        entityRenderer.setRenderShadow(true);
        buffers.endBatch();

        modelView.popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.restoreProjectionMatrix();
        RenderSystem.viewport(0, 0, window.getWidth(), window.getHeight());
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.disableScissor();
    }

    private static BlockState stateAt(Snapshot snapshot, int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= snapshot.size.getX() || y >= snapshot.size.getY() || z >= snapshot.size.getZ()) {
            return Blocks.AIR.defaultBlockState();
        }
        if (snapshot instanceof Template template) {
            return template.get(x, y, z) ? Blocks.QUARTZ_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState();
        }
        return snapshot instanceof Blueprint blueprint ? blueprint.get(x, y, z).getPreviewState() : Blocks.AIR.defaultBlockState();
    }

    /** Upstream's chunk buffer culled faces against their neighbours; blocks with every side covered are skipped. */
    private static boolean hidden(Snapshot snapshot, int x, int y, int z) {
        for (Direction side : Direction.values()) {
            BlockState neighbour = stateAt(snapshot, x + side.getStepX(), y + side.getStepY(), z + side.getStepZ());
            if (!neighbour.isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) return false;
        }
        return true;
    }

    private static Scene bake(Minecraft minecraft, Snapshot snapshot) {
        Map<RenderType, BufferBuilder> started = new LinkedHashMap<>();
        MultiBufferSource capture = type -> started.computeIfAbsent(type, key -> {
            BufferBuilder builder = BUILDERS.computeIfAbsent(key, unused -> new BufferBuilder(key.bufferSize()));
            builder.begin(key.mode(), key.format());
            return builder;
        });
        List<BlockEntity> blockEntities = new ArrayList<>();
        BlockPos size = snapshot.size;
        boolean withBlockEntities = Math.max(Math.max(size.getX(), size.getY()), size.getZ()) < MAX_BLOCK_ENTITY_SIZE;
        PoseStack pose = new PoseStack();
        for (int z = 0; z < size.getZ(); z++) {
            for (int y = 0; y < size.getY(); y++) {
                for (int x = 0; x < size.getX(); x++) {
                    BlockState state = stateAt(snapshot, x, y, z);
                    if (state.isAir() || hidden(snapshot, x, y, z)) continue;
                    pose.pushPose();
                    pose.translate(x, y, z);
                    if (state.getRenderShape() == RenderShape.MODEL) {
                        minecraft.getBlockRenderer().renderSingleBlock(state, pose, capture, RenderUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                    }
                    FluidState fluid = state.getFluidState();
                    if (!fluid.isEmpty()) {
                        TankRenderer.fluid(pose, capture, Fluids2.still(fluid.getType()), fluid.getOwnHeight(), 1, 0, 0, 0, 16, 16, 16,
                                RenderUtil.FULL_BRIGHT);
                    }
                    pose.popPose();
                    if (withBlockEntities && snapshot instanceof Blueprint blueprint) {
                        BlockEntity blockEntity = blockEntity(minecraft, blueprint.get(x, y, z), new BlockPos(x, y, z), state);
                        if (blockEntity != null) blockEntities.add(blockEntity);
                    }
                }
            }
        }
        Map<RenderType, VertexBuffer> buffers = new LinkedHashMap<>();
        started.forEach((type, builder) -> {
            BufferBuilder.RenderedBuffer rendered = builder.endOrDiscardIfEmpty();
            if (rendered == null) return;
            VertexBuffer buffer = new VertexBuffer();
            buffer.bind();
            buffer.upload(rendered);
            VertexBuffer.unbind();
            buffers.put(type, buffer);
        });
        List<Entity> entities = new ArrayList<>();
        if (snapshot instanceof Blueprint blueprint && minecraft.level != null) {
            for (SchematicEntity schematic : blueprint.entities) {
                Entity entity = schematic.createPreview(minecraft.level, BlockPos.ZERO);
                if (entity != null) entities.add(entity);
            }
        }
        return new Scene(buffers, blockEntities, entities);
    }

    @Nullable
    private static BlockEntity blockEntity(Minecraft minecraft, SchematicBlock schematic, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof EntityBlock entityBlock)) return null;
        CompoundTag tag = schematic.getPreviewTileNbt();
        BlockEntity blockEntity = tag == null ? entityBlock.newBlockEntity(pos, state) : BlockEntity.loadStatic(pos, state, tag);
        if (blockEntity != null) blockEntity.setLevel(minecraft.level);
        return blockEntity;
    }
}
