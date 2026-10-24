/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.client.model.obj;

import com.mojang.math.Vector4f;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * The materials of a Wavefront MTL file. Besides the standard {@code Ka}, {@code Kd}, {@code map_Kd}, {@code d} and
 * {@code Tr} statements it reads {@code forge_TintIndex}, the tint index of faces using the material.
 */
public class ObjMaterialLibrary {

    public static final ObjMaterialLibrary EMPTY = new ObjMaterialLibrary();

    private final Map<String, Material> materials = new HashMap<>();

    private ObjMaterialLibrary() {
    }

    public static ObjMaterialLibrary read(BufferedReader reader) throws IOException {
        ObjMaterialLibrary library = new ObjMaterialLibrary();
        Material current = null;
        for (String[] line; (line = ObjModel.nextLine(reader)) != null; ) {
            String keyword = line[0];
            if (keyword.equals("newmtl")) {
                current = new Material(ObjModel.joinFrom(line, 1));
                library.materials.put(current.name, current);
            } else if (current != null) {
                switch (keyword) {
                    case "Ka" -> current.ambientColor = parseColor(line, current.ambientColor.w());
                    case "map_Ka" -> current.ambientColorMap = ObjModel.joinFrom(line, 1);
                    case "Kd" -> current.diffuseColor = parseColor(line, current.diffuseColor.w());
                    case "map_Kd" -> current.diffuseColorMap = ObjModel.joinFrom(line, 1);
                    case "forge_TintIndex" -> current.diffuseTintIndex = Integer.parseInt(line[1]);
                    case "d" -> setAlpha(current, Float.parseFloat(line[1]));
                    case "Tr" -> setAlpha(current, 1 - Float.parseFloat(line[1]));
                    default -> {
                    }
                }
            }
        }
        return library;
    }

    private static Vector4f parseColor(String[] line, float alpha) {
        float r = Float.parseFloat(line[1]);
        float g = line.length > 2 ? Float.parseFloat(line[2]) : r;
        float b = line.length > 3 ? Float.parseFloat(line[3]) : r;
        return new Vector4f(r, g, b, alpha);
    }

    private static void setAlpha(Material material, float alpha) {
        material.dissolve = alpha;
        material.ambientColor = withAlpha(material.ambientColor, alpha);
        material.diffuseColor = withAlpha(material.diffuseColor, alpha);
    }

    private static Vector4f withAlpha(Vector4f color, float alpha) {
        return new Vector4f(color.x(), color.y(), color.z(), alpha);
    }

    public Material getMaterial(String name) {
        Material material = materials.get(name);
        return material == null ? Material.DEFAULT : material;
    }

    public static class Material {

        static final Material DEFAULT = new Material("default");

        public final String name;
        public Vector4f ambientColor = new Vector4f(0, 0, 0, 1);
        @Nullable
        public String ambientColorMap;
        public Vector4f diffuseColor = new Vector4f(1, 1, 1, 1);
        @Nullable
        public String diffuseColorMap;
        public int diffuseTintIndex = -1;
        public float dissolve = 1;

        public Material(String name) {
            this.name = name;
        }
    }
}
