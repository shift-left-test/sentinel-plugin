/*
 * Copyright (c) 2026 LG Electronics, Inc. Licensed under the MIT License.
 * SPDX-License-Identifier: MIT
 */

package io.jenkins.plugins.sentinel;

import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import io.jenkins.plugins.sentinel.config.SentinelConfiguration;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Pipeline step that runs sentinel on a single partition.
 *
 * <p>Usage in Jenkinsfile:</p>
 * <pre>
 * sentinelRun(
 *     buildCommand: 'make all',
 *     testCommand: 'make test',
 *     testResultDir: 'test-results/',
 *     partition: '1/4',
 *     seed: 12345
 * )
 * </pre>
 */

public class SentinelRunStep extends SentinelStepBase {

    private static final long serialVersionUID = 1L;

    private String partition;

    /**
     * Creates a new SentinelRunStep with required fields.
     *
     * @param buildCommand  build command
     * @param testCommand   test command
     * @param testResultDir test result directory
     */
    @DataBoundConstructor
    public SentinelRunStep(
            final String buildCommand,
            final String testCommand,
            final String testResultDir) {
        super(buildCommand, testCommand, testResultDir);
    }

    /**
     * Returns the partition spec.
     *
     * @return partition spec
     */
    public String getPartition() {
        return partition;
    }

    /**
     * Sets the partition spec.
     *
     * @param v partition spec
     */
    @DataBoundSetter
    public void setPartition(final String v) {
        partition = v;
    }

    /**
     * Converts this step's fields into a SentinelConfiguration.
     *
     * @return populated SentinelConfiguration
     */
    public SentinelConfiguration toConfiguration() {
        final SentinelConfiguration c = new SentinelConfiguration();
        populateConfiguration(c);
        c.setPartition(partition);
        return c;
    }

    /**
     * Converts this step's fields into a SentinelConfiguration,
     * auto-detecting partition, workspace, and seed from environment
     * variables when not explicitly set.
     *
     * <p>Environment variables checked:</p>
     * <ul>
     *   <li>SENTINEL_PARTITION_INDEX + SENTINEL_PARTITION_TOTAL
     *       -> partition ("index/total"), workspace (".sentinel-{index}")</li>
     *   <li>SENTINEL_SEED -> seed</li>
     * </ul>
     *
     * @param env environment variables
     * @return populated SentinelConfiguration
     */
    public SentinelConfiguration toConfiguration(
            final Map<String, String> env) {
        final SentinelConfiguration c = new SentinelConfiguration();
        populateConfiguration(c);
        c.setPartition(partition);

        final String envIndex = env.get(
                SentinelPostProcessor.ENV_PARTITION_INDEX);
        final String envTotal = env.get(
                SentinelPostProcessor.ENV_PARTITION_TOTAL);
        if (c.getPartition() == null
                && envIndex != null && envTotal != null) {
            c.setPartition(envIndex + "/" + envTotal);
        }
        if (c.getWorkspace() == null
                && envIndex != null) {
            c.setWorkspace(
                    SentinelPostProcessor.PARTITION_PREFIX
                            + envIndex);
        }

        final String envSeed = env.get(
                SentinelPostProcessor.ENV_SEED);
        if (c.getSeed() == null && envSeed != null) {
            c.setSeed(Long.parseLong(envSeed));
        }

        return c;
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
                             final SentinelRunStep step) {
            super(context);
            this.step = step;
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

            final SentinelConfiguration config =
                    step.toConfiguration(env);
            final String sentinelCmd = SentinelGlobalConfiguration
                    .getEffectivePath(config.getSentinelPath());

            final List<String> args = SentinelCommandBuilder
                    .buildRunArgs(config);
            args.add(0, sentinelCmd);

            SentinelRunner.run(args, env, ws, launcher, listener);
            return 0;
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
            return "Run mutation test";
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Set.of(
                    FilePath.class, Launcher.class,
                    TaskListener.class, EnvVars.class);
        }
    }
}
