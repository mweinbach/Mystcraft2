package ports.jurassicraft.model.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/** The OBJ subset produced by the JurassiCraft Tabula cuboid converter. */
final class MachineObjMesh {
    record Point(float x, float y, float z) {}
    record TexCoord(float u, float v) {}
    record Vertex(Point point, TexCoord uv) {}
    record Face(List<Vertex> vertices) {}

    private final List<Face> faces;

    private MachineObjMesh(List<Face> faces) {
        this.faces = List.copyOf(faces);
    }

    List<Face> faces() { return faces; }

    static MachineObjMesh read(Reader source) throws IOException {
        List<Point> points = new ArrayList<>();
        List<TexCoord> texCoords = new ArrayList<>();
        List<Face> faces = new ArrayList<>();
        BufferedReader reader = source instanceof BufferedReader buffered ? buffered : new BufferedReader(source);
        String line;
        int lineNumber = 0;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            String[] comment = line.split("#", 2);
            String content = comment[0].trim();
            if (content.isEmpty()) continue;
            String[] parts = content.split("\\s+");
            try {
                switch (parts[0]) {
                    case "v" -> {
                        if (parts.length < 4) throw new IllegalArgumentException("position needs three coordinates");
                        points.add(new Point(number(parts[1]), number(parts[2]), number(parts[3])));
                    }
                    case "vt" -> {
                        if (parts.length < 3) throw new IllegalArgumentException("texture coordinate needs two components");
                        texCoords.add(new TexCoord(number(parts[1]), number(parts[2])));
                    }
                    case "f" -> {
                        if (parts.length != 4 && parts.length != 5) throw new IllegalArgumentException("only triangles and quads are supported");
                        List<Vertex> vertices = new ArrayList<>(4);
                        for (int i = 1; i < parts.length; i++) {
                            String[] indices = parts[i].split("/", -1);
                            if (indices.length < 2 || indices[1].isEmpty()) throw new IllegalArgumentException("each vertex needs a texture coordinate");
                            vertices.add(new Vertex(points.get(index(indices[0], points.size())), texCoords.get(index(indices[1], texCoords.size()))));
                        }
                        if (vertices.size() == 3) vertices.add(vertices.get(2));
                        faces.add(new Face(List.copyOf(vertices)));
                    }
                    // Object, group, smoothing and material directives are informational here:
                    // the generated meshes each use one texture specified by the model JSON.
                    default -> { }
                }
            } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
                throw new IOException("Invalid OBJ at line " + lineNumber + ": " + e.getMessage(), e);
            }
        }
        if (faces.isEmpty()) throw new IOException("OBJ contains no faces");
        return new MachineObjMesh(faces);
    }

    private static float number(String value) {
        float parsed = Float.parseFloat(value);
        if (!Float.isFinite(parsed)) throw new IllegalArgumentException("non-finite coordinate");
        return parsed;
    }

    private static int index(String value, int size) {
        int i = Integer.parseInt(value);
        if (i == 0) throw new IllegalArgumentException("OBJ indices start at one");
        return i > 0 ? i - 1 : size + i;
    }
}
