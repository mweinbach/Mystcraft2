package net.mystcraft.minecraft.age.visual;

import java.util.List;
import net.mystcraft.minecraft.age.visual.AgeVisualSettings.Provider;
import net.mystcraft.minecraft.age.visual.AgeVisualSettings.Rgb;

/** Classic color-provider and light-table math shared by both loader adapters and tests. */
public final class AgeVisualMath {
    private AgeVisualMath() {}
    public static float daylight(float angle) {
        return Math.max(0, Math.min(1, (float) Math.cos(angle * Math.PI * 2) * 2 + .5F));
    }
    public static Rgb sky(AgeVisualSettings settings, long time, float partial, float angle, float temperature) {
        Rgb result = null;
        float alpha = daylight(angle);
        for (Provider provider : settings.sky()) {
            Rgb value = provider.mode().equals("natural") ? naturalSky(temperature) : provider.color((time + partial) / 12000D);
            value = value.multiply(provider.mode().equals("night") ? 1 - alpha : alpha);
            result = result == null ? value : result.average(value);
        }
        return result;
    }
    public static Rgb fog(AgeVisualSettings settings, long time, float partial, float angle) {
        Rgb result = null;
        for (Provider provider : settings.fog()) {
            Rgb value;
            if (provider.mode().equals("natural")) {
                float alpha = daylight(angle);
                value = new Rgb(.7529412F * (alpha * .94F + .06F), .8470588F * (alpha * .94F + .06F), alpha * .91F + .09F);
            } else {
                value = provider.color((time + partial) / 12000D);
                if (value.red() == 0 && value.green() == 0 && value.blue() == 0) value = new Rgb(.0001F, .0001F, .0001F);
            }
            result = result == null ? value : result.average(value);
        }
        return result;
    }
    public static int water(AgeVisualSettings settings, int natural) {
        if (settings.water().isEmpty()) return natural;
        Provider first = settings.water().get(0);
        // Classic AgeController ignored Color.average's returned value for static providers;
        // preserving that observable behavior means the first water provider wins.
        return first.mode().equals("natural") ? natural : first.color(0).packed();
    }

    public static int staticColor(List<Provider> providers, int natural) {
        return providers.isEmpty() || providers.get(0).mode().equals("natural") ? natural : providers.get(0).color(0).packed();
    }
    /** WorldProviderMyst.getCloudColor, including its distinct .95 weather blend strength. */
    public static Rgb cloud(AgeVisualSettings settings, long time, float partial, float angle, float rain, float thunder) {
        Rgb result = null;
        for (Provider provider : settings.environment().cloud()) {
            Rgb value = provider.mode().equals("natural") ? new Rgb(1, 1, 1) : provider.color((time + partial) / 12000D);
            result = result == null ? value : result.average(value);
        }
        if (result == null) result = new Rgb(1, 1, 1);
        result = cloudDesaturate(result, rain, .6F);
        float day = daylight(angle);
        result = new Rgb(result.red() * (day * .9F + .1F), result.green() * (day * .9F + .1F), result.blue() * (day * .85F + .15F));
        return cloudDesaturate(result, thunder, .2F);
    }
    private static Rgb cloudDesaturate(Rgb c, float strength, float greyFactor) {
        float grey = (c.red() * .3F + c.green() * .59F + c.blue() * .11F) * greyFactor, original = 1 - strength * .95F;
        return new Rgb(c.red() * original + grey * (1 - original), c.green() * original + grey * (1 - original), c.blue() * original + grey * (1 - original));
    }

    public static float lightBrightness(String lighting, int level) {
        float inverse = 1 - Math.max(0, Math.min(15, level)) / 15F;
        float normal = (1 - inverse) / (inverse * 3 + 1);
        return switch (lighting) { case "dark" -> normal / 2; case "bright" -> normal * .75F + .25F; default -> normal; };
    }
    /** Same environment blending performed after the classic sky providers. */
    public static Rgb weather(Rgb color, float rain, float thunder, float lightning) {
        Rgb out = desaturate(color, rain, .6F);
        out = desaturate(out, thunder, .2F);
        float flash = Math.max(0, Math.min(1, lightning)) * .45F;
        return new Rgb(out.red() * (1 - flash) + .8F * flash, out.green() * (1 - flash) + .8F * flash, out.blue() * (1 - flash) + flash);
    }
    private static Rgb desaturate(Rgb color, float strength, float greyFactor) {
        float grey = (color.red() * .3F + color.green() * .59F + color.blue() * .11F) * greyFactor;
        float original = 1 - strength * .75F;
        return new Rgb(color.red() * original + grey * (1 - original), color.green() * original + grey * (1 - original), color.blue() * original + grey * (1 - original));
    }
    private static Rgb naturalSky(float temperature) {
        float scaled = Math.max(-1, Math.min(1, temperature / 3));
        return rgb(java.awt.Color.HSBtoRGB(.62222224F - scaled * .05F, .5F + scaled * .1F, 1));
    }
    public static Rgb rgb(int value) { return new Rgb((value >> 16 & 255) / 255F, (value >> 8 & 255) / 255F, (value & 255) / 255F); }
}
