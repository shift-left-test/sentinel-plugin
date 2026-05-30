/*
 * Copyright (c) 2026 LG Electronics, Inc. Licensed under the MIT License.
 * SPDX-License-Identifier: MIT
 */

package io.jenkins.plugins.sentinel;

import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;

/**
 * Base execution for sentinel steps that launch an external sentinel
 * process. Holds the running {@link SentinelProcHandle} and forcibly
 * kills the process on abort so cancellation reliably terminates
 * sentinel (and its child build/test processes), not just the worker
 * thread that {@code SynchronousNonBlockingStepExecution} interrupts.
 *
 * @param <T> the step's return type
 */
abstract class SentinelStepExecution<T>
        extends SynchronousNonBlockingStepExecution<T> {

    private static final long serialVersionUID = 1L;

    /**
     * Tracks the currently running sentinel process. Transient because
     * {@link hudson.Proc} is not serializable; not restored on
     * deserialization, which is fine because a synchronous step does not
     * resume across a controller restart (onResume fails).
     */
    protected final transient SentinelProcHandle procHandle =
            new SentinelProcHandle();

    protected SentinelStepExecution(final StepContext context) {
        super(context);
    }

    @Override
    public void stop(final Throwable cause) throws Exception {
        // procHandle is transient; null only on a deserialized execution,
        // which never resumes (onResume fails) anyway.
        if (procHandle != null) {
            procHandle.killQuietly();
        }
        super.stop(cause);
    }
}
