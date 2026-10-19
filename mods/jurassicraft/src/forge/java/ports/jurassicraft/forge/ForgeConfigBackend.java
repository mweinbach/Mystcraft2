package ports.jurassicraft.forge;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import ports.jurassicraft.config.ConfigOption;

/** Persists the shared options as Forge TOML specs and copies loaded values back into them. */
final class ForgeConfigBackend {
    private final List<Binding> bindings = new ArrayList<>();

    private record Binding(ConfigOption<?> option, ForgeConfigSpec.ConfigValue<?> value, ForgeConfigSpec spec) {
        void copy() { option.set(value.get()); }
    }

    void register(IEventBus modBus) {
        Map<ConfigOption.File, Map<String, List<ConfigOption<?>>>> grouped = new EnumMap<>(ConfigOption.File.class);
        for (ConfigOption<?> option : ConfigOption.all()) {
            grouped.computeIfAbsent(option.file(), file -> new LinkedHashMap<>())
                    .computeIfAbsent(Objects.toString(option.section(), ""), section -> new ArrayList<>()).add(option);
        }
        grouped.forEach((file, sections) -> {
            ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
            List<Map.Entry<ConfigOption<?>, ForgeConfigSpec.ConfigValue<?>>> values = new ArrayList<>();
            sections.getOrDefault("", List.of()).forEach(option -> values.add(Map.entry(option, define(builder, option))));
            sections.forEach((section, options) -> {
                if (section.isEmpty()) return;
                builder.push(section);
                options.forEach(option -> values.add(Map.entry(option, define(builder, option))));
                builder.pop();
            });
            ForgeConfigSpec spec = builder.build();
            values.forEach(entry -> bindings.add(new Binding(entry.getKey(), entry.getValue(), spec)));
            ModLoadingContext.get().registerConfig(file.client() ? ModConfig.Type.CLIENT : ModConfig.Type.COMMON, spec, file.fileName());
        });
        modBus.addListener(this::loaded);
        modBus.addListener(this::reloaded);
    }

    private static ForgeConfigSpec.ConfigValue<?> define(ForgeConfigSpec.Builder builder, ConfigOption<?> option) {
        builder.comment(option.comment());
        if (option.worldRestart()) builder.worldRestart();
        if (option.isInteger()) return builder.defineInRange(option.key(), (Integer) option.defaultValue(), option.min(), option.max());
        return builder.define(option.key(), (boolean) (Boolean) option.defaultValue());
    }

    private void loaded(ModConfigEvent.Loading event) { copy(event.getConfig().getSpec()); }
    private void reloaded(ModConfigEvent.Reloading event) { copy(event.getConfig().getSpec()); }
    private void copy(Object spec) {
        for (Binding binding : bindings) if (binding.spec() == spec) binding.copy();
    }
}
