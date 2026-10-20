package ports.buildcraft.builders.snapshot;

import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.TreeMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * Upstream {@code HashUtil}: SHA-256 over a stable NBT encoding. Compound keys are written in sorted
 * order, so the hash does not depend on map iteration order.
 */
public final class SnapshotHash {
    private SnapshotHash() {}

    public static byte[] compute(CompoundTag tag) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        try (DataOutputStream out = new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), digest))) {
            writeStableCompound(tag, out);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write to a digest stream", e);
        }
        return digest.digest();
    }

    public static String toHex(byte[] hash) {
        StringBuilder builder = new StringBuilder();
        for (byte b : hash) {
            String s = Integer.toString(Byte.toUnsignedInt(b), 16);
            if (s.length() < 2) builder.append('0');
            builder.append(s);
        }
        return builder.toString();
    }

    public static byte[] fromHex(String hex) {
        byte[] hash = new byte[hex.length() / 2];
        for (int i = 0; i < hash.length; i++) hash[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        return hash;
    }

    /** Upstream writes a terminating zero byte after every entry, not only after the last. */
    private static void writeStableCompound(CompoundTag tag, DataOutput out) throws IOException {
        TreeMap<String, Tag> entries = new TreeMap<>();
        for (String key : tag.getAllKeys()) entries.put(key, tag.get(key));
        for (String key : entries.keySet()) {
            Tag entry = entries.get(key);
            byte id = entry.getId();
            out.writeByte(id);
            if (id != 0) {
                out.writeUTF(key);
                writeStable(entry, out);
            }
            out.writeByte(0);
        }
    }

    private static void writeStableList(ListTag list, DataOutput out) throws IOException {
        out.writeByte(list.isEmpty() ? 0 : list.get(0).getId());
        out.writeInt(list.size());
        for (Tag element : list) writeStable(element, out);
    }

    private static void writeStable(Tag tag, DataOutput out) throws IOException {
        if (tag instanceof CompoundTag compound) writeStableCompound(compound, out);
        else if (tag instanceof ListTag list) writeStableList(list, out);
        else tag.write(out);
    }
}
