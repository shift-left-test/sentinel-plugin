/*
 * Copyright (c) 2026 LG Electronics, Inc. Licensed under the MIT License.
 * SPDX-License-Identifier: MIT
 */

package io.jenkins.plugins.sentinel;

import java.io.IOException;
import java.io.Serializable;
import java.util.Map;
import java.util.Set;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.sentinel.config.SentinelConfigValidator;
import io.jenkins.plugins.sentinel.config.SentinelConfiguration;
import io.jenkins.plugins.sentinel.config.ThresholdAction;
import org.jenkinsci.plugins.workflow.flow.StashManager;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
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

    /** Partition total meaning "this run was not partitioned". */
    private static final int UNPARTITIONED = 0;

    private Double threshold;
    private String thresholdAction;
    private String sourceDir;
    private String outputDir;
    private String sentinelPath;
    private Integer partitionTotal;

    /**
     * Creates a new SentinelReportStep with no required parameters.
     */
    @DataBoundConstructor
    public SentinelReportStep() {
        super();
    }

    public Double getThreshold() {
        return threshold;
    }

    public String getThresholdAction() {
        return thresholdAction;
    }

    public String getSourceDir() {
        return sourceDir;
    }

    public String getOutputDir() {
        return outputDir;
    }

    public String getSentinelPath() {
        return sentinelPath;
    }

    public Integer getPartitionTotal() {
        return partitionTotal;
    }

    @DataBoundSetter
    public void setThreshold(final Double v) {
        threshold = v;
    }

    @DataBoundSetter
    public void setThresholdAction(final String v) {
        thresholdAction = v;
    }

    @DataBoundSetter
    public void setSourceDir(final String v) {
        sourceDir = v;
    }

    @DataBoundSetter
    public void setOutputDir(final String v) {
        outputDir = v;
    }

    @DataBoundSetter
    public void setSentinelPath(final String v) {
        sentinelPath = v;
    }

    @DataBoundSetter
    public void setPartitionTotal(final Integer v) {
        partitionTotal = v;
    }

    /**
     * Builds the merged configuration: environment variables first, then
     * any step parameter that was given.
     *
     * <p>The report step goes through the same
     * {@link SentinelConfiguration} as {@code sentinelRun} so that
     * {@link SentinelConfigValidator} actually runs against its
     * parameters - notably the threshold range and the
     * threshold/thresholdAction pairing, which are otherwise never
     * checked.</p>
     *
     * @param env environment variables
     * @return populated SentinelConfiguration
     * @throws IllegalArgumentException if {@code thresholdAction} is not a
     *                                  valid action
     */
    SentinelConfiguration toConfiguration(final Map<String, String> env) {
        final SentinelConfiguration config =
                SentinelEnvironment.toConfiguration(env);
        SentinelEnvironment.override(sourceDir, config::setSourceDir);
        SentinelEnvironment.override(outputDir, config::setOutputDir);
        SentinelEnvironment.override(sentinelPath, config::setSentinelPath);
        SentinelEnvironment.override(
                partitionTotal, config::setPartitionTotal);
        SentinelEnvironment.override(threshold, config::setThreshold);
        if (SentinelEnvironment.isSet(thresholdAction)) {
            config.setThresholdAction(
                    ThresholdAction.fromString(thresholdAction));
        }
        return config;
    }

    /**
     * Returns how many partitions to collect.
     *
     * @param config validated configuration
     * @return the partition total, or 0 when the run was not partitioned
     */
    static int partitionCount(final SentinelConfiguration config) {
        final Integer total = config.getPartitionTotal();
        return total != null ? total : UNPARTITIONED;
    }

    String managedOutputDirForCleanup(final Map<String, String> env) {
        return SentinelEnvironment.managedDefault(
                outputDir,
                env.get(SentinelEnvironment.OUTPUT_DIR),
                SentinelEnvironment.DEFAULT_OUTPUT_DIR);
    }

    void prepareManagedOutputDir(
            final FilePath ws,
            final Map<String, String> env,
            final TaskListener listener) throws Exception {
        SentinelWorkspaceCleaner.recreateIfManaged(
                ws, managedOutputDirForCleanup(env),
                listener, "report output directory");
    }

    /**
     * Returns the single-mode unstash directory when the plugin owns it.
     *
     * <p>The report step has no {@code workspace} parameter, so only
     * {@code SENTINEL_WORKSPACE} can move this directory.</p>
     *
     * @param env environment variables
     * @return the managed directory, or null when the user chose it
     */
    static String managedSingleWorkspaceForCleanup(
            final Map<String, String> env) {
        return SentinelEnvironment.managedDefault(
                null,
                env.get(SentinelEnvironment.WORKSPACE),
                SentinelEnvironment.DEFAULT_SINGLE_WORKSPACE);
    }

    /**
     * Unstashes single (non-partitioned) results into the
     * workspace subdirectory.
     *
     * @param build    current build
     * @param ws       workspace root
     * @param launcher launcher
     * @param env      environment variables
     * @param listener build listener
     * @param config   validated configuration
     * @throws Exception if unstash fails
     */
    static void unstashSingle(
            final Run<?, ?> build,
            final FilePath ws,
            final Launcher launcher,
            final EnvVars env,
            final TaskListener listener,
            final SentinelConfiguration config) throws Exception {
        SentinelWorkspaceCleaner.recreateIfManaged(
                ws, managedSingleWorkspaceForCleanup(env),
                listener, "single unstash directory");
        final String target =
                SentinelEnvironment.effectiveSingleWorkspace(config);
        listener.getLogger().printf(
                "[Sentinel] Unstashing %s into %s%n",
                SentinelEnvironment.SINGLE_STASH_NAME, target);
        StashManager.unstash(build,
                SentinelEnvironment.SINGLE_STASH_NAME,
                ws.child(target),
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
            final FilePath target = ws.child(
                    SentinelEnvironment.partitionWorkspace(i));
            SentinelWorkspaceCleaner.recreateDirectory(
                    target,
                    listener,
                    "partition unstash directory");
            listener.getLogger().printf(
                    "[Sentinel] Unstashing %s%n", name);
            try {
                StashManager.unstash(build, name,
                        target,
                        launcher, env, listener);
            } catch (final IOException e) {
                final AbortException error = new AbortException(
                        "Sentinel partition " + i + " of " + total
                                + " could not be collected (stash '" + name
                                + "' is missing or unreadable). Ensure every"
                                + " parallel branch runs sentinelRun with a"
                                + " matching partitionTotal=" + total + ".");
                error.initCause(e);
                throw error;
            }
        }
    }

    @Override
    public StepExecution start(final StepContext context) {
        return new SentinelReportExecution(context, this);
    }

    private static class SentinelReportExecution
            extends SentinelStepExecution<Void> {

        private static final long serialVersionUID = 1L;
        private final SentinelReportStep step;

        SentinelReportExecution(final StepContext context,
                                final SentinelReportStep stepRef) {
            super(context);
            this.step = stepRef;
        }

        @Override
        protected Void run() throws Exception {
            final Inputs in = inputs();

            // Build and validate before any work: an out-of-range
            // threshold or a misspelled thresholdAction must fail now,
            // not after the partitions have been unstashed and merged.
            final SentinelConfiguration config =
                    step.toConfiguration(in.env());
            SentinelConfigValidator.validate(config);

            final String sentinelCmd = SentinelGlobalConfiguration
                    .getEffectivePath(config.getSentinelPath());
            final int total = partitionCount(config);
            final String reportWorkspace = total > UNPARTITIONED
                    ? collectPartitions(in, sentinelCmd, total)
                    : collectSingle(in, config);

            step.prepareManagedOutputDir(
                    in.ws(), in.env(), in.listener());

            SentinelPostProcessor.reportAndJudge(
                    sentinelCmd, reportWorkspace,
                    SentinelEnvironment.effectiveSourceDir(config),
                    SentinelEnvironment.effectiveOutputDir(config),
                    config.getThreshold(), config.getThresholdAction(),
                    in.env(), in.ws(), in.launcher(), in.listener(),
                    in.build(), procHandle);

            return null;
        }

        private String collectPartitions(final Inputs in,
                                         final String sentinelCmd,
                                         final int total) throws Exception {
            unstashPartitions(in.build(), in.ws(), in.launcher(),
                    in.env(), in.listener(), total);
            SentinelWorkspaceCleaner.recreateDirectory(
                    in.ws().child(SentinelEnvironment.MERGED_WORKSPACE),
                    in.listener(), "merged workspace");
            SentinelPostProcessor.merge(
                    sentinelCmd,
                    SentinelPostProcessor.partitionPaths(total),
                    SentinelEnvironment.MERGED_WORKSPACE,
                    in.env(), in.ws(), in.launcher(), in.listener(),
                    procHandle);
            return SentinelEnvironment.MERGED_WORKSPACE;
        }

        private String collectSingle(final Inputs in,
                                     final SentinelConfiguration config)
                throws Exception {
            unstashSingle(in.build(), in.ws(), in.launcher(),
                    in.env(), in.listener(), config);
            return SentinelEnvironment.effectiveSingleWorkspace(config);
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
            return SentinelStepExecution.REQUIRED_CONTEXT;
        }
    }
}
