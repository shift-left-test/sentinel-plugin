/*
 * Copyright (c) 2026 LG Electronics, Inc. Licensed under the MIT License.
 * SPDX-License-Identifier: MIT
 */

package io.jenkins.plugins.sentinel;

import java.io.Serializable;
import java.util.List;
import java.util.Set;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.sentinel.config.ThresholdAction;
import org.jenkinsci.plugins.workflow.flow.StashManager;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Pipeline step that collects partition results, merges them,
 * generates reports, and applies threshold judgment.
 *
 * <p>Reads SENTINEL_PARTITION_TOTAL, SENTINEL_SOURCE_DIR,
 * SENTINEL_OUTPUT_DIR, SENTINEL_PATH from environment variables.
 * Step parameters override environment variables.</p>
 *
 * <p>Usage in Jenkinsfile:</p>
 * <pre>
 * sentinelReport(threshold: 80.0, thresholdAction: 'UNSTABLE')
 * </pre>
 */

public class SentinelReportStep extends Step implements Serializable {

    private static final long serialVersionUID = 1L;

    private Double threshold;
    private String thresholdAction;
    private String sourceDir;
    private String outputDir;
    private String sentinelPath;

    /**
     * Unstashes single (non-partitioned) results into the
     * workspace subdirectory.
     *
     * @param build   current build
     * @param ws      workspace root
     * @param launcher launcher
     * @param env     environment variables
     * @param listener build listener
     * @throws Exception if unstash fails
     */
    static void unstashSingle(
            final Run<?, ?> build,
            final FilePath ws,
            final Launcher launcher,
            final EnvVars env,
            final TaskListener listener) throws Exception {
        final String envWs =
                env.get(SentinelEnvironment.WORKSPACE);
        final String workspace =
                envWs != null && !envWs.isEmpty()
                        ? envWs
                        : SentinelEnvironment
                                .DEFAULT_SINGLE_WORKSPACE;
        listener.getLogger().printf(
                "[Sentinel] Unstashing %s%n",
                SentinelEnvironment.SINGLE_STASH_NAME);
        StashManager.unstash(build,
                SentinelEnvironment.SINGLE_STASH_NAME,
                ws.child(workspace),
                launcher, env, listener);
    }

    /**
     * Unstashes all partition results into their respective
     * partition subdirectories.
     *
     * @param build   current build
     * @param ws      workspace root
     * @param launcher launcher
     * @param env     environment variables
     * @param listener build listener
     * @param total   total number of partitions
     * @throws Exception if unstash fails
     */
    static void unstashPartitions(
            final Run<?, ?> build,
            final FilePath ws,
            final Launcher launcher,
            final EnvVars env,
            final TaskListener listener,
            final int total) throws Exception {
        for (int i = 1; i <= total; i++) {
            final String name =
                    SentinelEnvironment.stashName(i);
            listener.getLogger().printf(
                    "[Sentinel] Unstashing %s%n", name);
            StashManager.unstash(build, name,
                    ws.child(SentinelEnvironment
                            .partitionWorkspace(i)),
                    launcher, env, listener);
        }
    }

    /**
     * Creates a new SentinelReportStep with no required parameters.
     */
    @DataBoundConstructor
    public SentinelReportStep() {
        super();
    }

    /**
     * Returns the threshold percentage.
     *
     * @return threshold
     */
    public Double getThreshold() {
        return threshold;
    }

    /**
     * Returns the threshold action name.
     *
     * @return threshold action name
     */
    public String getThresholdAction() {
        return thresholdAction;
    }

    /**
     * Returns the source directory.
     *
     * @return source directory
     */
    public String getSourceDir() {
        return sourceDir;
    }

    /**
     * Returns the output directory.
     *
     * @return output directory
     */
    public String getOutputDir() {
        return outputDir;
    }

    /**
     * Returns the sentinel executable path.
     *
     * @return sentinel path
     */
    public String getSentinelPath() {
        return sentinelPath;
    }

    /**
     * Sets the threshold percentage.
     *
     * @param v threshold
     */
    @DataBoundSetter
    public void setThreshold(final Double v) {
        threshold = v;
    }

    /**
     * Sets the threshold action name.
     *
     * @param v threshold action name
     */
    @DataBoundSetter
    public void setThresholdAction(final String v) {
        thresholdAction = v;
    }

    /**
     * Sets the source directory.
     *
     * @param v source directory
     */
    @DataBoundSetter
    public void setSourceDir(final String v) {
        sourceDir = v;
    }

    /**
     * Sets the output directory.
     *
     * @param v output directory
     */
    @DataBoundSetter
    public void setOutputDir(final String v) {
        outputDir = v;
    }

    /**
     * Sets the sentinel executable path.
     *
     * @param v sentinel path
     */
    @DataBoundSetter
    public void setSentinelPath(final String v) {
        sentinelPath = v;
    }

    @Override
    public StepExecution start(final StepContext context) {
        return new SentinelReportExecution(context, this);
    }

    private static class SentinelReportExecution
            extends SynchronousNonBlockingStepExecution<Void> {

        private static final long serialVersionUID = 1L;
        private final SentinelReportStep step;

        SentinelReportExecution(final StepContext context,
                                final SentinelReportStep stepRef) {
            super(context);
            this.step = stepRef;
        }

        @Override
        protected Void run() throws Exception {
            final TaskListener listener = getContext()
                    .get(TaskListener.class);
            final FilePath ws = getContext().get(FilePath.class);
            final Launcher launcher = getContext()
                    .get(Launcher.class);
            final EnvVars env = getContext().get(EnvVars.class);
            final Run<?, ?> build = getContext().get(Run.class);

            final String sentinelCmd = resolveSentinelPath(env);
            final int partitionTotal = resolvePartitionTotal(env);
            final String reportWorkspace;

            if (partitionTotal > 0) {
                unstashPartitions(build, ws, launcher, env,
                        listener, partitionTotal);
                mergePartitions(sentinelCmd, ws, launcher,
                        listener, env, partitionTotal);
                reportWorkspace =
                        SentinelEnvironment.MERGED_WORKSPACE;
            } else {
                unstashSingle(build, ws, launcher, env, listener);
                reportWorkspace = resolveWorkspace(env);
            }

            final String srcDir = resolveSourceDir(env);
            final String outDir = resolveOutputDir(env);
            final ThresholdAction action =
                    step.thresholdAction != null
                            ? ThresholdAction.fromString(
                            step.thresholdAction)
                            : null;

            SentinelPostProcessor.reportAndJudge(
                    sentinelCmd, reportWorkspace, srcDir,
                    outDir, step.threshold, action,
                    env, ws, launcher, listener, build);

            return null;
        }

        private int resolvePartitionTotal(final EnvVars env) {
            final String envTotal = env.get(
                    SentinelEnvironment.PARTITION_TOTAL);
            if (envTotal != null && !envTotal.isEmpty()) {
                return Integer.parseInt(envTotal);
            }
            return 0;
        }

        private void mergePartitions(
                final String sentinelCmd,
                final FilePath ws,
                final Launcher launcher,
                final TaskListener listener,
                final EnvVars env,
                final int total) throws Exception {
            final List<String> paths =
                    SentinelPostProcessor.partitionPaths(total);
            SentinelPostProcessor.merge(
                    sentinelCmd, paths,
                    SentinelEnvironment.MERGED_WORKSPACE,
                    env, ws, launcher, listener);
        }

        private String resolveSourceDir(final EnvVars env) {
            return resolve(step.sourceDir,
                    env.get(SentinelEnvironment.SOURCE_DIR),
                    SentinelEnvironment.DEFAULT_SOURCE_DIR);
        }

        private String resolveOutputDir(final EnvVars env) {
            return resolve(step.outputDir,
                    env.get(SentinelEnvironment.OUTPUT_DIR),
                    SentinelEnvironment.DEFAULT_OUTPUT_DIR);
        }

        private String resolveSentinelPath(final EnvVars env) {
            final String resolved = resolve(step.sentinelPath,
                    env.get(SentinelEnvironment.PATH), null);
            return SentinelGlobalConfiguration
                    .getEffectivePath(resolved);
        }

        private String resolveWorkspace(final EnvVars env) {
            return resolve(null,
                    env.get(SentinelEnvironment.WORKSPACE),
                    SentinelEnvironment.DEFAULT_SINGLE_WORKSPACE);
        }

        private static String resolve(
                final String stepValue,
                final String envValue,
                final String defaultValue) {
            if (stepValue != null) {
                return stepValue;
            }
            if (envValue != null && !envValue.isEmpty()) {
                return envValue;
            }
            return defaultValue;
        }
    }

    /**
     * Descriptor for the sentinelReport pipeline step.
     */
    @Extension
    public static class DescriptorImpl extends StepDescriptor {

        /**
         * Fills the thresholdAction dropdown.
         *
         * @return list of threshold action options
         */
        public ListBoxModel doFillThresholdActionItems() {
            final ListBoxModel items = new ListBoxModel();
            items.add("", "");
            for (final ThresholdAction action
                    : ThresholdAction.values()) {
                items.add(action.name(), action.name());
            }
            return items;
        }

        @Override
        public String getFunctionName() {
            return "sentinelReport";
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return "Sentinel mutation testing report";
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Set.of(
                    FilePath.class, Launcher.class,
                    TaskListener.class, EnvVars.class,
                    Run.class);
        }
    }
}
