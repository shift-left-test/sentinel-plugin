/*
 * Copyright (c) 2026 LG Electronics, Inc. Licensed under the MIT License.
 * SPDX-License-Identifier: MIT
 */

package io.jenkins.plugins.sentinel;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.sentinel.config.SentinelConfigValidator;
import io.jenkins.plugins.sentinel.config.SentinelConfiguration;
import org.jenkinsci.plugins.workflow.cps.CpsStepContext;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.EnvironmentExpander;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

/**
 * Orchestrates the sentinelPipeline step execution:
 *
 * <ol>
 *   <li>Validate configuration</li>
 *   <li>Generate shared seed if not provided</li>
 *   <li>Execute user closure on partition node</li>
 *   <li>Run sentinel report generation</li>
 *   <li>Parse mutations.xml and attach SentinelBuildAction</li>
 *   <li>Apply threshold judgment</li>
 * </ol>
 *
 * <p>Uses CPS body execution to run the user-provided closure.</p>
 */

public class SentinelOrchestrator extends StepExecution {

    private static final long serialVersionUID = 1L;
    private static final int SINGLE_PARTITION = 1;

    private final SentinelConfiguration config;
    private final AtomicInteger completedCount =
            new AtomicInteger(0);
    private final AtomicReference<Throwable> firstFailure =
            new AtomicReference<>();

    /**
     * Creates a new SentinelOrchestrator.
     *
     * @param context the step context
     * @param step    the pipeline step
     */
    public SentinelOrchestrator(
            final StepContext context,
            final SentinelPipelineStep step) {
        super(context);
        this.config = step.toConfiguration();
    }

    @Override
    @SuppressWarnings("FutureReturnValueIgnored")
    public boolean start() throws Exception {
        SentinelConfigValidator.validate(config);

        final TaskListener listener = getContext()
                .get(TaskListener.class);
        listener.getLogger().println(
                "[Sentinel] Starting distributed mutation test "
                        + "with " + config.getPartitions()
                        + " partition(s)");

        if (config.getSeed() == null) {
            config.setSeed(ThreadLocalRandom.current().nextLong()
                    & Long.MAX_VALUE);
        }

        listener.getLogger().println(
                "[Sentinel] Seed: " + config.getSeed());

        final CpsStepContext cpsContext =
                (CpsStepContext) getContext();
        final EnvironmentExpander parentExpander =
                getContext().get(EnvironmentExpander.class);
        final int total = config.getPartitions();

        if (total == SINGLE_PARTITION) {
            final EnvironmentExpander partEnv =
                    EnvironmentExpander.merge(
                            parentExpander,
                            partitionEnv(1, total));
            cpsContext.newBodyInvoker()
                    .withContext(partEnv)
                    .withCallback(new AfterPartitionCallback())
                    .start();
        } else {
            for (int i = 1; i <= total; i++) {
                final EnvironmentExpander partEnv =
                        EnvironmentExpander.merge(
                                parentExpander,
                                partitionEnv(i, total));
                cpsContext.newBodyInvoker()
                        .withContext(partEnv)
                        .withCallback(
                                new MultiPartitionCallback(total))
                        .start();
            }
        }

        return false;
    }

    @Override
    public void stop(final Throwable cause) throws Exception {
        // Allow interruption
    }

    private class AfterPartitionCallback
            extends BodyExecutionCallback {

        private static final long serialVersionUID = 1L;

        @Override
        public void onSuccess(
                final StepContext context, final Object result) {
            try {
                handlePostPartition();
                getContext().onSuccess(null);
            } catch (Exception e) {
                getContext().onFailure(e);
            }
        }

        @Override
        public void onFailure(final StepContext context,
                              final Throwable t) {
            getContext().onFailure(t);
        }
    }

    private EnvironmentExpander partitionEnv(
            final int index, final int total) {
        return EnvironmentExpander.constant(Map.of(
                SentinelPostProcessor.ENV_PARTITION_INDEX,
                String.valueOf(index),
                SentinelPostProcessor.ENV_PARTITION_TOTAL,
                String.valueOf(total),
                SentinelPostProcessor.ENV_SEED,
                String.valueOf(config.getSeed())));
    }

    private void handlePostPartition() throws Exception {
        postProcess(false);
    }

    private class MultiPartitionCallback
            extends BodyExecutionCallback {

        private static final long serialVersionUID = 1L;

        private final int total;

        MultiPartitionCallback(final int total) {
            super();
            this.total = total;
        }

        @Override
        public void onSuccess(
                final StepContext context,
                final Object result) {
            if (completedCount.incrementAndGet() == total) {
                onAllComplete();
            }
        }

        @Override
        public void onFailure(final StepContext context,
                              final Throwable t) {
            firstFailure.compareAndSet(null, t);
            if (completedCount.incrementAndGet() == total) {
                onAllComplete();
            }
        }

        private void onAllComplete() {
            final Throwable failure = firstFailure.get();
            if (failure != null) {
                getContext().onFailure(failure);
                return;
            }
            try {
                handleMultiPartitionPostProcessing();
                getContext().onSuccess(null);
            } catch (Exception e) {
                getContext().onFailure(e);
            }
        }
    }

    private void handleMultiPartitionPostProcessing()
            throws Exception {
        postProcess(true);
    }

    private void postProcess(final boolean mergePartitions)
            throws Exception {
        if (config.getOutputDir() == null) {
            return;
        }

        final TaskListener listener = getContext()
                .get(TaskListener.class);
        final FilePath ws = getContext().get(FilePath.class);
        final Launcher launcher = getContext().get(Launcher.class);
        final Map<String, String> env =
                getContext().get(EnvVars.class);
        final Run<?, ?> build = getContext().get(Run.class);

        final String sentinelCmd = SentinelGlobalConfiguration
                .getEffectivePath(config.getSentinelPath());
        final String srcDir = config.getSourceDir() != null
                ? config.getSourceDir()
                : SentinelPostProcessor.DEFAULT_SOURCE_DIR;

        final String reportWs;
        if (mergePartitions) {
            final List<String> partitionPaths =
                    SentinelPostProcessor.partitionPaths(
                            config.getPartitions());
            listener.getLogger().println(
                    "[Sentinel] All partitions complete. "
                            + "Merging results...");
            SentinelPostProcessor.merge(
                    sentinelCmd, partitionPaths,
                    SentinelPostProcessor.MERGED_WORKSPACE,
                    env, ws, launcher, listener);
            reportWs = SentinelPostProcessor.MERGED_WORKSPACE;
        } else {
            reportWs = config.getWorkspace() != null
                    ? config.getWorkspace() : ".sentinel";
        }

        SentinelPostProcessor.reportAndJudge(
                sentinelCmd, reportWs, srcDir,
                config.getOutputDir(),
                config.getThreshold(),
                config.getThresholdAction(),
                env, ws, launcher, listener, build);
    }
}
