package ports.buildcraft.builders.snapshot;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ports.buildcraft.platform.Platform;

/**
 * Upstream {@code GlobalSavedDataSnapshots}: every snapshot is a file named by its hash in
 * {@code snapshots-server} or {@code snapshots-client} next to the saves folder, shared by all worlds. Files
 * are gzipped NBT, which is what upstream writes; upstream's reader also took its own packed format.
 */
public final class SnapshotStorage {
    private static final Logger LOGGER = LoggerFactory.getLogger("BuildCraft");
    private static final String SNAPSHOT_FILE_EXTENSION = ".bcnbt";
    /** Not upstream: past this many bytes of stored snapshots, uploads are refused. */
    public static final long MAX_UPLOAD_STORAGE_BYTES = 1024L * 1024 * 1024;
    private static final Map<Side, SnapshotStorage> INSTANCES = new EnumMap<>(Side.class);

    public enum Side { CLIENT, SERVER }

    private final Path directory;
    private final LoadingCache<Snapshot.Key, Optional<Snapshot>> snapshotsCache = CacheBuilder.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build(CacheLoader.from(key -> Optional.ofNullable(readSnapshot(key)).map(Found::snapshot)));
    @Nullable private List<Snapshot.Key> list;
    private long listReadAt;

    private record Found(Snapshot snapshot, Path file) {}

    private SnapshotStorage(Side side) {
        directory = Platform.get().gameDirectory().resolve("snapshots-" + side.name().toLowerCase(Locale.ROOT));
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to make the directories required for snapshots: " + directory, e);
        }
    }

    public static synchronized SnapshotStorage get(Side side) {
        return INSTANCES.computeIfAbsent(side, SnapshotStorage::new);
    }

    public static SnapshotStorage get(Level level) {
        return get(level.isClientSide ? Side.CLIENT : Side.SERVER);
    }

    public Path directory() {
        return directory;
    }

    private List<Path> files() {
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*" + SNAPSHOT_FILE_EXTENSION)) {
            stream.forEach(files::add);
        } catch (IOException e) {
            LOGGER.error("Failed to list the snapshots in {}", directory, e);
        }
        return files;
    }

    @Nullable
    private static Snapshot read(Path file) {
        try {
            return Snapshot.readFromNBT(NbtIo.readCompressed(file.toFile()));
        } catch (IOException | InvalidSnapshotException | RuntimeException e) {
            LOGGER.error("Failed to read the snapshot {}", file, e);
            return null;
        }
    }

    @Nullable
    private Found readSnapshot(Snapshot.Key key) {
        for (Path file : files()) {
            if (!file.getFileName().toString().startsWith(key.toString())) continue;
            Snapshot snapshot = read(file);
            if (snapshot != null && Objects.equals(snapshot.key, key)) return new Found(snapshot, file);
        }
        return null;
    }

    private List<Snapshot.Key> readList() {
        List<Snapshot.Key> keys = new ArrayList<>();
        for (Path file : files()) {
            Snapshot snapshot = read(file);
            if (snapshot != null && file.getFileName().toString().startsWith(snapshot.key.toString())) keys.add(snapshot.key);
        }
        return List.copyOf(keys);
    }

    /**
     * Not upstream: whether the stored snapshots take less than {@value #MAX_UPLOAD_STORAGE_BYTES} bytes, so that
     * an upload may be stored. Snapshots the server makes itself, by scanning, are always stored.
     */
    public synchronized boolean hasRoomForUploads() {
        long total = 0;
        for (Path file : files()) {
            try {
                total += Files.size(file);
            } catch (IOException e) {
                LOGGER.error("Failed to measure the snapshot file {}", file, e);
            }
            if (total >= MAX_UPLOAD_STORAGE_BYTES) return false;
        }
        return true;
    }

    public synchronized void addSnapshot(Snapshot snapshot) {
        Path file = directory.resolve(snapshot.key + SNAPSHOT_FILE_EXTENSION);
        if (!Files.exists(file)) {
            try {
                NbtIo.writeCompressed(Snapshot.writeToNBT(snapshot), file.toFile());
            } catch (IOException e) {
                LOGGER.error("Failed to write the snapshot file {}", file, e);
            }
        }
        snapshotsCache.invalidate(snapshot.key);
        list = null;
    }

    public synchronized void removeSnapshot(Snapshot.Key key) {
        Found found = readSnapshot(key);
        if (found != null) {
            try {
                Files.delete(found.file());
            } catch (IOException e) {
                LOGGER.error("Failed to delete the snapshot file {}", found.file(), e);
            }
            snapshotsCache.invalidate(key);
        }
        list = null;
    }

    @Nullable
    public Snapshot getSnapshot(@Nullable Snapshot.Key key) {
        return key == null ? null : snapshotsCache.getUnchecked(key).orElse(null);
    }

    /** Upstream caches the list for a second. */
    public synchronized List<Snapshot.Key> getList() {
        long now = System.currentTimeMillis();
        if (list == null || now - listReadAt > 1000) {
            list = readList();
            listReadAt = now;
        }
        return list;
    }
}
