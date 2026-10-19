package ports.buildcraft.transport;

import net.minecraft.util.StringRepresentable;

/**
 * The model state of one pipe face. {@code SPECIAL} is a connected face drawn with the pipe's
 * alternate texture: the extracting face of a wooden pipe, the output of an iron pipe, or the
 * engine-facing side of a wooden kinesis pipe.
 */
public enum PipeSide implements StringRepresentable {
    NONE("none"),
    CONNECTED("connected"),
    SPECIAL("special");

    private final String name;

    PipeSide(String name) {
        this.name = name;
    }

    public boolean isConnected() {
        return this != NONE;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
