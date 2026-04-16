/*
 * Copyright (c) 2026 LG Electronics, Inc. Licensed under the MIT License.
 * SPDX-License-Identifier: MIT
 */

package io.jenkins.plugins.sentinel;

import hudson.FilePath;
import hudson.model.TaskListener;

/**
 * Recreates plugin-managed workspace directories to avoid stale results.
 */
final class SentinelWorkspaceCleaner {

    private SentinelWorkspaceCleaner() {
    }

    static void recreateDirectory(
            final FilePath dir,
            final TaskListener listener,
            final String label) throws Exception {
        listener.getLogger().printf(
                "[Sentinel] Preparing %s: %s%n",
                label, dir.getRemote());
        dir.deleteRecursive();
        dir.mkdirs();
    }
}
