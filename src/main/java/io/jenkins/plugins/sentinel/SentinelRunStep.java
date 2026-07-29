/*
 * Copyright (c) 2026 LG Electronics, Inc. Licensed under the MIT License.
 * SPDX-License-Identifier: MIT
 */

package io.jenkins.plugins.sentinel;

import java.io.PrintStream;
import java.io.Serializable;
import java.util.Map;
import java.util.Set;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.sentinel.config.SentinelConfigValidator;
import io.jenkins.plugins.sentinel.config.SentinelConfiguration;
import org.jenkinsci.plugins.workflow.flow.StashManager;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Pipeline step that runs sentinel mutation testing.
 *
 * <p>All fields are optional. Configuration is read from
 * SENTINEL_* environment variables. Step-level parameters
 * override environment values when both are set.</p>
 *
 * <p>Usage in Jenkinsfile:</p>
 * <pre>
 * sentinelRun()
 * sentinelRun(partitionIndex: 1)
 * sentinelRun(buildCommand: 'cmake --build .', verbose: true)
 * </pre>
 */

public class SentinelRunStep extends Step implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer partitionIndex;
    private Integer partitionTotal;
    private String buildCommand;
    private String testCommand;
    private String testResultDir;
    private String sourceDir;
    private Long seed;
    private Boolean verbose;
    private String workspace;
    private String sentinelPath;

    /**
     * Creates a new SentinelRunStep with no required fields.
     */
    @DataBoundConstructor
    public SentinelRunStep() {
        super();
    }

    public Integer getPartitionIndex() {
        return partitionIndex;
    }

    @DataBoundSetter
    public void setPartitionIndex(final Integer v) {
        partitionIndex = v;
    }

    public Integer getPartitionTotal() {
        return partitionTotal;
    }

    @DataBoundSetter
    public void setPartitionTotal(final Integer v) {
        partitionTotal = v;
    }

    public String getBuildCommand() {
        return buildCommand;
    }

    @DataBoundSetter
    public void setBuildCommand(final String v) {
        buildCommand = v;
    }

    public String getTestCommand() {
        return testCommand;
    }

    @DataBoundSetter
    public void setTestCommand(final String v) {
        testCommand = v;
    }

    public String getTestResultDir() {
        return testResultDir;
    }

    @DataBoundSetter
    public void setTestResultDir(final String v) {
        testResultDir = v;
    }

    public String getSourceDir() {
        return sourceDir;
    }

    @DataBoundSetter
    public void setSourceDir(final String v) {
        sourceDir = v;
    }

    public Long getSeed() {
        return seed;
    }

    @DataBoundSetter
    public void setSeed(final Long v) {
        seed = v;
    }

    public Boolean isVerbose() {
        return verbose;
    }

    @DataBoundSetter
    public void setVerbose(final Boolean v) {
        verbose = v;
    }

    public String getWorkspace() {
        return workspace;
    }

    @DataBoundSetter
    public void setWorkspace(final String v) {
        workspace = v;
    }

    public String getSentinelPath() {
        return sentinelPath;
    }

    @DataBoundSetter
    public void setSentinelPath(final String v) {
        sentinelPath = v;
    }

    /**
     * Builds a SentinelConfiguration by first reading environment
     * variables via {@link SentinelEnvironment#toConfiguration},
     * then applying any non-null step-level overrides.
     *
     * <p>If {@code partitionIndex} is set and workspace is not
     * explicitly set, auto-assigns the workspace to
     * {@code .sentinel-{index}}.</p>
     *
     * @param env environment variables
     * @return populated SentinelConfiguration
     */
    SentinelConfiguration toConfiguration(
            final Map<String, String> env) {
        final SentinelConfiguration config =
                SentinelEnvironment.toConfiguration(env);
        applyOverrides(config);
        applyAutoWorkspace(config);
        return config;
    }

    String managedWorkspaceForCleanup(final Map<String, String> env) {
        return SentinelEnvironment.managedDefault(
                workspace,
                env.get(SentinelEnvironment.WORKSPACE),
                partitionIndex != null
                        ? SentinelEnvironment.partitionWorkspace(
                                partitionIndex)
                        : SentinelEnvironment.DEFAULT_SINGLE_WORKSPACE);
    }

    void prepareManagedWorkspace(
            final FilePath ws,
            final Map<String, String> env,
            final TaskListener listener) throws Exception {
        SentinelWorkspaceCleaner.recreateIfManaged(
                ws, managedWorkspaceForCleanup(env),
                listener, "sentinel workspace");
    }

    /**
     * Fills in a derived per-build seed when neither the step
     * parameter nor {@code SENTINEL_SEED} provided one, and logs the
     * value so the build can be reproduced by pinning it. Partitions
     * of the same build derive identical seeds, keeping the
     * {@code --partition} split consistent.
     *
     * @param config configuration after env/param merging
     * @param runId  externalizable run ID of the current build
     * @param logger build log destination
     */
    void applySeedFallback(final SentinelConfiguration config,
                           final String runId,
                           final PrintStream logger) {
        if (config.getSeed() != null) {
            return;
        }
        final long generated = SentinelSeed.deriveFrom(runId);
        config.setSeed(generated);
        logger.println("[Sentinel] Using generated seed: " + generated
                + " (set SENTINEL_SEED to pin this run)");
    }

    private void applyOverrides(final SentinelConfiguration c) {
        SentinelEnvironment.override(buildCommand, c::setBuildCommand);
        SentinelEnvironment.override(testCommand, c::setTestCommand);
        SentinelEnvironment.override(testResultDir, c::setTestResultDir);
        SentinelEnvironment.override(sourceDir, c::setSourceDir);
        SentinelEnvironment.override(seed, c::setSeed);
        SentinelEnvironment.override(verbose, c::setVerbose);
        SentinelEnvironment.override(workspace, c::setWorkspace);
        SentinelEnvironment.override(sentinelPath, c::setSentinelPath);
        SentinelEnvironment.override(partitionIndex, c::setPartitionIndex);
        SentinelEnvironment.override(partitionTotal, c::setPartitionTotal);
    }

    private void applyAutoWorkspace(
            final SentinelConfiguration config) {
        if (config.getWorkspace() == null
                && config.getPartitionIndex() != null) {
            config.setWorkspace(
                    SentinelEnvironment.partitionWorkspace(
                            config.getPartitionIndex()));
        }
    }

    @Override
    public StepExecution start(final StepContext context) {
        return new SentinelRunExecution(context, this);
    }

    private static class SentinelRunExecution
            extends SentinelStepExecution<Integer> {

        private static final long serialVersionUID = 1L;
        private final SentinelRunStep step;

        SentinelRunExecution(final StepContext context,
                             final SentinelRunStep stepRef) {
            super(context);
            this.step = stepRef;
        }

        @Override
        protected Integer run() throws Exception {
            final Inputs in = inputs();

            final SentinelConfiguration config =
                    step.toConfiguration(in.env());
            step.applySeedFallback(config,
                    in.build().getExternalizableId(),
                    in.listener().getLogger());
            SentinelConfigValidator.validate(config);
            step.prepareManagedWorkspace(
                    in.ws(), in.env(), in.listener());

            SentinelRunner.run(
                    SentinelGlobalConfiguration.getEffectivePath(
                            config.getSentinelPath()),
                    SentinelCommandBuilder.buildRunArgs(config),
                    in.env(), in.ws(), in.launcher(), in.listener(),
                    procHandle);

            stashResults(in.ws(), config, in.listener(), in.build());
            return 0;
        }

        private void stashResults(
                final FilePath ws,
                final SentinelConfiguration config,
                final TaskListener listener,
                final Run<?, ?> build) throws Exception {
            final Integer idx = config.getPartitionIndex();
            final String stashName = idx != null
                    ? SentinelEnvironment.stashName(idx)
                    : SentinelEnvironment.SINGLE_STASH_NAME;
            // Stash the directory sentinel was actually told to write to,
            // never a recomputed partition path: when the user pins an
            // explicit workspace alongside partitionIndex, .sentinel-{idx}
            // does not exist and the partition would stash nothing.
            // The stash is keyed by name and its contents are relative to
            // this root, so sentinelReport can still unstash it into
            // .sentinel-{idx} for the merge.
            final String stashDir =
                    SentinelEnvironment.effectiveSingleWorkspace(config);

            listener.getLogger().println(
                    "[Sentinel] Stashing results: "
                            + stashName + " from " + stashDir);

            StashManager.stash(
                    build, stashName, ws.child(stashDir),
                    listener, "**", null, false, true);
        }
    }

    /**
     * Descriptor for the sentinelRun pipeline step.
     */
    @Extension
    public static class DescriptorImpl extends StepDescriptor {

        @Override
        public String getFunctionName() {
            return "sentinelRun";
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return "Run sentinel mutation testing";
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return SentinelStepExecution.REQUIRED_CONTEXT;
        }
    }
}
