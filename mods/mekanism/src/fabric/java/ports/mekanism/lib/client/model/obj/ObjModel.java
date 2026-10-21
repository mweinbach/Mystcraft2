/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.client.model.obj;

import com.mojang.datafixers.util.Pair;
import com.mojang.math.Matrix3f;
import com.mojang.math.Matrix4f;
import com.mojang.math.Transformation;
import com.mojang.math.Vector3f;
import com.mojang.math.Vector4f;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec2;
import org.jetbrains.annotations.Nullable;
import ports.mekanism.lib.client.model.QuadListModel;
import ports.mekanism.lib.client.model.UnbakedGeometryHelper;
import ports.mekanism.lib.client.model.geometry.IGeometryBakingContext;
import ports.mekanism.lib.client.model.geometry.IUnbakedGeometry;
import ports.mekanism.lib.client.model.pipeline.QuadBakingVertexConsumer;

/**
 * A Wavefront OBJ model. Its first level of {@code o} or {@code g} statements are the root components; the other
 * kind nested inside them are child components named {@code root/child}. Each component can be hidden through the
 * baking context. Faces have at most four vertices; triangles repeat their last vertex.
 */
public class ObjModel implements IUnbakedGeometry<ObjModel> {

    private static final Vec2[] DEFAULT_COORDS = {new Vec2(0, 0), new Vec2(0, 1), new Vec2(1, 1), new Vec2(1, 0)};
    private static final Vector4f WHITE = new Vector4f(1, 1, 1, 1);
    private static final float CULL_EPSILON = 1.0E-4F;

    private final ModelSettings settings;
    private final Map<String, Component> parts = new LinkedHashMap<>();
    private final Set<String> configurableNames = new LinkedHashSet<>();
    private final List<Vector3f> positions = new ArrayList<>();
    private final List<Vec2> texCoords = new ArrayList<>();
    private final List<Vector3f> normals = new ArrayList<>();
    private final List<Vector4f> colors = new ArrayList<>();

    private ObjModel(ModelSettings settings) {
        this.settings = settings;
    }

    @Nullable
    static String[] nextLine(BufferedReader reader) throws IOException {
        StringBuilder builder = new StringBuilder();
        for (String line; (line = reader.readLine()) != null; ) {
            int comment = line.indexOf('#');
            if (comment != -1) {
                line = line.substring(0, comment);
            }
            line = line.strip();
            if (line.endsWith("\\")) {
                builder.append(line, 0, line.length() - 1).append(' ');
                continue;
            }
            builder.append(line);
            String full = builder.toString().strip();
            builder.setLength(0);
            if (!full.isEmpty()) {
                return full.split("\\s+");
            }
        }
        String rest = builder.toString().strip();
        return rest.isEmpty() ? null : rest.split("\\s+");
    }

    static String joinFrom(String[] line, int start) {
        return String.join(" ", List.of(line).subList(Math.min(start, line.length), line.length));
    }

    static ObjModel parse(BufferedReader reader, ModelSettings settings, Function<ResourceLocation, ObjMaterialLibrary> materialLibraries) throws IOException {
        ObjModel model = new ObjModel(settings);
        ResourceLocation modelLocation = settings.modelLocation();
        String directory = modelLocation.getPath().substring(0, modelLocation.getPath().lastIndexOf('/') + 1);
        ObjMaterialLibrary library = settings.mtlOverride() == null ? ObjMaterialLibrary.EMPTY : materialLibraries.apply(new ResourceLocation(settings.mtlOverride()));
        ObjMaterialLibrary.Material material = ObjMaterialLibrary.Material.DEFAULT;
        Component root = null;
        Component child = null;
        Mesh mesh = null;
        //Whichever of "o" and "g" appears first names the root components
        String rootKeyword = null;
        for (String[] line; (line = nextLine(reader)) != null; ) {
            switch (line[0]) {
                case "mtllib" -> {
                    if (settings.mtlOverride() == null) {
                        String name = joinFrom(line, 1);
                        library = materialLibraries.apply(name.contains(":") ? new ResourceLocation(name)
                                                                             : new ResourceLocation(modelLocation.getNamespace(), directory + name));
                    }
                }
                case "usemtl" -> {
                    material = library.getMaterial(joinFrom(line, 1));
                    mesh = null;
                }
                case "v" -> model.positions.add(new Vector3f(Float.parseFloat(line[1]), Float.parseFloat(line[2]), Float.parseFloat(line[3])));
                case "vt" -> model.texCoords.add(new Vec2(Float.parseFloat(line[1]), line.length > 2 ? Float.parseFloat(line[2]) : 0));
                case "vn" -> model.normals.add(new Vector3f(Float.parseFloat(line[1]), Float.parseFloat(line[2]), Float.parseFloat(line[3])));
                case "vc" -> model.colors.add(new Vector4f(Float.parseFloat(line[1]), Float.parseFloat(line[2]), Float.parseFloat(line[3]),
                      line.length > 4 ? Float.parseFloat(line[4]) : 1));
                case "o", "g" -> {
                    String name = joinFrom(line, 1);
                    if (rootKeyword == null) {
                        rootKeyword = line[0];
                    }
                    if (line[0].equals(rootKeyword) || root == null) {
                        root = new Component(name);
                        model.parts.put(name, root);
                        model.configurableNames.add(name);
                        child = null;
                    } else {
                        child = new Component(root.name + "/" + name);
                        root.children.put(name, child);
                        model.configurableNames.add(child.name);
                    }
                    mesh = null;
                }
                case "f" -> {
                    if (mesh == null) {
                        if (root == null) {
                            root = new Component("");
                            model.parts.put("", root);
                        }
                        mesh = new Mesh(material);
                        (child == null ? root : child).meshes.add(mesh);
                    }
                    int[][] vertices = new int[line.length - 1][];
                    for (int i = 0; i < vertices.length; i++) {
                        vertices[i] = model.parseVertex(line[i + 1]);
                    }
                    mesh.faces.add(vertices);
                }
                default -> {
                }
            }
        }
        return model;
    }

    /**
     * @return the position, texture coordinate, normal and color indices of a face vertex, {@code -1} where absent
     */
    private int[] parseVertex(String token) {
        String[] parts = token.split("/");
        int[] indices = {-1, -1, -1, -1};
        int[] sizes = {positions.size(), texCoords.size(), normals.size(), colors.size()};
        for (int i = 0; i < Math.min(parts.length, indices.length); i++) {
            if (!parts[i].isEmpty()) {
                int index = Integer.parseInt(parts[i]);
                indices[i] = index < 0 ? sizes[i] + index : index - 1;
            }
        }
        return indices;
    }

    public Set<String> getRootComponentNames() {
        return Collections.unmodifiableSet(parts.keySet());
    }

    @Override
    public Set<String> getConfigurableComponentNames() {
        return Collections.unmodifiableSet(configurableNames);
    }

    @Override
    public Collection<Material> getMaterials(IGeometryBakingContext context, Function<ResourceLocation, UnbakedModel> modelGetter,
          Set<Pair<String, String>> missingTextureErrors) {
        Set<Material> materials = new HashSet<>();
        for (Component component : parts.values()) {
            component.collectMaterials(context, materials);
        }
        return materials;
    }

    @Override
    public BakedModel bake(IGeometryBakingContext context, ModelBakery bakery, Function<Material, TextureAtlasSprite> spriteGetter, ModelState modelState,
          ItemOverrides overrides, ResourceLocation modelLocation) {
        ModelState state = UnbakedGeometryHelper.composeRootTransform(modelState, context.getRootTransform());
        TextureAtlasSprite particle = spriteGetter.apply(context.hasMaterial("particle") ? context.getMaterial("particle") : missingMaterial());
        QuadListModel.Builder builder = QuadListModel.builder(context, particle, overrides, context.getTransforms());
        VertexTransform transform = new VertexTransform(state.getRotation());
        for (Component component : parts.values()) {
            if (context.isComponentVisible(component.name, true)) {
                component.addQuads(this, context, builder, spriteGetter, transform);
            }
        }
        return builder.build();
    }

    private static Material missingMaterial() {
        return new Material(TextureAtlas.LOCATION_BLOCKS, MissingTextureAtlasSprite.getLocation());
    }

    private static Material resolveMaterial(@Nullable String texture, IGeometryBakingContext context) {
        if (texture == null) {
            return missingMaterial();
        } else if (texture.startsWith("#")) {
            return context.getMaterial(texture.substring(1));
        }
        return new Material(TextureAtlas.LOCATION_BLOCKS, new ResourceLocation(texture.replace('\\', '/')));
    }

    private void addFace(int[][] face, ObjMaterialLibrary.Material material, TextureAtlasSprite sprite, VertexTransform transform, QuadListModel.Builder builder) {
        Vector3f faceNormal = null;
        for (int i = 0; i < 4; i++) {
            int[] vertex = face[Math.min(i, face.length - 1)];
            if (vertex[2] < 0 || vertex[2] >= normals.size()) {
                faceNormal = computeFaceNormal(face);
                break;
            }
        }
        QuadBakingVertexConsumer.Buffered baker = new QuadBakingVertexConsumer.Buffered();
        baker.setSprite(sprite);
        baker.setTintIndex(material.diffuseTintIndex);
        int packedLight = 0;
        if (settings.emissiveAmbient()) {
            int fakeLight = (int) ((material.ambientColor.x() + material.ambientColor.y() + material.ambientColor.z()) * 15 / 3.0F);
            packedLight = LightTexture.pack(fakeLight, fakeLight);
            baker.setShade(fakeLight == 0 && settings.shadeQuads());
        } else {
            baker.setShade(settings.shadeQuads());
        }
        Vector3f[] transformedPositions = new Vector3f[4];
        for (int i = 0; i < 4; i++) {
            int[] vertex = face[Math.min(i, face.length - 1)];
            Vector3f position = transform.position(positions.get(vertex[0]));
            Vector3f normal = transform.normal(faceNormal != null ? faceNormal : normals.get(vertex[2]));
            Vec2 uv = vertex[1] >= 0 && vertex[1] < texCoords.size() ? texCoords.get(vertex[1]) : DEFAULT_COORDS[i];
            Vector4f color = vertex[3] >= 0 && vertex[3] < colors.size() ? colors.get(vertex[3]) : WHITE;
            baker.vertex(position.x(), position.y(), position.z());
            baker.color(color.x() * material.diffuseColor.x(), color.y() * material.diffuseColor.y(), color.z() * material.diffuseColor.z(),
                  color.w() * material.diffuseColor.w());
            baker.uv(sprite.getU(uv.x * 16), sprite.getV((settings.flipV() ? 1 - uv.y : uv.y) * 16));
            baker.uv2(packedLight & 0xFFFF, packedLight >> 16 & 0xFFFF);
            baker.normal(normal.x(), normal.y(), normal.z());
            if (i == 0) {
                baker.setDirection(Direction.getNearest(normal.x(), normal.y(), normal.z()));
            }
            baker.endVertex();
            transformedPositions[i] = position;
        }
        Direction cull = settings.automaticCulling() ? cullFace(transformedPositions, baker.getQuad().getDirection()) : null;
        if (cull == null) {
            builder.addUnculledFace(baker.getQuad());
        } else {
            builder.addCulledFace(cull, baker.getQuad());
        }
    }

    private Vector3f computeFaceNormal(int[][] face) {
        Vector3f a = positions.get(face[0][0]);
        Vector3f ab = positions.get(face[Math.min(1, face.length - 1)][0]).copy();
        Vector3f ac = positions.get(face[Math.min(2, face.length - 1)][0]).copy();
        ab.sub(a);
        ac.sub(a);
        ab.cross(ac);
        ab.normalize();
        return ab;
    }

    /**
     * @return the block face a quad lies on and faces out of, if any
     */
    @Nullable
    private static Direction cullFace(Vector3f[] positions, Direction facing) {
        Direction.Axis axis = facing.getAxis();
        float bound = facing.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0;
        for (Vector3f position : positions) {
            if (Math.abs(axis.choose(position.x(), position.y(), position.z()) - bound) > CULL_EPSILON) {
                return null;
            }
        }
        return facing;
    }

    public record ModelSettings(ResourceLocation modelLocation, boolean automaticCulling, boolean shadeQuads, boolean flipV, boolean emissiveAmbient,
                                @Nullable String mtlOverride) {
    }

    private static final class Mesh {

        private final ObjMaterialLibrary.Material material;
        private final List<int[][]> faces = new ArrayList<>();

        private Mesh(ObjMaterialLibrary.Material material) {
            this.material = material;
        }
    }

    private static final class Component {

        private final String name;
        private final List<Mesh> meshes = new ArrayList<>();
        private final Map<String, Component> children = new LinkedHashMap<>();

        private Component(String name) {
            this.name = name;
        }

        private void collectMaterials(IGeometryBakingContext context, Set<Material> materials) {
            for (Mesh mesh : meshes) {
                materials.add(resolveMaterial(mesh.material.diffuseColorMap, context));
            }
            for (Component child : children.values()) {
                child.collectMaterials(context, materials);
            }
        }

        private void addQuads(ObjModel model, IGeometryBakingContext context, QuadListModel.Builder builder, Function<Material, TextureAtlasSprite> spriteGetter,
              VertexTransform transform) {
            for (Mesh mesh : meshes) {
                TextureAtlasSprite sprite = spriteGetter.apply(resolveMaterial(mesh.material.diffuseColorMap, context));
                for (int[][] face : mesh.faces) {
                    model.addFace(face, mesh.material, sprite, transform, builder);
                }
            }
            for (Component child : children.values()) {
                if (context.isComponentVisible(child.name, true)) {
                    child.addQuads(model, context, builder, spriteGetter, transform);
                }
            }
        }
    }

    /**
     * Applies a model state's transformation, which is relative to the block center, to positions and normals relative
     * to the block corner.
     */
    private static final class VertexTransform {

        @Nullable
        private final Matrix4f positionMatrix;
        @Nullable
        private final Matrix3f normalMatrix;

        private VertexTransform(Transformation transformation) {
            if (transformation.equals(Transformation.identity())) {
                positionMatrix = null;
                normalMatrix = null;
            } else {
                Matrix4f matrix = Matrix4f.createTranslateMatrix(0.5F, 0.5F, 0.5F);
                matrix.multiply(transformation.getMatrix());
                matrix.multiply(Matrix4f.createTranslateMatrix(-0.5F, -0.5F, -0.5F));
                positionMatrix = matrix;
                Matrix3f normal = new Matrix3f(transformation.getMatrix());
                if (normal.invert()) {
                    normal.transpose();
                }
                normalMatrix = normal;
            }
        }

        private Vector3f position(Vector3f position) {
            if (positionMatrix == null) {
                return position.copy();
            }
            Vector4f transformed = new Vector4f(position.x(), position.y(), position.z(), 1);
            transformed.transform(positionMatrix);
            return new Vector3f(transformed.x(), transformed.y(), transformed.z());
        }

        private Vector3f normal(Vector3f normal) {
            Vector3f transformed = normal.copy();
            if (normalMatrix != null) {
                transformed.transform(normalMatrix);
                transformed.normalize();
            }
            return transformed;
        }
    }
}
