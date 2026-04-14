/*
 * Copyright (c) 2026 LG Electronics, Inc. Licensed under the MIT License.
 * SPDX-License-Identifier: MIT
 */

package io.jenkins.plugins.sentinel;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
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
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Pipeline step that merges partition results and generates reports.
 *
 * <p>Usage in Jenkinsfile:</p>
 * <pre>
 * sentinelMerge(
 *     partitions: ['.sentinel-1', '.sentinel-2'],
 *     workspace: '.sentinel-merged',
 *     sourceDir: '.',
 *     outputDir: 'sentinel-report',
 *     threshold: 80.0,
 *     thresholdAction: 'UNSTABLE'
 * )
 * </pre>
 */

public class SentinelMergeStep extends Step implements Serializable {

    private static final long serialVersionUID = 1L;

    private final List<String> partitions;
    private String workspace;
    private String sourceDir;
    private String outputDir;
    private Double threshold;
    private String thresholdAction;
    private String sentinelPath;

    /**
     * Creates a new SentinelMergeStep with the given partition paths.
     *
     * @param partitions list of partition workspace paths to merge
     */
    @DataBoundConstructor
    public SentinelMergeStep(final List<String> partitions) {
        super();
        this.partitions = List.copyOf(partitions);
    }

    /**
     * Returns the partition workspace paths.
     *
     * @return partition paths
     */
    public List<String> getPartitions() {
        return partitions;
    }

    /**
     * Returns the target workspace.
     *
     * @return workspace
     */
    public String getWorkspace() {
        return workspace;
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
     * Returns the sentinel path.
     *
     * @return sentinel path
     */
    public String getSentinelPath() {
        return sentinelPath;
    }

    /**
     * Sets the target workspace.
     *
     * @param v workspace
     */
    @DataBoundSetter
    public void setWorkspace(final String v) {
        workspace = v;
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
     * Sets the sentinel path.
     *
     * @param v sentinel path
     */
    @DataBoundSetter
    public void setSentinelPath(final String v) {
        sentinelPath = v;
    }

    @Override
    public StepExecution start(final StepContext context) {
        return new SentinelMergeExecution(context, this);
    }

    private static class SentinelMergeExecution
            extends SynchronousNonBlockingStepExecution<Void> {

        private static final long serialVersionUID = 1L;
        private final SentinelMergeStep step;

        SentinelMergeExecution(final StepContext context,
                               final SentinelMergeStep step) {
            super(context);
            this.step = step;
        }

        @Override
        protected Void run() throws Exception {
            final TaskListener listener = getContext()
                    .get(TaskListener.class);
            final FilePath ws = getContext().get(FilePath.class);
            final Launcher launcher = getContext()
                    .get(Launcher.class);
            final Map<String, String> env =
                    getContext().get(EnvVars.class);
            final Run<?, ?> build = getContext().get(Run.class);

            final String sentinelCmd = SentinelGlobalConfiguration
                    .getEffectivePath(step.sentinelPath);
            final String targetWs = step.workspace != null
                    ? step.workspace
                    : SentinelPostProcessor.MERGED_WORKSPACE;

            SentinelPostProcessor.merge(
                    sentinelCmd, step.partitions, targetWs,
                    env, ws, launcher, listener);

            if (step.outputDir != null) {
                final String srcDir = step.sourceDir != null
                        ? step.sourceDir
                        : SentinelPostProcessor.DEFAULT_SOURCE_DIR;
                final ThresholdAction action = step.thresholdAction != null
                        ? ThresholdAction.fromString(step.thresholdAction)
                        : null;
                SentinelPostProcessor.reportAndJudge(
                        sentinelCmd, targetWs, srcDir,
                        step.outputDir, step.threshold, action,
                        env, ws, launcher, listener, build);
            }

            return null;
        }
    }

    /**
     * Descriptor for the sentinelMerge pipeline step.
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
            for (final ThresholdAction action : ThresholdAction.values()) {
                items.add(action.name(), action.name());
            }
            return items;
        }

        @Override
        public String getFunctionName() {
            return "sentinelMerge";
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return "Merge partition results";
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
