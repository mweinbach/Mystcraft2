package com.technovision.advancedgenetics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loader-neutral description of {@code config/AdvancedGenetics.toml}. Each loader binds these
 * values to the same file; gameplay only reads them. Values hold their defaults until loaded.
 */
public final class Config {

    public static final String FILE_NAME = "AdvancedGenetics.toml";

    private static final List<Section> SECTIONS = new ArrayList<>();

    private Config() { }

    /** Every section in file order. */
    public static List<Section> sections() {
        Common.init();
        return Collections.unmodifiableList(SECTIONS);
    }

    public static final class Section {
        private final String name;
        private final List<String> comment;
        private final List<Value<?>> values = new ArrayList<>();

        private Section(String name, String... comment) {
            this.name = name;
            this.comment = List.of(comment);
            SECTIONS.add(this);
        }

        public String name() { return name; }
        public List<String> comment() { return comment; }
        public List<Value<?>> values() { return Collections.unmodifiableList(values); }

        private BoolValue bool(String key, boolean defaultValue, String... comment) {
            return add(new BoolValue(key, defaultValue, comment));
        }

        private IntValue range(String key, int defaultValue, int min, int max, String... comment) {
            return add(new IntValue(key, defaultValue, min, max, comment));
        }

        private DoubleValue range(String key, double defaultValue, double min, double max, String... comment) {
            return add(new DoubleValue(key, defaultValue, min, max, comment));
        }

        private <V extends Value<?>> V add(V value) {
            values.add(value);
            return value;
        }
    }

    public abstract static sealed class Value<T> permits BoolValue, IntValue, DoubleValue {
        private final String key;
        private final List<String> comment;
        private final T defaultValue;
        private volatile T value;

        private Value(String key, T defaultValue, String... comment) {
            this.key = key;
            this.comment = List.of(comment);
            this.defaultValue = defaultValue;
            this.value = defaultValue;
        }

        public String key() { return key; }
        public List<String> comment() { return comment; }
        public T defaultValue() { return defaultValue; }
        public T get() { return value; }

        public void set(T value) {
            if (!isValid(value)) throw new IllegalArgumentException(key + " = " + value + " is out of range");
            this.value = value;
        }

        /** Sets this value from what the file holds after ForgeConfigSpec's corrections. */
        public void load(Object stored) {
            set(convert(stored));
        }

        /**
         * Reads a corrected file value the way ForgeConfigSpec's getters do: numbers are narrowed,
         * and the {@code "true"}/{@code "false"} strings its boolean check accepts are parsed.
         */
        public abstract T convert(Object stored);

        public abstract boolean isValid(T value);
    }

    public static final class BoolValue extends Value<Boolean> {
        private BoolValue(String key, boolean defaultValue, String... comment) { super(key, defaultValue, comment); }
        @Override public Boolean convert(Object stored) { return stored instanceof String text ? Boolean.parseBoolean(text) : (Boolean) stored; }
        @Override public boolean isValid(Boolean value) { return value != null; }
    }

    public static final class IntValue extends Value<Integer> {
        private final int min;
        private final int max;
        private IntValue(String key, int defaultValue, int min, int max, String... comment) {
            super(key, defaultValue, comment);
            this.min = min;
            this.max = max;
        }
        public int min() { return min; }
        public int max() { return max; }
        @Override public Integer convert(Object stored) { return ((Number) stored).intValue(); }
        @Override public boolean isValid(Integer value) { return value != null && value >= min && value <= max; }
    }

    public static final class DoubleValue extends Value<Double> {
        private final double min;
        private final double max;
        private DoubleValue(String key, double defaultValue, double min, double max, String... comment) {
            super(key, defaultValue, comment);
            this.min = min;
            this.max = max;
        }
        public double min() { return min; }
        public double max() { return max; }
        /** ForgeConfigSpec's range check lets {@code nan} through; it means the default. */
        @Override public Double convert(Object stored) {
            double number = ((Number) stored).doubleValue();
            return Double.isNaN(number) ? defaultValue() : number;
        }
        @Override public boolean isValid(Double value) { return value != null && value >= min && value <= max; }
    }

    public static final class Common {

        private static final Section GENERAL = new Section("general_settings", "General Settings");

        public static final BoolValue hardMode = GENERAL.bool("hardMode", false,
                "Increase difficulty by making plasmids no longer accept basic genes.", "Default: false");
        public static final BoolValue geneSharing = GENERAL.bool("geneSharing", false,
                "Allows players to take the blood of other players and get all of their genes from it.", "Default: false");
        public static final DoubleValue basicGeneChance = GENERAL.range("basicGeneChance", 0.50, 0.00, 1.00,
                "Percent chance to receive a basic gene upon DNA decryption.", "Default: 50% chance");
        public static final IntValue plasmidRequirement = GENERAL.range("plasmidRequirement", 24, 2, 100,
                "The number of decrypted DNA helices needed to fill a plasmid.", "Default: 24 DNA helices");
        public static final IntValue overclockSpeed = GENERAL.range("overclockSpeed", 2, 0, Integer.MAX_VALUE,
                "The number of seconds that an overclock item speeds up a machine.", "Default: 2 seconds faster");
        public static final IntValue overclockEnergy = GENERAL.range("overclockEnergy", 5, 0, Integer.MAX_VALUE,
                "The amount of extra energy per tick that an overclock item uses.", "Default: 5 E/t");

        private static final Section GENES = new Section("genes", "Genes", "Set any gene to 'false' to disable it");

        public static final Map<String, BoolValue> genes = new HashMap<>();

        static {
            for (String gene : List.of("beelicious", "climb_walls", "dragons_breath", "dragons_health", "eat_grass",
                    "emerald_heart", "explosive_exit", "fireproof", "flight", "haste", "infinity", "invisibility",
                    "jump_boost", "keep_inventory", "lay_egg", "luck", "meaty", "milky", "mob_sight", "night_vision",
                    "no_fall_damage", "no_hunger", "poison_immunity", "regeneration", "resistance", "scare_creepers",
                    "scare_skeletons", "shoot_fireballs", "slimy", "speed", "strength", "teleport", "venom",
                    "water_breathing", "web_walking", "wither_resistance", "wither_hit", "wooly")) {
                genes.put(gene, GENES.bool(gene, true));
            }
        }

        private static final Section CELL_ANALYZER = new Section("cell_analyzer", "Cell Analyzer");

        public static final IntValue cellAnalyzerEnergyCapacity = CELL_ANALYZER.range("energyCapacity", 20000, 0, Integer.MAX_VALUE,
                "Maximum energy capacity for the Cell Analyzer.", "Default: 20,000 (20k E)");
        public static final IntValue cellAnalyzerEnergyPerTick = CELL_ANALYZER.range("energyPerTick", 20, 0, Integer.MAX_VALUE,
                "Energy consumed per tick when the Cell Analyzer is processing.", "Default: 20 E");
        public static final IntValue cellAnalyzerTicksPerOperation = CELL_ANALYZER.range("ticksPerOperation", 400, 20, Integer.MAX_VALUE,
                "Ticks per operation when using the Cell Analyzer.", "Default: 400 ticks");
        public static final IntValue cellAnalyzerMaxOverclock = CELL_ANALYZER.range("maxOverclock", 10, 0, Integer.MAX_VALUE,
                "The max amount of overclock items that can be used on the Cell Analyzer.", "Default: 10x overclock");
        public static final DoubleValue cellAnalyzerSuccessRate = CELL_ANALYZER.range("successRate", 1.00, 0.00, 1.00,
                "Percent chance for the Cell Analyzer to successfully create a cell.", "Default: 100% success rate");

        private static final Section DNA_EXTRACTOR = new Section("dna_extractor", "DNA Extractor");

        public static final IntValue dnaExtractorEnergyCapacity = DNA_EXTRACTOR.range("energyCapacity", 20000, 0, Integer.MAX_VALUE,
                "Maximum energy capacity for the DNA Extractor.", "Default: 20,000 (20k E)");
        public static final IntValue dnaExtractorEnergyPerTick = DNA_EXTRACTOR.range("energyPerTick", 20, 0, Integer.MAX_VALUE,
                "Energy consumed per tick when the DNA Extractor is processing.", "Default: 20 E");
        public static final IntValue dnaExtractorTicksPerOperation = DNA_EXTRACTOR.range("ticksPerOperation", 200, 20, Integer.MAX_VALUE,
                "Ticks per operation when using the DNA Extractor.", "Default: 200 ticks");
        public static final IntValue dnaExtractorMaxOverclock = DNA_EXTRACTOR.range("maxOverclock", 5, 0, Integer.MAX_VALUE,
                "The max amount of overclock items that can be used on the DNA Extractor.", "Default: 5x overclock");
        public static final DoubleValue dnaExtractorSuccessRate = DNA_EXTRACTOR.range("successRate", 1.00, 0.00, 1.00,
                "Percent chance for the DNA Extractor to successfully extract a DNA helix.", "Default: 100% success rate");

        private static final Section DNA_DECRYPTER = new Section("dna_decrypter", "DNA Decrypter");

        public static final IntValue dnaDecrypterEnergyCapacity = DNA_DECRYPTER.range("energyCapacity", 20000, 0, Integer.MAX_VALUE,
                "Maximum energy capacity for the DNA Decrypter.", "Default: 20,000 (20k E)");
        public static final IntValue dnaDecrypterEnergyPerTick = DNA_DECRYPTER.range("energyPerTick", 20, 0, Integer.MAX_VALUE,
                "Energy consumed per tick when the DNA Decrypter is processing.", "Default: 20 E");
        public static final IntValue dnaDecrypterTicksPerOperation = DNA_DECRYPTER.range("ticksPerOperation", 200, 20, Integer.MAX_VALUE,
                "Ticks per operation when using the DNA Decrypter.", "Default: 200 ticks");
        public static final IntValue dnaDecrypterMaxOverclock = DNA_DECRYPTER.range("maxOverclock", 5, 0, Integer.MAX_VALUE,
                "The max amount of overclock items that can be used on the DNA Decrypter.", "Default: 5x overclock");
        public static final DoubleValue dnaDecrypterSuccessRate = DNA_DECRYPTER.range("successRate", 1.00, 0.00, 1.00,
                "Percent chance for the DNA Decrypter to successfully decode a DNA helix.", "Default: 100% success rate");

        private static final Section PLASMID_INFUSER = new Section("plasmid_infuser", "Plasmid Infuser");

        public static final IntValue plasmidInfuserEnergyCapacity = PLASMID_INFUSER.range("energyCapacity", 20000, 0, Integer.MAX_VALUE,
                "Maximum energy capacity for the Plasmid Infuser.", "Default: 20,000 (20k E)");
        public static final IntValue plasmidInfuserEnergyPerTick = PLASMID_INFUSER.range("energyPerTick", 20, 0, Integer.MAX_VALUE,
                "Energy consumed per tick when the Plasmid Infuser is processing.", "Default: 20 E");
        public static final IntValue plasmidInfuserTicksPerOperation = PLASMID_INFUSER.range("ticksPerOperation", 400, 20, Integer.MAX_VALUE,
                "Ticks per operation when using the Plasmid Infuser.", "Default: 400 ticks");
        public static final IntValue plasmidInfuserMaxOverclock = PLASMID_INFUSER.range("maxOverclock", 10, 0, Integer.MAX_VALUE,
                "The max amount of overclock items that can be used on the Plasmid Infuser.", "Default: 10x overclock");
        public static final DoubleValue plasmidInfuserSuccessRate = PLASMID_INFUSER.range("successRate", 1.00, 0.00, 1.00,
                "Percent chance for the Plasmid Infuser to successfully add DNA helix to plasmid.", "Default: 100% success rate");

        private static final Section BLOOD_PURIFIER = new Section("blood_purifier", "Blood Purifier");

        public static final IntValue bloodPurifierEnergyCapacity = BLOOD_PURIFIER.range("energyCapacity", 20000, 0, Integer.MAX_VALUE,
                "Maximum energy capacity for the Blood Purifier.", "Default: 20,000 (20k E)");
        public static final IntValue bloodPurifierEnergyPerTick = BLOOD_PURIFIER.range("energyPerTick", 20, 0, Integer.MAX_VALUE,
                "Energy consumed per tick when the Blood Purifier is processing.", "Default: 20 E");
        public static final IntValue bloodPurifierTicksPerOperation = BLOOD_PURIFIER.range("ticksPerOperation", 200, 20, Integer.MAX_VALUE,
                "Ticks per operation when using the Blood Purifier.", "Default: 200 ticks");
        public static final IntValue bloodPurifierMaxOverclock = BLOOD_PURIFIER.range("maxOverclock", 5, 0, Integer.MAX_VALUE,
                "The max amount of overclock items that can be used on the Blood Purifier.", "Default: 5x overclock");
        public static final DoubleValue bloodPurifierSuccessRate = BLOOD_PURIFIER.range("successRate", 1.00, 0.00, 1.00,
                "Percent chance for the Blood Purifier to successfully purify blood.", "Default: 100% success rate");

        private static final Section PLASMID_INJECTOR = new Section("plasmid_injector", "Plasmid Injector");

        public static final IntValue plasmidInjectorEnergyCapacity = PLASMID_INJECTOR.range("energyCapacity", 20000, 0, Integer.MAX_VALUE,
                "Maximum energy capacity for the Plasmid Injector.", "Default: 20,000 (20k E)");
        public static final IntValue plasmidInjectorEnergyPerTick = PLASMID_INJECTOR.range("energyPerTick", 20, 0, Integer.MAX_VALUE,
                "Energy consumed per tick when the Plasmid Injector is processing.", "Default: 20 E");
        public static final IntValue plasmidInjectorTicksPerOperation = PLASMID_INJECTOR.range("ticksPerOperation", 400, 20, Integer.MAX_VALUE,
                "Ticks per operation when using the Plasmid Injector.", "Default: 400 ticks");
        public static final IntValue plasmidInjectorMaxOverclock = PLASMID_INJECTOR.range("maxOverclock", 10, 0, Integer.MAX_VALUE,
                "The max amount of overclock items that can be used on the Plasmid Injector.", "Default: 10x overclock");
        public static final DoubleValue plasmidInjectorSuccessRate = PLASMID_INJECTOR.range("successRate", 1.00, 0.00, 1.00,
                "Percent chance for the Plasmid Injector to successfully inject a tplasmid into the syringe.", "Default: 100% success rate");

        private static final Section CLONING_MACHINE = new Section("cloning_machine", "Cloning Machine");

        public static final IntValue cloningMachineEnergyCapacity = CLONING_MACHINE.range("energyCapacity", 500000, 0, Integer.MAX_VALUE,
                "Maximum energy capacity for the Cloning Machine.", "Default: 500,000 (500k E)");
        public static final IntValue cloningMachineEnergyPerTick = CLONING_MACHINE.range("energyPerTick", 1000, 0, Integer.MAX_VALUE,
                "Energy consumed per tick when the Cloning Machine is processing.", "Default: 1000 E");
        public static final IntValue cloningMachineTicksPerOperation = CLONING_MACHINE.range("ticksPerOperation", 600, 20, Integer.MAX_VALUE,
                "Ticks per operation when using the Cloning Machine.", "Default: 600 ticks");
        public static final IntValue cloningMachineMaxOverclock = CLONING_MACHINE.range("maxOverclock", 5, 0, Integer.MAX_VALUE,
                "The max amount of overclock items that can be used on the Cloning Machine.", "Default: 5x overclock");
        public static final DoubleValue cloningMachineSuccessRate = CLONING_MACHINE.range("successRate", 1.00, 0.00, 1.00,
                "Percent chance for the cloning machine to successfully spawn an entity.", "Default: 100% success rate");

        private Common() { }

        private static void init() { }
    }
}
