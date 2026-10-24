/*
 * Mekanism 1.19.2 port - in-world tests shared by the Forge and Fabric builds.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a GameTest, or every GameTest in a class, that checks how the level itself loads and ticks chunks: chunk
 * tickets, forced chunks, chunk saves. {@link SharedGameTests} leaves such tests to the level alone and does not tick
 * their block entities through {@link GameTestTicking#keepTestAreaTicking}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface LevelTickingOnly {
}
