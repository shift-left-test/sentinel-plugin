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
import io.jenkins.plugins.sentinel.config.SentinelConfigValidator;
import io.jenkins.plugins.sentinel.config.SentinelConfiguration;
import org.jenkinsci.plugins.workflow.flow.StashManager;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
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

    /**
     * Returns the partition index.
     *
     * @return partition index, or null if not set
     */
    public Integer getPartitionIndex() {
        return partitionIndex;
    }

    /**
     * Sets the partition index.
     *
     * @param v partition index (1-based)
     */
    @DataBoundSetter
    public void setPartitionIndex(final Integer v) {
        partitionIndex = v;
    }

    /**
     * Returns the build command.
     *
     * @return build command, or null if not set
     */
    public String getBuildCommand() {
        return buildCommand;
    }

    /**
     * Sets the build command.
     *
     * @param v build command
     */
    @DataBoundSetter
    public void setBuildCommand(final String v) {
        buildCommand = v;
    }

    /**
     * Returns the test command.
     *
     * @return test command, or null if not set
     */
    public String getTestCommand() {
        return testCommand;
    }

    /**
     * Sets the test command.
     *
     * @param v test command
     */
    @DataBoundSetter
    public void setTestCommand(final String v) {
        testCommand = v;
    }

    /**
     * Returns the test result directory.
     *
     * @return test result directory, or null if not set
     */
    public String getTestResultDir() {
        return testResultDir;
    }

    /**
     * Sets the test result directory.
     *
     * @param v test result directory
     */
    @DataBoundSetter
    public void setTestResultDir(final String v) {
        testResultDir = v;
    }

    /**
     * Returns the source directory.
     *
     * @return source directory, or null if not set
     */
    public String getSourceDir() {
        return sourceDir;
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
     * Returns the random seed.
     *
     * @return seed, or null if not set
     */
    public Long getSeed() {
        return seed;
    }

    /**
     * Sets the random seed.
     *
     * @param v seed
     */
    @DataBoundSetter
    public void setSeed(final Long v) {
        seed = v;
    }

    /**
     * Returns whether verbose output is enabled.
     *
     * @return verbose flag, or null if not set
     */
    public Boolean isVerbose() {
        return verbose;
    }

    /**
     * Sets whether verbose output is enabled.
     *
     * @param v verbose flag
     */
    @DataBoundSetter
    public void setVerbose(final Boolean v) {
        verbose = v;
    }

    /**
     * Returns the sentinel workspace directory.
     *
     * @return workspace, or null if not set
     */
    public String getWorkspace() {
        return workspace;
    }

    /**
     * Sets the sentinel workspace directory.
     *
     * @param v workspace
     */
    @DataBoundSetter
    public void setWorkspace(final String v) {
        workspace = v;
    }

    /**
     * Returns the sentinel executable path override.
     *
     * @return sentinel path, or null if not set
     */
    public String getSentinelPath() {
        return sentinelPath;
    }

    /**
     * Sets the sentinel executable path override.
     *
     * @param v sentinel path
     */
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
        if (workspace != null) {
            return null;
        }
        final String envWorkspace = env.get(SentinelEnvironment.WORKSPACE);
        if (envWorkspace != null && !envWorkspace.isEmpty()) {
            return null;
        }
        if (partitionIndex != null) {
            return SentinelEnvironment.partitionWorkspace(partitionIndex);
        }
        return SentinelEnvironment.DEFAULT_SINGLE_WORKSPACE;
    }

    void prepareManagedWorkspace(
            final FilePath ws,
            final Map<String, String> env,
            final TaskListener listener) throws Exception {
        final String managedWorkspace = managedWorkspaceForCleanup(env);
        if (managedWorkspace != null) {
            SentinelWorkspaceCleaner.recreateDirectory(
                    ws.child(managedWorkspace),
                    listener,
                    "sentinel workspace");
        }
    }

    @SuppressWarnings("PMD.NPathComplexity")
    private void applyOverrides(final SentinelConfiguration c) {
        if (buildCommand != null) {
            c.setBuildCommand(buildCommand);
        }
        if (testCommand != null) {
            c.setTestCommand(testCommand);
        }
        if (testResultDir != null) {
            c.setTestResultDir(testResultDir);
        }
        if (sourceDir != null) {
            c.setSourceDir(sourceDir);
        }
        if (seed != null) {
            c.setSeed(seed);
        }
        if (verbose != null) {
            c.setVerbose(verbose);
        }
        if (workspace != null) {
            c.setWorkspace(workspace);
        }
        if (sentinelPath != null) {
            c.setSentinelPath(sentinelPath);
        }
        if (partitionIndex != null) {
            c.setPartitionIndex(partitionIndex);
        }
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
            extends SynchronousNonBlockingStepExecution<Integer> {

        private static final long serialVersionUID = 1L;
        private final SentinelRunStep step;

        SentinelRunExecution(final StepContext context,
                             final SentinelRunStep stepRef) {
            super(context);
            this.step = stepRef;
        }

        @Override
        protected Integer run() throws Exception {
            final TaskListener listener = getContext()
                    .get(TaskListener.class);
            final FilePath ws = getContext().get(FilePath.class);
            final Launcher launcher = getContext()
                    .get(Launcher.class);
            final Map<String, String> env =
                    getContext().get(EnvVars.class);
            final Run<?, ?> build = getContext().get(Run.class);

            final SentinelConfiguration config =
                    step.toConfiguration(env);
            SentinelConfigValidator.validate(config);
            step.prepareManagedWorkspace(ws, env, listener);

            final String sentinelCmd = SentinelGlobalConfiguration
                    .getEffectivePath(config.getSentinelPath());

            final List<String> args = SentinelCommandBuilder
                    .buildRunArgs(config);
            args.add(0, sentinelCmd);

            SentinelRunner.run(args, env, ws, launcher, listener);

            stashResults(ws, config, listener, build);
            return 0;
        }

        private void stashResults(
                final FilePath ws,
                final SentinelConfiguration config,
                final TaskListener listener,
                final Run<?, ?> build) throws Exception {
            final Integer idx = config.getPartitionIndex();
            final String stashName;
            final String stashDir;

            if (idx != null) {
                stashName = SentinelEnvironment.stashName(idx);
                stashDir = SentinelEnvironment
                        .partitionWorkspace(idx);
            } else {
                stashName = SentinelEnvironment.SINGLE_STASH_NAME;
                stashDir = config.getWorkspace() != null
                        ? config.getWorkspace()
                        : SentinelEnvironment
                                .DEFAULT_SINGLE_WORKSPACE;
            }

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
            return Set.of(
                    FilePath.class, Launcher.class,
                    TaskListener.class, EnvVars.class,
                    Run.class);
        }
    }
}
