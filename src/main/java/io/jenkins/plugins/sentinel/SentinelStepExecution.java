/*
 * Copyright (c) 2026 LG Electronics, Inc. Licensed under the MIT License.
 * SPDX-License-Identifier: MIT
 */

package io.jenkins.plugins.sentinel;

import java.util.Set;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
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
     * Context types both steps require.
     *
     * <p>Declared once because it must match exactly what {@link #inputs()}
     * pulls out of the context: a type missing here but read there fails
     * the step at runtime instead of at configuration time. Both
     * descriptors return this from {@code getRequiredContext()}.</p>
     */
    static final Set<Class<?>> REQUIRED_CONTEXT = Set.of(
            FilePath.class, Launcher.class, TaskListener.class,
            EnvVars.class, Run.class);

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

    /**
     * The pipeline context a sentinel step runs against.
     *
     * <p>A local value passed down through one {@code run()} call, never a
     * field, so it is outside CPS serialization.</p>
     *
     * @param ws       the node's workspace
     * @param launcher launcher for the node the step runs on
     * @param listener build log destination
     * @param env      environment variables visible to the step
     * @param build    the running build
     */
    protected record Inputs(FilePath ws, Launcher launcher,
                            TaskListener listener, EnvVars env,
                            Run<?, ?> build) {
    }

    /**
     * Extracts the required context and reports unrecognized
     * {@code SENTINEL_*} variables.
     *
     * @return the step's inputs
     * @throws Exception if the context cannot be read
     */
    protected Inputs inputs() throws Exception {
        final StepContext context = getContext();
        final Inputs in = new Inputs(
                context.get(FilePath.class),
                context.get(Launcher.class),
                context.get(TaskListener.class),
                context.get(EnvVars.class),
                context.get(Run.class));
        SentinelEnvironment.warnUnknownVariables(
                in.env(), in.listener().getLogger());
        return in;
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
