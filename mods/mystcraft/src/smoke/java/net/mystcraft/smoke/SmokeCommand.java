package net.mystcraft.smoke;

import com.mojang.brigadier.CommandDispatcher;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Opt-in test-mod command. Never included in either production Mystcraft JAR. */
public final class SmokeCommand {
    private static final Logger LOG = LoggerFactory.getLogger("mystcraft-smoke");
    private SmokeCommand() {}

    @FunctionalInterface
    public interface ExtraChecks {
        List<String> run(MinecraftServer server, ServerLevel level) throws Exception;
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        register(dispatcher, (server, level) -> List.of());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, ExtraChecks extraChecks) {
        VisualFixtureCommands.register(dispatcher);
        WritingGuiFixture.register(dispatcher);
        GraphicalParityFixture.register(dispatcher);
        dispatcher.register(Commands.literal("mystcraft_smoke")
                .requires(source -> source.hasPermission(4))
                .executes(context -> {
                    CommandSourceStack source = context.getSource();
                    try {
                        ServerLevel level = source.getLevel();
                        if (level == null) throw new IllegalStateException("Wait for server Done before running smoke checks");
                        FullRun run = new FullRun(source, extraChecks);
                        SmokeRegionPreparation.beginPhased(source.getServer(), level,
                                run::step, failure -> failed(source, failure), run::close);
                        source.sendSuccess(Component.literal("MYSTCRAFT_SMOKE_PREPARING real entity-ticking fixture chunks"), false);
                        return 1;
                    } catch (Throwable failure) {
                        LOG.error("MYSTCRAFT_SMOKE_FAILURE", failure);
                        source.sendFailure(Component.literal("MYSTCRAFT_SMOKE_FAILURE " + failure));
                        return 0;
                    }
                }));
        registerSubset(dispatcher, "mystcraft_smoke_worlds", (server, level) -> {
            List<String> passed = new ArrayList<>();
            passed.addAll(ExplorationSmoke.run(server, level));
            passed.addAll(BoosterSmoke.run(server, level));
            passed.addAll(AgeVisualNetworkSmoke.run(server, level));
            passed.addAll(AgeSmoke.run(server, level));
            passed.addAll(TerrainSmoke.run(server, level));
            passed.addAll(DecaySmoke.run(server, level));
            passed.addAll(StarFissureSmoke.run(server, level));
            return passed;
        });
        registerSubset(dispatcher, "mystcraft_smoke_diagnose", (server, level) -> {
            var checks = new java.util.LinkedHashMap<String, ExtraChecks>();
            checks.put("ink", InkFluidSmoke::run);
            checks.put("village", VillageArchivistSmoke::run);
            checks.put("meteor", MeteorSmoke::run);
            checks.put("dynamic_pages", DynamicPagesSmoke::run);
            checks.put("admin_pvp", AdminPvpSmoke::run);
            checks.put("linking_rules", LinkingRulesSmoke::run);
            checks.put("environment", AgeEnvironmentSmoke::run);
            checks.put("generation", ClassicGenerationSmoke::run);
            checks.put("huge_trees", HugeTreeSmoke::run);
            checks.put("instability", InstabilitySmoke::run);
            checks.put("fluid_profile", InstabilityFluidSmoke::run);
            checks.put("deferred_crystals", DeferredCrystalSmoke::run);
            List<String> passed = new ArrayList<>(), failures = new ArrayList<>();
            for (var check : checks.entrySet()) {
                try {
                    var results = check.getValue().run(server, level);
                    passed.addAll(results);
                    LOG.info("MYSTCRAFT_DIAGNOSTIC_PASS {} {}", check.getKey(), results);
                } catch (Throwable failure) {
                    failures.add(check.getKey() + ": " + failure);
                    LOG.error("MYSTCRAFT_DIAGNOSTIC_FAILURE " + check.getKey(), failure);
                }
            }
            if (!failures.isEmpty()) throw new AssertionError(String.join("; ", failures));
            return passed;
        });
        registerSubset(dispatcher, "mystcraft_smoke_classic", (server, level) -> {
            List<String> passed = new ArrayList<>();
            passed.addAll(InkFluidSmoke.run(server, level));
            passed.addAll(VillageArchivistSmoke.run(server, level));
            passed.addAll(BookPresentationSmoke.run(server, level));
            passed.addAll(MeteorSmoke.run(server, level));
            passed.addAll(DynamicPagesSmoke.run(server, level));
            passed.addAll(GrammarSmoke.run(server, level));
            passed.addAll(AdminPvpSmoke.run(server, level));
            passed.addAll(LinkingRulesSmoke.run(server, level));
            passed.addAll(AgeEnvironmentSmoke.run(server, level));
            passed.addAll(ClassicGenerationSmoke.run(server, level));
            passed.addAll(HugeTreeSmoke.run(server, level));
            passed.addAll(InstabilitySmoke.run(server, level));
            passed.addAll(InstabilityFluidSmoke.run(server, level));
            passed.addAll(DeferredCrystalSmoke.run(server, level));
            return passed;
        });
        registerSubset(dispatcher, "mystcraft_smoke_parity04", (server, level) -> {
            List<String> passed = new ArrayList<>(), failures = new ArrayList<>();
            var checks = new java.util.LinkedHashMap<String, ExtraChecks>();
            checks.put("book_lifecycle", BookHealthSmoke::run);
            checks.put("lectern", LecternSmoke::run);
            checks.put("archivist", ArchivistEconomySmoke::run);
            checks.put("writing", WritingDeskSmoke::run);
            checks.put("surfaces", ClassicSpecializedSurfaceSmoke::run);
            for (var check : checks.entrySet()) {
                try {
                    var results = check.getValue().run(server, level);
                    passed.addAll(results);
                    LOG.info("MYSTCRAFT_PARITY04_PASS {} {}", check.getKey(), results);
                } catch (Throwable failure) {
                    failures.add(check.getKey() + ": " + failure);
                    LOG.error("MYSTCRAFT_PARITY04_FAILURE " + check.getKey(), failure);
                }
            }
            if (!failures.isEmpty()) throw new AssertionError(String.join("; ", failures));
            return passed;
        });
        registerSubset(dispatcher, "mystcraft_smoke_travel", LinkTravelModifierSmoke::run);
        registerSubset(dispatcher, "mystcraft_smoke_entities", (server, level) -> {
            List<String> passed = new ArrayList<>(PortalEntitySmoke.run(server, level));
            passed.addAll(extraChecks.run(server, level));
            return passed;
        });
        dispatcher.register(Commands.literal("mystcraft_symbol_audit").requires(source -> source.hasPermission(4)).executes(context -> {
            try {
                var source = context.getSource();
                StaticSymbolSmoke.begin(source.getServer(), results -> {
                    for (String result : results) source.sendSuccess(Component.literal("MYSTCRAFT_SYMBOL_AUDIT_PASS " + result), false);
                    source.sendSuccess(Component.literal("MYSTCRAFT_SYMBOL_AUDIT_COMPLETE 145 symbols, 580 seeded witnesses"), false);
                }, failure -> failed(source, failure));
                source.sendSuccess(Component.literal("MYSTCRAFT_SYMBOL_AUDIT_STARTED one catalog symbol per server tick"), false);
                return 1;
            } catch (Throwable failure) { failed(context.getSource(), failure); return 0; }
        }));
        dispatcher.register(Commands.literal("mystcraft_grammar_audit").requires(source -> source.hasPermission(4)).executes(context -> {
            try {
                var results = GrammarSmoke.audit100(context.getSource().getServer());
                for (String result : results) context.getSource().sendSuccess(Component.literal(result), false);
                return results.size();
            } catch (Throwable failure) { failed(context.getSource(), failure); return 0; }
        }));
        dispatcher.register(Commands.literal("mystcraft_age_verify")
                .requires(source -> source.hasPermission(4))
                .executes(context -> {
                    try {
                        List<String> passed = new ArrayList<>(AgeSmoke.verifyFixture(context.getSource().getServer(), context.getSource().getLevel()));
                        passed.addAll(DeferredCrystalSmoke.verifyFixture(context.getSource().getServer()));
                        for (String result : passed) context.getSource().sendSuccess(Component.literal("MYSTCRAFT_AGE_RESTART_PASS " + result), false);
                        return passed.size();
                    } catch (Throwable failure) {
                        LOG.error("MYSTCRAFT_AGE_RESTART_FAILURE", failure);
                        context.getSource().sendFailure(Component.literal("MYSTCRAFT_AGE_RESTART_FAILURE " + failure));
                        return 0;
                    }
                }));
    }

    private static void registerSubset(CommandDispatcher<CommandSourceStack> dispatcher, String name, ExtraChecks checks) {
        dispatcher.register(Commands.literal(name).requires(source -> source.hasPermission(4)).executes(context -> {
            var source = context.getSource();
            try {
                SmokeRegionPreparation.begin(source.getServer(), source.getLevel(), () -> {
                    try {
                        List<String> passed = checks.run(source.getServer(), source.getLevel());
                        for (String result : passed) source.sendSuccess(Component.literal("MYSTCRAFT_SUBSET_PASS " + name + " " + result), false);
                        source.sendSuccess(Component.literal("MYSTCRAFT_SUBSET_COMPLETE " + name + " " + passed.size()), false);
                    } catch (Throwable failure) { failed(source, failure); }
                }, failure -> failed(source, failure));
                source.sendSuccess(Component.literal("MYSTCRAFT_SUBSET_PREPARING " + name), false);
                return 1;
            } catch (Throwable failure) { failed(source, failure); return 0; }
        }));
    }

    /** Ordered scenarios retain shared fixture leases until the last real tick completes. */
    private static final class FullRun {
        private final CommandSourceStack source;
        private final List<ExtraChecks> checks;
        private final List<String> passed = new ArrayList<>();
        private int next;
        private final ExtraChecks villageMarker = VillageArchivistSmoke::run;
        private VillageArchivistSmoke.State village;
        FullRun(CommandSourceStack source, ExtraChecks extraChecks) {
            this.source = source;
            checks = List.of(
                    RecipeAndMixerSmoke::run,
                    InkFluidSmoke::run,
                    BinderSmoke::run,
                    WritingDeskSmoke::run,
                    WritingDeskStructureSmoke::run,
                    PageCollectionSmoke::run,
                    LinkModifierSmoke::run,
                    ModifierEditorSmoke::run,
                    LinkTravelModifierSmoke::run,
                    LinkingSmoke::run,
                    BookHealthSmoke::run,
                    BookPresentationSmoke::run,
                    LecternSmoke::run,
                    ArchivistEconomySmoke::run,
                    MeteorSmoke::run,
                    PortalSmoke::run,
                    PortalEntitySmoke::run,
                    ExplorationSmoke::run,
                    villageMarker,
                    BoosterSmoke::run,
                    DynamicPagesSmoke::run,
                    AgeVisualNetworkSmoke::run,
                    GrammarSmoke::run,
                    AgeSmoke::run,
                    AdminPvpSmoke::run,
                    LinkingRulesSmoke::run,
                    AgeEnvironmentSmoke::run,
                    TerrainSmoke::run,
                    ClassicGenerationSmoke::run,
                    HugeTreeSmoke::run,
                    InstabilitySmoke::run,
                    InstabilityFluidSmoke::run,
                    DeferredCrystalSmoke::run,
                    DecaySmoke::run,
                    StarFissureSmoke::run,
                    extraChecks);
        }
        void close() {
            if (village != null) { village.close(); village = null; }
        }
        boolean step() {
            try {
                if (checks.get(next) == villageMarker) {
                    if (village == null) village = VillageArchivistSmoke.begin(source.getServer(), source.getLevel());
                    if (!village.tick()) return false;
                    report(source, passed, village.passed());
                    village.close();
                    village = null;
                } else report(source, passed, checks.get(next).run(source.getServer(), source.getLevel()));
                next++;
                if (next == checks.size()) {
                    source.sendSuccess(Component.literal("MYSTCRAFT_SMOKE_COMPLETE " + passed.size()), false);
                    return true;
                }
                return false;
            } catch (Throwable failure) {
                failed(source, failure);
                return true;
            }
        }
    }

    private static void report(CommandSourceStack source, List<String> total, List<String> results) {
        total.addAll(results);
        for (String result : results) source.sendSuccess(Component.literal("MYSTCRAFT_SMOKE_PASS " + result), false);
    }

    private static void failed(CommandSourceStack source, Throwable failure) {
        LOG.error("MYSTCRAFT_SMOKE_FAILURE", failure);
        source.sendFailure(Component.literal("MYSTCRAFT_SMOKE_FAILURE " + failure));
    }
}
