package com.technovision.advancedgenetics.gametest;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.io.ParsingMode;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.technovision.advancedgenetics.Config;
import com.technovision.advancedgenetics.api.genetics.Genes;
import com.technovision.advancedgenetics.fabric.FabricConfig;
import com.technovision.advancedgenetics.fabric.GeneticsHolder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.technovision.advancedgenetics.gametest.GameTestSupport.TEMPLATE;
import static com.technovision.advancedgenetics.gametest.GameTestSupport.check;
import static com.technovision.advancedgenetics.gametest.GameTestSupport.genetics;
import static com.technovision.advancedgenetics.gametest.GameTestSupport.player;

/** Fabric-only behavior: upstream Cardinal Components saves, and reloading the config file. */
public class FabricGameTests {

    @GameTest(template = TEMPLATE)
    public static void upstreamCardinalComponentsSavesLoad(GameTestHelper helper) {
        ServerPlayer upstream = player(helper, "ag_upstream");
        CompoundTag saved = upstream.saveWithoutId(new CompoundTag());
        saved.remove(GeneticsHolder.SAVE_KEY);
        saved.put("cardinal_components", components(Genes.FLIGHT));

        ServerPlayer restored = player(helper, "ag_upstream_load");
        restored.load(saved);
        check(genetics(restored).geneCount() == 1 && genetics(restored).hasGene(Genes.FLIGHT),
                "Upstream Cardinal Components genes did not load: " + genetics(restored).getGenes());
        CompoundTag resaved = restored.saveWithoutId(new CompoundTag());
        check(resaved.getCompound(GeneticsHolder.SAVE_KEY).getCompound("genes").contains(Genes.FLIGHT.toString()),
                "Loaded upstream genes were not saved under " + GeneticsHolder.SAVE_KEY);

        ServerPlayer current = player(helper, "ag_current", Genes.SPEED);
        CompoundTag both = current.saveWithoutId(new CompoundTag());
        both.put("cardinal_components", components(Genes.FLIGHT));
        ServerPlayer preferred = player(helper, "ag_current_load");
        preferred.load(both);
        check(genetics(preferred).hasGene(Genes.SPEED) && !genetics(preferred).hasGene(Genes.FLIGHT),
                "The legacy compound overrode current genetics: " + genetics(preferred).getGenes());
        helper.succeed();
    }

    /** A reload corrects and rewrites the file, then hands the values to the game thread, not before. */
    @GameTest(template = TEMPLATE)
    public static void configReloadHandsCorrectedValuesToTheGameThread(GameTestHelper helper) {
        Path file = temporaryFile();
        write(file, "[cloning_machine]\nticksPerOperation = 777\nmaxOverclock = -4\n");
        Map<Config.Value<?>, Object> previous = new LinkedHashMap<>();
        Config.sections().forEach(section -> section.values().forEach(value -> previous.put(value, value.get())));
        List<Runnable> handed = new ArrayList<>();
        try {
            check(FabricConfig.reload(file, handed::add) && handed.size() == 1, "The changed file was not handed to the game thread");
            check(Config.Common.cloningMachineTicksPerOperation.get().equals(previous.get(Config.Common.cloningMachineTicksPerOperation)),
                    "Values changed before reaching the game thread");
            String rewritten = read(file);
            FabricConfig.Corrected corrected = FabricConfig.correct(rewritten);
            check(corrected.text().equals(rewritten) && rewritten.contains("maxOverclock = 0"), "The corrected file was not written back:\n" + rewritten);
            check(!FabricConfig.reload(file, handed::add), "The reload's own rewrite triggered another reload");

            handed.get(0).run();
            check(Config.Common.cloningMachineTicksPerOperation.get() == 777 && Config.Common.cloningMachineMaxOverclock.get() == 0,
                    "The game did not get the corrected values");
            corrected.values().forEach((value, setting) -> check(setting.equals(value.get()), value.key() + " differs from the file"));

            write(file, "[cloning_machine\nticksPerOperation = 5\n");
            check(!FabricConfig.reload(file, handed::add) && handed.size() == 1, "A malformed file was applied");
            check(read(file).equals("[cloning_machine\nticksPerOperation = 5\n"), "A malformed file was overwritten");
        } finally {
            previous.forEach(Config.Value::load);
            try {
                Files.deleteIfExists(file);
            } catch (IOException ignored) {
                // A leftover temporary file is harmless.
            }
        }
        helper.succeed();
    }

    /**
     * Edits to the real config file reload through night-config's FileWatcher. Its own batch keeps the
     * temporary values away from other tests; on macOS the JDK polls for changes every 10 seconds.
     */
    @GameTest(template = TEMPLATE, batch = "config_watch", timeoutTicks = 1200)
    public static void configReloadsWhenTheFileChanges(GameTestHelper helper) {
        Path file = FabricLoader.getInstance().getConfigDir().resolve(Config.FILE_NAME);
        String original = read(file);
        Config.IntValue energy = Config.Common.cloningMachineEnergyPerTick;
        int target = energy.get() == 1234 ? 4321 : 1234;
        CommentedConfig edited = CommentedConfig.of(LinkedHashMap::new, TomlFormat.instance());
        TomlFormat.instance().createParser().parse(original, edited, ParsingMode.REPLACE);
        edited.set("cloning_machine.energyPerTick", target);
        edited.set("cloning_machine.maxOverclock", -3);
        edited.set("retired_section.key", 1);
        writeInPlace(file, TomlFormat.instance().createWriter().writeToString(edited));
        helper.succeedWhen(() -> {
            check(energy.get() == target, "The edited config has not been reloaded");
            check(Config.Common.cloningMachineMaxOverclock.get() == 0, "The reloaded maxOverclock was not clamped");
            String reloaded = read(file);
            check(reloaded.contains("maxOverclock = 0") && !reloaded.contains("retired_section"), "The reloaded file was not corrected:\n" + reloaded);
            write(file, original);
            FabricConfig.correct(original).apply();
        });
    }

    private static Path temporaryFile() {
        try {
            return Files.createTempFile("advancedgenetics", ".toml");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void write(Path file, String text) {
        try {
            Files.writeString(file, text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Overwrites the file with one write and no truncation, padding with blank lines if it would get
     * shorter. The watcher ignores a modification event that the OS reports twice, as a truncate and
     * a write can be.
     */
    private static void writeInPlace(Path file, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.allocate((int) Math.max(bytes.length, channel.size()));
            buffer.put(bytes);
            while (buffer.hasRemaining()) buffer.put((byte) '\n');
            buffer.flip();
            while (buffer.hasRemaining()) channel.write(buffer);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static CompoundTag components(Genes gene) {
        CompoundTag genes = new CompoundTag();
        genes.putString(gene.toString(), gene.getName());
        CompoundTag component = new CompoundTag();
        component.put("genes", genes);
        CompoundTag components = new CompoundTag();
        components.put(GeneticsHolder.SAVE_KEY, component);
        return components;
    }
}
