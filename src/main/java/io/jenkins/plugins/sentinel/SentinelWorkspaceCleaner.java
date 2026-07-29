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

    /**
     * Deletes and recreates a directory the plugin owns.
     *
     * <p>The {@code label} names the directory's role in the build log.
     * This deletes a directory inside the user's workspace, so the log
     * line has to say which one and why, not just print a path.</p>
     *
     * @param dir      directory to recreate
     * @param listener build listener for the log line
     * @param label    human-readable role of the directory
     * @throws Exception if the directory cannot be recreated
     */
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

    /**
     * Recreates {@code managedDir} under {@code base}, or does nothing when
     * it is null.
     *
     * <p>A null {@code managedDir} means the user chose the directory
     * themselves (see
     * {@link SentinelEnvironment#managedDefault(String, String, String)}),
     * and the plugin never deletes a directory it did not assign.</p>
     *
     * @param base       workspace root the directory sits under
     * @param managedDir plugin-assigned directory name, or null
     * @param listener   build listener for the log line
     * @param label      human-readable role of the directory
     * @throws Exception if the directory cannot be recreated
     */
    static void recreateIfManaged(
            final FilePath base,
            final String managedDir,
            final TaskListener listener,
            final String label) throws Exception {
        if (managedDir != null) {
            recreateDirectory(base.child(managedDir), listener, label);
        }
    }
}
