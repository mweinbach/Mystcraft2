package net.mystcraft.minecraft.linking;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import net.mystcraft.minecraft.writing.PageData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Classic LinkPropertyAPI flags, read without mutating the source book or page payload. */
public record LinkModifiers(boolean intraLinking, boolean intraLinkingOnly, boolean following,
        boolean disarm, boolean maintainMomentum, boolean generatePlatform, boolean relative) {
    public static final String INTRA_LINKING = "Intra Linking";
    public static final String INTRA_LINKING_ONLY = "Intra Linking Only";
    public static final String FOLLOWING = "Following";
    public static final String DISARM = "Disarm";
    public static final String MAINTAIN_MOMENTUM = "Maintain Momentum";
    public static final String GENERATE_PLATFORM = "Generate Platform";
    public static final String RELATIVE = "Relative";

    public static LinkModifiers from(ItemStack book) {
        CompoundTag data = book.hasTag() ? book.getTag() : new CompoundTag();
        Set<String> properties = new HashSet<>();
        PageData.linkProperties(data).forEach(flag -> properties.add(normalize(flag)));
        var pages = PageData.readPages(data);
        if (!pages.isEmpty()) PageData.linkProperties(pages.get(0)).forEach(flag -> properties.add(normalize(flag)));
        // Actual legacy book flags are a compound, not a list. Explicit false wins over panel data.
        CompoundTag flags = data.getCompound("Flags");
        for (String key : flags.getAllKeys()) if (flags.contains(key, Tag.TAG_ANY_NUMERIC)) {
            if (flags.getBoolean(key)) properties.add(normalize(key));
            else properties.remove(normalize(key));
        }
        return new LinkModifiers(properties.contains(normalize(INTRA_LINKING)), properties.contains(normalize(INTRA_LINKING_ONLY)),
                properties.contains(normalize(FOLLOWING)), properties.contains(normalize(DISARM)),
                properties.contains(normalize(MAINTAIN_MOMENTUM)), properties.contains(normalize(GENERATE_PLATFORM)),
                properties.contains(normalize(RELATIVE)));
    }
    private static String normalize(String flag) { return flag.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", ""); }
    public boolean allowsDimension(boolean sameDimension) { return sameDimension ? intraLinking || intraLinkingOnly : !intraLinkingOnly; }

    /** Exact two-stage horizontal redirection and upward nudge from LinkListenerBasic.handleMomentum. */
    public Vec3 arrivalMotion(Vec3 before, float targetYaw, boolean forceMaintain) {
        if (!maintainMomentum && !forceMaintain) return new Vec3(0, 0.2, 0);
        double heading = Math.atan2(before.x, before.z);
        double localX = Math.cos(-heading) * before.x - Math.sin(-heading) * before.z;
        double localZ = Math.sin(-heading) * before.x + Math.cos(-heading) * before.z;
        double yaw = Math.toRadians(targetYaw);
        return new Vec3(Math.cos(yaw) * localX - Math.sin(yaw) * localZ, before.y + 0.2,
                Math.sin(yaw) * localX + Math.cos(yaw) * localZ);
    }
}
