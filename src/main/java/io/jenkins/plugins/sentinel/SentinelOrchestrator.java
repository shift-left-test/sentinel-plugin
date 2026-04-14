/*
 * Copyright (c) 2026 LG Electronics, Inc. Licensed under the MIT License.
 * SPDX-License-Identifier: MIT
 */

package io.jenkins.plugins.sentinel;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.sentinel.config.SentinelConfigValidator;
import io.jenkins.plugins.sentinel.config.SentinelConfiguration;
import io.jenkins.plugins.sentinel.model.SentinelResult;
import org.jenkinsci.plugins.workflow.cps.CpsStepContext;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
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

        if (config.getPartitions() == SINGLE_PARTITION) {
            final Map<String, String> envOverrides = new EnvVars();
            envOverrides.put("SENTINEL_PARTITION_INDEX", "1");
            envOverrides.put("SENTINEL_PARTITION_TOTAL", "1");
            envOverrides.put("SENTINEL_SEED",
                    String.valueOf(config.getSeed()));

            final CpsStepContext cpsContext =
                    (CpsStepContext) getContext();
            cpsContext.newBodyInvoker()
                    .withContext(envOverrides)
                    .withCallback(new AfterPartitionCallback())
                    .start();
        } else {
            getContext().onFailure(new UnsupportedOperationException(
                    "Multi-partition execution is not yet "
                            + "implemented. Use partitions=1 or "
                            + "manual mode with sentinelRun "
                            + "+ sentinelMerge."));
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

    private void handlePostPartition() throws Exception {
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
                ? config.getSourceDir() : ".";
        final String wsPath = config.getWorkspace() != null
                ? config.getWorkspace() : ".sentinel";

        final List<String> reportArgs =
                SentinelCommandBuilder.buildReportArgs(
                        wsPath, srcDir, config.getOutputDir());
        reportArgs.add(0, sentinelCmd);
        SentinelRunner.run(reportArgs, env, ws, launcher, listener);

        final Path xmlPath = Path.of(
                ws.getRemote(),
                config.getOutputDir(),
                "mutations.xml");
        final SentinelResult sentinelResult =
                SentinelResultParser.parse(xmlPath);

        final SentinelBuildAction action =
                new SentinelBuildAction(sentinelResult);
        action.setRun(build);
        build.addAction(action);

        listener.getLogger().printf(
                "[Sentinel] Score: %.1f%% "
                        + "(killed=%d, survived=%d, "
                        + "skipped=%d)%n",
                sentinelResult.overallScore().score(),
                sentinelResult.overallScore().killed(),
                sentinelResult.overallScore().survived(),
                sentinelResult.overallScore().skipped());

        applyThreshold(listener, build, sentinelResult);
    }

    private void applyThreshold(
            final TaskListener listener,
            final Run<?, ?> build,
            final SentinelResult result) {
        if (config.getThreshold() == null
                || config.getThresholdAction() == null) {
            return;
        }
        final double score = result.overallScore().score();
        if (score < config.getThreshold()) {
            listener.getLogger().printf(
                    "[Sentinel] Score %.1f%% is below "
                            + "threshold %.1f%% -> %s%n",
                    score, config.getThreshold(),
                    config.getThresholdAction());
            switch (config.getThresholdAction()) {
                case FAILURE ->
                        build.setResult(Result.FAILURE);
                case UNSTABLE ->
                        build.setResult(Result.UNSTABLE);
                default ->
                        throw new IllegalStateException(
                                "Unexpected action: "
                                        + config.getThresholdAction());
            }
        }
    }
}
