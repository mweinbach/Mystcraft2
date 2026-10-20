/*
 * Extreme Reactors 1.19.2 port - tests
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.test;

import org.junit.jupiter.api.Test;
import ports.extremereactors.effects.ReactorAlarm;
import ports.extremereactors.effects.RodDrive;
import ports.extremereactors.effects.ScramDetector;
import ports.extremereactors.effects.StatusSyncTracker;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The client-side effects logic: rod drive motion, SCRAM recognition, alarms, and the server's rate-limited
 * status sync.
 */
class EffectsLogicTest {

    @Test
    void rodDriveStrokesAtFixedSpeed() {

        final RodDrive drive = new RodDrive(0f);

        drive.tick(1f, false);
        assertEquals(RodDrive.STROKE_PER_TICK, drive.get(), 1e-6);
        assertEquals(RodDrive.STROKE_PER_TICK / 2, drive.get(0.5f), 1e-6);

        for (int i = 0; i < 30; ++i) {
            drive.tick(1f, false);
        }

        assertEquals(1f, drive.get(), 1e-6);
        drive.tick(1f, false);
        assertFalse(drive.isMoving());

        drive.tick(0.98f, false);
        assertEquals(0.98f, drive.get(), 1e-6);
    }

    @Test
    void rodDriveDropsFasterThanItStrokesAndSettles() {

        final RodDrive dropping = new RodDrive(0f);
        final RodDrive stroking = new RodDrive(0f);
        int ticksToDrop = 0;

        while (dropping.get() < 1f) {

            dropping.tick(1f, true);
            stroking.tick(1f, false);
            ++ticksToDrop;
        }

        assertTrue(ticksToDrop < 1f / RodDrive.STROKE_PER_TICK, "a SCRAM drop beats a normal stroke");
        assertTrue(stroking.get() < 1f);

        float lowest = 1f;

        for (int i = 0; i < 60; ++i) {

            dropping.tick(1f, true);
            lowest = Math.min(lowest, dropping.get());
            assertTrue(dropping.get() <= 1f, "a bouncing rod never goes past full insertion");
        }

        assertTrue(lowest < 1f, "the rod bounces off the bottom");
        assertEquals(1f, dropping.get(), 1e-6);
        assertFalse(dropping.isMoving());
    }

    @Test
    void scramNeedsShutdownAndFullInsertionTogether() {

        final ScramDetector detector = new ScramDetector();

        assertFalse(detector.update(true, false), "the first update only learns the state");
        assertFalse(detector.update(false, false), "shutting down alone is not a SCRAM");

        for (int i = 0; i < ScramDetector.WINDOW + 5; ++i) {
            assertFalse(detector.update(false, false));
        }

        assertFalse(detector.update(false, true), "inserting the rods long after the shutdown is not a SCRAM");

        final ScramDetector scram = new ScramDetector();

        scram.update(true, false);
        assertFalse(scram.update(true, true), "inserting the rods alone is not a SCRAM");
        assertFalse(scram.update(true, false));
        assertFalse(scram.update(false, false));
        assertTrue(scram.update(false, true), "shutdown then full insertion within the window");
        assertFalse(scram.update(false, true), "a SCRAM is reported once");
    }

    @Test
    void scramIsRecognizedWhicheverUpdateArrivesFirst() {

        final ScramDetector rodsFirst = new ScramDetector();

        rodsFirst.update(true, false);
        assertFalse(rodsFirst.update(true, true));
        assertTrue(rodsFirst.update(false, true));

        final ScramDetector sameTick = new ScramDetector();

        sameTick.update(true, false);
        assertTrue(sameTick.update(false, true));

        final ScramDetector alreadyInserted = new ScramDetector();

        alreadyInserted.update(true, true);
        assertFalse(alreadyInserted.update(false, true), "turning off a reactor whose rods are all in");
    }

    @Test
    void alarmsFollowTheReadings() {

        assertEquals(EnumSet.noneOf(ReactorAlarm.class), alarms(true, true, 500, 0.8, 1000, 0, 4000));
        assertEquals(EnumSet.of(ReactorAlarm.OVERHEAT), alarms(true, false, 2500, 0, 1000, 0, 4000));
        assertEquals(EnumSet.of(ReactorAlarm.OVERHEAT), alarms(false, true, 2000, 0, 1000, 0, 4000),
                "a hot reactor alarms even when off");
        assertEquals(EnumSet.of(ReactorAlarm.COOLANT_LOW), alarms(true, true, 500, 0.05, 1000, 0, 4000));
        assertEquals(EnumSet.noneOf(ReactorAlarm.class), alarms(false, true, 20, 0, 1000, 0, 4000),
                "an idle reactor needs no coolant");
        assertEquals(EnumSet.noneOf(ReactorAlarm.class), alarms(true, false, 500, 0, 1000, 0, 4000),
                "a passively-cooled reactor has no coolant tank");
        assertEquals(EnumSet.of(ReactorAlarm.NO_FUEL), alarms(true, false, 20, 0, 0, 0, 4000));
        assertEquals(EnumSet.of(ReactorAlarm.WASTE_FULL), alarms(true, false, 20, 0, 300, 3600, 4000));

        assertTrue(ReactorAlarm.anyCritical(EnumSet.of(ReactorAlarm.NO_FUEL, ReactorAlarm.COOLANT_LOW)));
        assertFalse(ReactorAlarm.anyCritical(EnumSet.of(ReactorAlarm.NO_FUEL, ReactorAlarm.WASTE_FULL)));
    }

    @Test
    void statusSyncFiresOnNoticeableChangesAtMostEveryInterval() {

        final StatusSyncTracker tracker = new StatusSyncTracker();

        assertTrue(tracker.shouldSync(20, 1, 0), "the first check sends the state");

        int sent = 0;

        for (int tick = 0; tick < 200; ++tick) {
            sent += tracker.shouldSync(20 + tick * 0.01, 1, 0) ? 1 : 0;
        }

        assertEquals(0, sent, "a steady reactor sends nothing");

        for (int tick = 0; tick < StatusSyncTracker.MIN_INTERVAL * 10; ++tick) {
            sent += tracker.shouldSync(20 + tick * 10, 1, 0) ? 1 : 0;
        }

        assertTrue(sent > 0 && sent <= 10, "a heating reactor sends at most once per interval: " + sent);

        final StatusSyncTracker coolant = new StatusSyncTracker();

        coolant.shouldSync(500, 1, 0);

        for (int tick = 0; tick < StatusSyncTracker.MIN_INTERVAL; ++tick) {
            assertFalse(coolant.shouldSync(500, 1, 0));
        }

        assertTrue(coolant.shouldSync(500, 0.9, 0), "a coolant drop is sent");
    }

    private static Set<ReactorAlarm> alarms(final boolean active, final boolean activelyCooled,
                                            final double temperature, final double coolant,
                                            final int fuel, final int waste, final int capacity) {
        return ReactorAlarm.evaluate(new ReactorAlarm.Readings(active, activelyCooled, temperature, coolant, fuel,
                waste, capacity), 2000, 0.1);
    }
}
