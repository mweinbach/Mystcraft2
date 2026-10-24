/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.client.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.math.Matrix4f;
import com.mojang.math.Quaternion;
import com.mojang.math.Transformation;
import com.mojang.math.Vector3f;
import java.nio.FloatBuffer;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/**
 * Reads the {@code transform} of a model JSON in Forge's format: {@code "identity"}, a 3x4 or 4x4 row-major matrix
 * (bare or under {@code matrix}), or any of {@code translation}, {@code rotation}/{@code left_rotation},
 * {@code scale}, {@code post-rotation}/{@code right_rotation} and {@code origin}. Rotations are XYZ Euler degrees, a
 * quaternion, or a list of single-axis rotations. The origin defaults to the corner opposite the block origin, as in
 * Forge.
 */
public final class TransformationJson {

    private static final Vector3f ORIGIN_CORNER = new Vector3f(0, 0, 0);
    private static final Vector3f ORIGIN_CENTER = new Vector3f(0.5F, 0.5F, 0.5F);
    private static final Vector3f ORIGIN_OPPOSING_CORNER = new Vector3f(1, 1, 1);
    private static final Set<String> KEYS = Set.of("translation", "rotation", "left_rotation", "scale", "post-rotation", "right_rotation", "origin");

    private TransformationJson() {
    }

    public static Transformation parse(JsonElement json) {
        if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
            if ("identity".equals(json.getAsString())) {
                return Transformation.identity();
            }
            throw new JsonParseException("Unknown transformation: " + json.getAsString());
        } else if (json.isJsonArray()) {
            return new Transformation(matrix(json));
        } else if (!json.isJsonObject()) {
            throw new JsonParseException("Expected a transformation array or object, got " + json);
        }
        JsonObject object = json.getAsJsonObject();
        if (object.has("matrix")) {
            if (object.size() != 1) {
                throw new JsonParseException("A transformation matrix can't be combined with other keys");
            }
            return new Transformation(matrix(object.get("matrix")));
        }
        Set<String> unknown = new HashSet<>(object.keySet());
        unknown.removeAll(KEYS);
        if (!unknown.isEmpty()) {
            throw new JsonParseException("Unknown transformation keys: " + String.join(", ", unknown));
        }
        Vector3f translation = object.has("translation") ? vector(object.get("translation"), "translation") : null;
        Quaternion left = rotation(object, "rotation", "left_rotation");
        Quaternion right = rotation(object, "right_rotation", "post-rotation");
        Vector3f scale = null;
        if (object.has("scale")) {
            JsonElement scaleJson = object.get("scale");
            if (scaleJson.isJsonArray()) {
                scale = vector(scaleJson, "scale");
            } else {
                float uniform = scaleJson.getAsFloat();
                scale = new Vector3f(uniform, uniform, uniform);
            }
        }
        Transformation transformation = new Transformation(translation, left, scale, right);
        Vector3f origin = object.has("origin") ? origin(object.get("origin")) : ORIGIN_OPPOSING_CORNER;
        if (origin.equals(ORIGIN_CENTER)) {
            return transformation;
        }
        float x = origin.x() - ORIGIN_CENTER.x(), y = origin.y() - ORIGIN_CENTER.y(), z = origin.z() - ORIGIN_CENTER.z();
        Matrix4f moved = Matrix4f.createTranslateMatrix(x, y, z);
        moved.multiply(transformation.getMatrix());
        moved.multiply(Matrix4f.createTranslateMatrix(-x, -y, -z));
        return new Transformation(moved);
    }

    private static Matrix4f matrix(JsonElement json) {
        if (!json.isJsonArray()) {
            throw new JsonParseException("A transformation matrix must be an array, got " + json);
        }
        JsonArray rows = json.getAsJsonArray();
        if (rows.size() != 3 && rows.size() != 4) {
            throw new JsonParseException("A transformation matrix needs 3 or 4 rows, got " + rows.size());
        }
        FloatBuffer values = FloatBuffer.allocate(16);
        for (int row = 0; row < 4; row++) {
            float[] columns = row < rows.size() ? floats(rows.get(row), 4, "matrix row") : new float[]{0, 0, 0, 1};
            values.put(columns);
        }
        values.flip();
        Matrix4f matrix = new Matrix4f();
        matrix.loadTransposed(values);
        return matrix;
    }

    @Nullable
    private static Quaternion rotation(JsonObject object, String key, String alternative) {
        if (object.has(key)) {
            return rotation(object.get(key));
        }
        return object.has(alternative) ? rotation(object.get(alternative)) : null;
    }

    private static Quaternion rotation(JsonElement json) {
        if (json.isJsonObject()) {
            return axisRotation(json.getAsJsonObject());
        } else if (!json.isJsonArray()) {
            throw new JsonParseException("Expected a rotation array or object, got " + json);
        }
        JsonArray array = json.getAsJsonArray();
        if (array.size() > 0 && array.get(0).isJsonObject()) {
            Quaternion combined = Quaternion.ONE.copy();
            for (JsonElement axis : array) {
                combined.mul(axisRotation(axis.getAsJsonObject()));
            }
            return combined;
        } else if (array.size() == 3) {
            float[] degrees = floats(array, 3, "rotation");
            return new Quaternion(degrees[0], degrees[1], degrees[2], true);
        } else if (array.size() == 4) {
            float[] quaternion = floats(array, 4, "rotation");
            return new Quaternion(quaternion[0], quaternion[1], quaternion[2], quaternion[3]);
        }
        throw new JsonParseException("A rotation array needs 3 Euler angles or 4 quaternion components, got " + array);
    }

    private static Quaternion axisRotation(JsonObject json) {
        if (json.size() != 1) {
            throw new JsonParseException("An axis rotation needs exactly one axis, got " + json);
        }
        Map.Entry<String, JsonElement> entry = json.entrySet().iterator().next();
        float degrees = entry.getValue().getAsFloat();
        return switch (entry.getKey()) {
            case "x" -> Vector3f.XP.rotationDegrees(degrees);
            case "y" -> Vector3f.YP.rotationDegrees(degrees);
            case "z" -> Vector3f.ZP.rotationDegrees(degrees);
            default -> throw new JsonParseException("Unknown rotation axis: " + entry.getKey());
        };
    }

    private static Vector3f origin(JsonElement json) {
        if (json.isJsonArray()) {
            return vector(json, "origin");
        }
        return switch (json.getAsString()) {
            case "center" -> ORIGIN_CENTER;
            case "corner" -> ORIGIN_CORNER;
            case "opposing-corner" -> ORIGIN_OPPOSING_CORNER;
            default -> throw new JsonParseException("Unknown transformation origin: " + json.getAsString());
        };
    }

    private static Vector3f vector(JsonElement json, String name) {
        float[] values = floats(json, 3, name);
        return new Vector3f(values[0], values[1], values[2]);
    }

    private static float[] floats(JsonElement json, int length, String name) {
        if (!json.isJsonArray() || json.getAsJsonArray().size() != length) {
            throw new JsonParseException("Expected " + length + " numbers for " + name + ", got " + json);
        }
        JsonArray array = json.getAsJsonArray();
        float[] values = new float[length];
        for (int i = 0; i < length; i++) {
            values[i] = array.get(i).getAsFloat();
        }
        return values;
    }
}
