/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import ports.extremereactors.platform.Platform;

/** Small general-purpose helpers shared by the multiblock library and the game content. */
public final class CodeHelper {

    public static final Direction[] DIRECTIONS = Direction.values();
    public static final Object[] EMPTY_GENERIC_ARRAY = new Object[0];
    public static final AABB EMPTY_AABB = new AABB(0, 0, 0, 0, 0, 0);
    public static final Component TEXT_EMPTY_LINE = Component.literal("");

    private CodeHelper() {}

    //region environment

    public static boolean isDevEnv() {
        return Platform.isDevelopmentEnvironment();
    }

    public static Optional<MinecraftServer> getMinecraftServer() {
        return Optional.ofNullable(Platform.currentServer());
    }

    /** The ID of the mod whose initialization code is currently running, or an empty string. */
    public static String getModIdFromActiveModContainer() {
        return Platform.activeModId();
    }

    /** Creates {@code config/<name>} if needed; false if it cannot be created. */
    public static boolean ioCreateModConfigDirectory(final String name) {
        try {
            java.nio.file.Files.createDirectories(Platform.configDirectory().resolve(name));
            return true;
        } catch (java.io.IOException | RuntimeException e) {
            return false;
        }
    }

    /** Runs {@code listener} after each client resource reload. Client only. */
    public static void addResourceReloadListener(final Runnable listener) {
        Platform.addClientReloadListener(listener);
    }

    /** Hides the multiblock validation error currently shown to the local player, if any. Client only. */
    public static void clearErrorReport() {
        ErrorReport.clear();
    }

    //endregion
    //region logical sides

    public static String getWorldSideName(final Level world) {
        return world.isClientSide() ? "CLIENT" : "SERVER";
    }

    public static boolean calledByLogicalServer(final Level world) {
        return !world.isClientSide();
    }

    public static boolean calledByLogicalClient(final Level world) {
        return world.isClientSide();
    }

    public static LogicalSide getWorldLogicalSide(final Level world) {
        return world.isClientSide() ? LogicalSide.CLIENT : LogicalSide.SERVER;
    }

    public static void callOnLogicalSide(final Level world, final Runnable serverCode, final Runnable clientCode) {
        if (world.isClientSide()) {
            clientCode.run();
        } else {
            serverCode.run();
        }
    }

    public static <T> T callOnLogicalSide(final Level world, final Supplier<T> serverCode, final Supplier<T> clientCode) {
        return world.isClientSide() ? clientCode.get() : serverCode.get();
    }

    public static boolean callOnLogicalSide(final Level world, final BooleanSupplier serverCode, final BooleanSupplier clientCode) {
        return world.isClientSide() ? clientCode.getAsBoolean() : serverCode.getAsBoolean();
    }

    public static int callOnLogicalSide(final Level world, final IntSupplier serverCode, final IntSupplier clientCode) {
        return world.isClientSide() ? clientCode.getAsInt() : serverCode.getAsInt();
    }

    public static long callOnLogicalSide(final Level world, final LongSupplier serverCode, final LongSupplier clientCode) {
        return world.isClientSide() ? clientCode.getAsLong() : serverCode.getAsLong();
    }

    public static double callOnLogicalSide(final Level world, final DoubleSupplier serverCode, final DoubleSupplier clientCode) {
        return world.isClientSide() ? clientCode.getAsDouble() : serverCode.getAsDouble();
    }

    public static void callOnLogicalServer(final Level world, final Runnable code) {
        if (!world.isClientSide()) {
            code.run();
        }
    }

    public static <T> T callOnLogicalServer(final Level world, final Supplier<T> code) {
        return world.isClientSide() ? null : code.get();
    }

    public static <T> T callOnLogicalServer(final Level world, final Supplier<T> code, final Supplier<T> invalidSideReturnValue) {
        return world.isClientSide() ? invalidSideReturnValue.get() : code.get();
    }

    public static void callOnLogicalClient(final Level world, final Runnable code) {
        if (world.isClientSide()) {
            code.run();
        }
    }

    public static <T> T callOnLogicalClient(final Level world, final Supplier<T> code) {
        return world.isClientSide() ? code.get() : null;
    }

    public static <T> T callOnLogicalClient(final Level world, final Supplier<T> code, final Supplier<T> invalidSideReturnValue) {
        return world.isClientSide() ? code.get() : invalidSideReturnValue.get();
    }

    //endregion
    //region Optional helpers

    public static <A, B> void optionalIfPresent(final Optional<A> first, final Optional<B> second, final BiConsumer<A, B> consumer) {
        if (first.isPresent() && second.isPresent()) {
            consumer.accept(first.get(), second.get());
        }
    }

    public static <T> void optionalIfPresentOrElse(final Optional<T> optional, final Consumer<? super T> action, final Runnable emptyAction) {
        optional.ifPresentOrElse(action, emptyAction);
    }

    /** Runs {@code action} with the value, failing loudly if the value is missing. */
    public static <T> void optionalIfPresentOrThrow(final Optional<T> optional, final Consumer<? super T> action) {
        action.accept(optional.orElseThrow());
    }

    public static <A, B, R> Optional<R> optionalMap(final Optional<A> first, final Optional<B> second,
                                                    final BiFunction<? super A, ? super B, ? extends R> mapper) {
        return first.isPresent() && second.isPresent() ? Optional.ofNullable(mapper.apply(first.get(), second.get())) : Optional.empty();
    }

    public static <A, B, R> Optional<R> optionalFlatMap(final Optional<A> first, final Optional<B> second,
                                                        final BiFunction<? super A, ? super B, Optional<R>> mapper) {
        return first.isPresent() && second.isPresent() ? mapper.apply(first.get(), second.get()) : Optional.empty();
    }

    public static <T> Optional<T> optionalOr(final Optional<T> optional, final Supplier<Optional<T>> alternative) {
        return optional.isPresent() ? optional : alternative.get();
    }

    //endregion
    //region text

    public static String neutralLowercase(final String text) {
        return text.toLowerCase(Locale.ROOT);
    }

    private static final String[] MAGNITUDE_PREFIXES = {"", "K", "M", "G", "T", "P", "E", "Z", "Y"};

    /**
     * Formats a quantity with a metric prefix and up to two decimals, e.g. 12.5 KFE.
     * Values below one thousand are shown as-is.
     */
    public static String formatAsHumanReadableNumber(final double value, final String unit) {

        double scaled = Math.abs(value);
        int magnitude = 0;

        while (scaled >= 1000.0 && magnitude < MAGNITUDE_PREFIXES.length - 1) {
            scaled /= 1000.0;
            ++magnitude;
        }

        final DecimalFormat format = new DecimalFormat(0 == magnitude ? "#,##0.##" : "#,##0.00",
                DecimalFormatSymbols.getInstance(Locale.ROOT));

        return (value < 0 ? "-" : "") + format.format(scaled) + " " + MAGNITUDE_PREFIXES[magnitude] + unit;
    }

    public static ResourceLocation getObjectId(final Fluid fluid) {
        return Registry.FLUID.getKey(fluid);
    }

    //endregion
    //region math and directions

    /** The number of blocks in the cuboid between two inclusive corners. */
    public static int mathVolume(final BlockPos min, final BlockPos max) {
        return (Math.abs(max.getX() - min.getX()) + 1) * (Math.abs(max.getY() - min.getY()) + 1) * (Math.abs(max.getZ() - min.getZ()) + 1);
    }

    /** The four directions perpendicular to {@code direction}, in {@link Direction} order. */
    public static Stream<Direction> directionStream() {
        return Arrays.stream(DIRECTIONS);
    }

    public static List<Direction> perpendicularDirections(final Direction direction) {

        final List<Direction> result = new java.util.ArrayList<>(4);

        for (final Direction candidate : DIRECTIONS) {
            if (candidate.getAxis() != direction.getAxis()) {
                result.add(candidate);
            }
        }

        return Collections.unmodifiableList(result);
    }

    /** The plane perpendicular to {@code axis}: horizontal for Y, vertical otherwise. */
    public static Direction.Plane perpendicularPlane(final Direction.Axis axis) {
        return Direction.Axis.Y == axis ? Direction.Plane.HORIZONTAL : Direction.Plane.VERTICAL;
    }

    /** Rotates {@code direction} by 90 degrees clockwise around {@code axis}; directions on the axis are unchanged. */
    public static Direction directionRotateAround(final Direction direction, final Direction.Axis axis) {
        return switch (axis) {
            case X -> direction.getAxis() == Direction.Axis.X ? direction : direction.getClockWise(Direction.Axis.X);
            case Y -> direction.getAxis() == Direction.Axis.Y ? direction : direction.getClockWise(Direction.Axis.Y);
            case Z -> direction.getAxis() == Direction.Axis.Z ? direction : direction.getClockWise(Direction.Axis.Z);
        };
    }

    //endregion
    //region timing

    /** A supplier that returns true once every {@code ticks} calls, starting with the {@code ticks}th call. */
    public static BooleanSupplier tickCountdown(final int ticks) {

        final int[] remaining = {ticks};

        return () -> {

            if (--remaining[0] <= 0) {

                remaining[0] = ticks;
                return true;
            }

            return false;
        };
    }

    /** A runnable that runs {@code code} on every {@code ticks}th call. */
    public static Runnable delayedRunnable(final Runnable code, final int ticks) {

        final BooleanSupplier countdown = tickCountdown(ticks);

        return () -> {

            if (countdown.getAsBoolean()) {
                code.run();
            }
        };
    }

    //endregion
    //region error report

    /** The multiblock validation error shown to the local player; cleared when the machine changes. */
    public static final class ErrorReport {

        @Nullable
        private static Component s_message;
        @Nullable
        private static BlockPos s_position;

        private ErrorReport() {}

        public static void set(final Component message, @Nullable final BlockPos position) {
            s_message = message;
            s_position = position;
        }

        public static void clear() {
            s_message = null;
            s_position = null;
        }

        public static Optional<Component> message() {
            return Optional.ofNullable(s_message);
        }

        public static Optional<BlockPos> position() {
            return Optional.ofNullable(s_position);
        }
    }

    //endregion
}
