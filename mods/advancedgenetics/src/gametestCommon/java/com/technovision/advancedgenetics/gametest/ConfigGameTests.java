package com.technovision.advancedgenetics.gametest;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.technovision.advancedgenetics.Config;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

import java.util.List;
import java.util.Objects;

import static com.technovision.advancedgenetics.gametest.GameTestSupport.TEMPLATE;
import static com.technovision.advancedgenetics.gametest.GameTestSupport.check;
import static com.technovision.advancedgenetics.gametest.GameTestSupport.platform;

/**
 * The same expectations against each loader's config corrections: ForgeConfigSpec itself on Forge,
 * and FabricConfig's reimplementation of it on Fabric.
 */
public class ConfigGameTests {

    @GameTest(template = TEMPLATE)
    public static void configCorrectionFillsInAnEmptyFile(GameTestHelper helper) {
        String text = platform().correctConfig("");
        CommentedConfig config = parse(text);
        for (Config.Section section : Config.sections()) {
            check(String.join("\n", section.comment()).equals(config.getComment(section.name())),
                    "Wrong comment on [" + section.name() + "]: " + config.getComment(section.name()));
            for (Config.Value<?> value : section.values()) {
                expect(config, value, value.defaultValue(), value.defaultValue());
            }
        }
        check("The number of seconds that an overclock item speeds up a machine.\nDefault: 2 seconds faster\nRange: > 0"
                .equals(config.getComment("general_settings.overclockSpeed")), "Wrong comment on overclockSpeed");
        check("Percent chance to receive a basic gene upon DNA decryption.\nDefault: 50% chance\nRange: 0.0 ~ 1.0"
                .equals(config.getComment("general_settings.basicGeneChance")), "Wrong comment on basicGeneChance");
        check(config.getComment("genes.flight") == null, "Genes have no comment");
        check(platform().correctConfig(text).equals(text), "Correcting a corrected file changed it");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void configCorrectionClampsOutOfRangeNumbers(GameTestHelper helper) {
        CommentedConfig config = parse(platform().correctConfig("""
                [general_settings]
                plasmidRequirement = 500
                basicGeneChance = -0.5
                overclockSpeed = -1
                overclockEnergy = 9999999999
                [cell_analyzer]
                ticksPerOperation = 5
                successRate = inf
                maxOverclock = 3.7
                """));
        expect(config, Config.Common.plasmidRequirement, 100, 100);
        expect(config, Config.Common.basicGeneChance, 0.0, 0.0);
        expect(config, Config.Common.overclockSpeed, 0, 0);
        expect(config, Config.Common.overclockEnergy, Integer.MAX_VALUE, Integer.MAX_VALUE);
        expect(config, Config.Common.cellAnalyzerTicksPerOperation, 20, 20);
        expect(config, Config.Common.cellAnalyzerSuccessRate, 1.0, 1.0);
        // In range, so kept as written; the game reads it the way ForgeConfigSpec's int getter does.
        expect(config, Config.Common.cellAnalyzerMaxOverclock, 3.7, 3);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void configCorrectionResetsWrongTypes(GameTestHelper helper) {
        CommentedConfig config = parse(platform().correctConfig("""
                genes = 5
                [general_settings]
                hardMode = 3
                geneSharing = "TRUE"
                plasmidRequirement = "lots"
                basicGeneChance = true
                [cell_analyzer]
                successRate = nan
                """));
        expect(config, Config.Common.hardMode, false, false);
        // ForgeConfigSpec accepts the strings "true" and "false" in any case for booleans.
        expect(config, Config.Common.geneSharing, "TRUE", true);
        expect(config, Config.Common.plasmidRequirement, 24, 24);
        expect(config, Config.Common.basicGeneChance, 0.5, 0.5);
        for (Config.BoolValue gene : Config.Common.genes.values()) expect(config, gene, true, true);
        Object nan = config.get("cell_analyzer.successRate");
        check(nan instanceof Double number && number.isNaN(), "ForgeConfigSpec keeps nan in the file: " + nan);
        check(Config.Common.cellAnalyzerSuccessRate.convert(nan) == 1.0, "nan did not read as the default");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void configCorrectionDropsUnknownKeysAndRewritesComments(GameTestHelper helper) {
        String text = platform().correctConfig("""
                #A stale section comment
                [general_settings]
                #My own note
                hardMode = true
                retired = 1
                [retired_section]
                key = 2
                """);
        CommentedConfig config = parse(text);
        expect(config, Config.Common.hardMode, true, true);
        check(!config.contains("general_settings.retired") && !config.contains("retired_section"), "Unknown keys were kept:\n" + text);
        check("Increase difficulty by making plasmids no longer accept basic genes.\nDefault: false"
                .equals(config.getComment("general_settings.hardMode")), "The hardMode comment was not restored:\n" + text);
        check("General Settings".equals(config.getComment("general_settings")), "The section comment was not restored:\n" + text);
        check(!text.contains("My own note") && !text.contains("stale"), "Comments on known keys were kept:\n" + text);
        helper.succeed();
    }

    private static CommentedConfig parse(String text) {
        return TomlFormat.instance().createParser().parse(text);
    }

    /** Checks what the corrected file holds for {@code value}, and what the game reads from it. */
    private static void expect(CommentedConfig config, Config.Value<?> value, Object stored, Object setting) {
        Object actual = config.get(List.of(section(value), value.key()));
        check(Objects.equals(actual, stored), value.key() + " was corrected to " + actual + " instead of " + stored);
        check(Objects.equals(value.convert(actual), setting), value.key() + " reads as " + value.convert(actual) + " instead of " + setting);
    }

    private static String section(Config.Value<?> value) {
        return Config.sections().stream().filter(section -> section.values().contains(value)).findFirst()
                .orElseThrow().name();
    }
}
