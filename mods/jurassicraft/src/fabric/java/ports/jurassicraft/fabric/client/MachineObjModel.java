package ports.jurassicraft.fabric.client;

import com.mojang.datafixers.util.Pair;
import com.mojang.math.Vector4f;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Function;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/** Bakes the generated OBJ faces using the same blockstate transforms as vanilla models. */
final class MachineObjModel implements UnbakedModel {
    private final MachineObjMesh mesh;
    private final Material surface;
    private final Material particle;
    private final ItemTransforms transforms;

    MachineObjModel(MachineObjMesh mesh, ResourceLocation surface, ResourceLocation particle, ItemTransforms transforms) {
        this.mesh = mesh;
        this.surface = new Material(TextureAtlas.LOCATION_BLOCKS, surface);
        this.particle = new Material(TextureAtlas.LOCATION_BLOCKS, particle);
        this.transforms = transforms == null ? ItemTransforms.NO_TRANSFORMS : transforms;
    }

    ItemTransforms transforms() { return transforms; }

    @Override public Collection<ResourceLocation> getDependencies() { return List.of(); }

    @Override public Collection<Material> getMaterials(Function<ResourceLocation, UnbakedModel> models,
                                                         Set<Pair<String, String>> missing) {
        return List.of(surface, particle);
    }

    @Override public BakedModel bake(ModelBakery bakery, Function<Material, TextureAtlasSprite> sprites,
                                     ModelState state, ResourceLocation location) {
        TextureAtlasSprite texture = sprites.apply(surface);
        List<BakedQuad> quads = new ArrayList<>(mesh.faces().size());
        for (MachineObjMesh.Face face : mesh.faces()) {
            BakedFace baked = bakeFace(face, state, texture::getU, texture::getV);
            quads.add(new BakedQuad(baked.data(), -1, baked.direction(), texture, true));
        }
        return new BakedMachine(List.copyOf(quads), sprites.apply(particle), transforms);
    }

    record BakedFace(int[] data, Direction direction) {}

    static BakedFace bakeFace(MachineObjMesh.Face face, ModelState state, DoubleUnaryOperator u, DoubleUnaryOperator v) {
        int[] data = new int[32];
        float[][] positions = new float[4][3];
        for (int i = 0; i < 4; i++) {
            MachineObjMesh.Vertex vertex = face.vertices().get(i);
            MachineObjMesh.Point p = vertex.point();
            Vector4f rotated = new Vector4f(p.x() - .5F, p.y() - .5F, p.z() - .5F, 1F);
            rotated.transform(state.getRotation().getMatrix());
            positions[i][0] = rotated.x() + .5F;
            positions[i][1] = rotated.y() + .5F;
            positions[i][2] = rotated.z() + .5F;
            int offset = i * 8;
            for (int coordinate = 0; coordinate < 3; coordinate++) {
                data[offset + coordinate] = Float.floatToRawIntBits(positions[i][coordinate]);
            }
            data[offset + 3] = -1;
            data[offset + 4] = Float.floatToRawIntBits((float) u.applyAsDouble(vertex.uv().u() * 16));
            // Forge's source models use flip_v=false; their Tabula UVs are already oriented.
            data[offset + 5] = Float.floatToRawIntBits((float) v.applyAsDouble(vertex.uv().v() * 16));
        }
        float ax = positions[1][0] - positions[0][0];
        float ay = positions[1][1] - positions[0][1];
        float az = positions[1][2] - positions[0][2];
        float bx = positions[2][0] - positions[0][0];
        float by = positions[2][1] - positions[0][1];
        float bz = positions[2][2] - positions[0][2];
        float nx = ay * bz - az * by;
        float ny = az * bx - ax * bz;
        float nz = ax * by - ay * bx;
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length > 0) { nx /= length; ny /= length; nz /= length; }
        int normal = ((int) (nx * 127F) & 255) | (((int) (ny * 127F) & 255) << 8)
                | (((int) (nz * 127F) & 255) << 16);
        for (int i = 0; i < 4; i++) data[i * 8 + 7] = normal;
        return new BakedFace(data, Direction.getNearest(nx, ny, nz));
    }

    private record BakedMachine(List<BakedQuad> quads, TextureAtlasSprite particle, ItemTransforms transforms) implements BakedModel {
        @Override public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random) {
            // The converted meshes explicitly disable automatic face culling.
            return side == null ? quads : List.of();
        }
        @Override public boolean useAmbientOcclusion() { return true; }
        @Override public boolean isGui3d() { return true; }
        @Override public boolean usesBlockLight() { return true; }
        @Override public boolean isCustomRenderer() { return false; }
        @Override public TextureAtlasSprite getParticleIcon() { return particle; }
        @Override public ItemTransforms getTransforms() { return transforms; }
        @Override public ItemOverrides getOverrides() { return ItemOverrides.EMPTY; }
    }
}
