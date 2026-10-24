/*
 * ProjectE 1.19.2 port - Fabric implementation of the Forge API shapes ProjectE uses.
 * Copyright (c) 2026 ProjectE 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.fml;

import java.util.Map;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.jetbrains.annotations.Nullable;
import ports.projecte.lib.forgespi.language.IModInfo;

/**
 * Forge's update checker. The port never contacts an update URL: every result is {@link Status#UP_TO_DATE}.
 */
public final class VersionChecker {

    private VersionChecker() {
    }

    public static CheckResult getResult(IModInfo mod) {
        return new CheckResult(Status.UP_TO_DATE, null, Map.of(), null);
    }

    public enum Status {
        PENDING, FAILED, UP_TO_DATE, OUTDATED, AHEAD, BETA, BETA_OUTDATED
    }

    public record CheckResult(Status status, @Nullable ComparableVersion target, Map<ComparableVersion, String> changes, @Nullable String url) {
    }
}
