package ports.buildcraft.builders.snapshot;

/** Upstream {@code InvalidInputDataException}: stored snapshot or schematic data that cannot be read. */
public class InvalidSnapshotException extends Exception {
    public InvalidSnapshotException(String message) {
        super(message);
    }

    public InvalidSnapshotException(String message, Throwable cause) {
        super(message, cause);
    }
}
