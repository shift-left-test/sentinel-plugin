/*
 * Copyright (c) 2026 LG Electronics, Inc. Licensed under the MIT License.
 * SPDX-License-Identifier: MIT
 */

package io.jenkins.plugins.sentinel;

import java.util.logging.Level;
import java.util.logging.Logger;

import hudson.Proc;

/**
 * Holds the currently running sentinel {@link Proc} so a step's
 * {@code stop()} can forcibly kill it on abort.
 *
 * <p>{@link org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution}
 * cancels a step only by interrupting its worker thread. That unwinds a
 * live {@code Proc.join()} over a healthy channel, but the launched
 * process (and its child build/test processes) may survive. Killing the
 * {@link Proc} directly makes abort deterministic and reaps the process
 * tree via Jenkins' ProcessTreeKiller.</p>
 */
final class SentinelProcHandle {

    private static final Logger LOGGER =
            Logger.getLogger(SentinelProcHandle.class.getName());

    private volatile Proc currentProc;

    /**
     * Registers the process that is now running.
     *
     * @param proc the started process (nullable)
     */
    void set(final Proc proc) {
        this.currentProc = proc;
    }

    /**
     * Clears the registered process after it has completed.
     */
    void clear() {
        this.currentProc = null;
    }

    /**
     * Best-effort kills the registered process. Never throws; intended
     * to be called from {@code stop()} on build abort.
     */
    void killQuietly() {
        final Proc proc = currentProc;
        if (proc == null) {
            return;
        }
        try {
            proc.kill();
        } catch (Exception e) {
            LOGGER.log(Level.FINE,
                    "Failed to kill sentinel process on abort", e);
        }
    }
}
