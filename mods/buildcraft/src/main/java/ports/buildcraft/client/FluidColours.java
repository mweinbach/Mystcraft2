package ports.buildcraft.client;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * Upstream {@code FluidRenderer.getAverageFluidColour}: the mean of every texel of every frame of the
 * still sprite, tinted. Read from the texture file, since vanilla keeps sprite pixels private.
 */
public final class FluidColours {
    private static final Map<Fluid, Integer> AVERAGES = new HashMap<>();

    private FluidColours() {}

    /** Opaque ARGB, or -1 (white) for no fluid or an unreadable sprite. */
    public static int average(Fluid fluid) {
        if (fluid == Fluids.EMPTY) return -1;
        return AVERAGES.computeIfAbsent(fluid, FluidColours::compute);
    }

    private static int compute(Fluid fluid) {
        ResourceLocation name = ClientPlatform.get().fluidSprite(fluid).getName();
        ResourceLocation file = new ResourceLocation(name.getNamespace(), "textures/" + name.getPath() + ".png");
        long r = 0, g = 0, b = 0, count = 0;
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(file);
        if (resource.isEmpty()) return -1;
        try (InputStream stream = resource.get().open(); NativeImage image = NativeImage.read(stream)) {
            for (int x = 0; x < image.getWidth(); x++) {
                for (int y = 0; y < image.getHeight(); y++) {
                    int abgr = image.getPixelRGBA(x, y);
                    r += abgr & 0xFF;
                    g += abgr >> 8 & 0xFF;
                    b += abgr >> 16 & 0xFF;
                    count++;
                }
            }
        } catch (IOException e) {
            return -1;
        }
        if (count == 0) return -1;
        int tint = ClientPlatform.get().fluidColour(fluid);
        int red = (int) Math.min(255, r / count * (tint >> 16 & 0xFF) / 255);
        int green = (int) Math.min(255, g / count * (tint >> 8 & 0xFF) / 255);
        int blue = (int) Math.min(255, b / count * (tint & 0xFF) / 255);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }
}
