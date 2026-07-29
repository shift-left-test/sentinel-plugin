/*
 * Copyright (c) 2026 LG Electronics, Inc. Licensed under the MIT License.
 * SPDX-License-Identifier: MIT
 */

package io.jenkins.plugins.sentinel;

import static io.jenkins.plugins.sentinel.SentinelTestSupport.loggingListener;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.sentinel.config.SentinelConfigValidator;
import io.jenkins.plugins.sentinel.config.SentinelConfiguration;
import io.jenkins.plugins.sentinel.config.ThresholdAction;
import org.jenkinsci.plugins.workflow.flow.StashManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class SentinelReportStepTest {

    private static final String PARTITION_ONE =
            SentinelEnvironment.partitionWorkspace(1);
    private static final String PARTITION_TWO =
            SentinelEnvironment.partitionWorkspace(2);
    private static final String PARTITION_THREE =
            SentinelEnvironment.partitionWorkspace(3);
    private static final String STALE_FILE = "stale.txt";
    private static final String STALE_XML = "stale.xml";
    private static final String OLD = "old";
    private static final String UTF_8 = "UTF-8";
    private static final String CUSTOM_WORKSPACE = "custom-ws";
    private static final String CUSTOM_REPORT = "custom-report";

    @Test
    void allFieldsNullByDefault() {
        final SentinelReportStep step = new SentinelReportStep();
        assertThat(step.getThreshold()).isNull();
        assertThat(step.getThresholdAction()).isNull();
        assertThat(step.getSourceDir()).isNull();
        assertThat(step.getOutputDir()).isNull();
        assertThat(step.getSentinelPath()).isNull();
        assertThat(step.getPartitionTotal()).isNull();
    }

    @Test
    void settersRoundTrip() {
        final SentinelReportStep step = new SentinelReportStep();
        step.setThreshold(80.0);
        step.setThresholdAction("UNSTABLE");
        step.setSourceDir("src/main");
        step.setOutputDir("my-report");
        step.setSentinelPath("/usr/bin/sentinel");
        step.setPartitionTotal(4);

        assertThat(step.getThreshold()).isEqualTo(80.0);
        assertThat(step.getThresholdAction()).isEqualTo("UNSTABLE");
        assertThat(step.getSourceDir()).isEqualTo("src/main");
        assertThat(step.getOutputDir()).isEqualTo("my-report");
        assertThat(step.getSentinelPath()).isEqualTo("/usr/bin/sentinel");
        assertThat(step.getPartitionTotal()).isEqualTo(4);
    }

    // --- configuration merging -------------------------------------------

    @Test
    void toConfigurationReadsEnvironmentVariables() {
        final EnvVars env = new EnvVars();
        env.put(SentinelEnvironment.PARTITION_TOTAL, "4");
        env.put(SentinelEnvironment.SOURCE_DIR, "env-src");
        env.put(SentinelEnvironment.OUTPUT_DIR, "env-report");
        env.put(SentinelEnvironment.PATH, "/env/sentinel");

        final SentinelConfiguration config =
                new SentinelReportStep().toConfiguration(env);

        assertThat(config.getPartitionTotal()).isEqualTo(4);
        assertThat(config.getSourceDir()).isEqualTo("env-src");
        assertThat(config.getOutputDir()).isEqualTo("env-report");
        assertThat(config.getSentinelPath()).isEqualTo("/env/sentinel");
    }

    @Test
    void stepParamsOverrideEnvironmentVariables() {
        final SentinelReportStep step = new SentinelReportStep();
        step.setPartitionTotal(8);
        step.setSourceDir("step-src");
        step.setOutputDir("step-report");
        step.setSentinelPath("/step/sentinel");

        final EnvVars env = new EnvVars();
        env.put(SentinelEnvironment.PARTITION_TOTAL, "4");
        env.put(SentinelEnvironment.SOURCE_DIR, "env-src");
        env.put(SentinelEnvironment.OUTPUT_DIR, "env-report");
        env.put(SentinelEnvironment.PATH, "/env/sentinel");

        final SentinelConfiguration config = step.toConfiguration(env);

        assertThat(config.getPartitionTotal()).isEqualTo(8);
        assertThat(config.getSourceDir()).isEqualTo("step-src");
        assertThat(config.getOutputDir()).isEqualTo("step-report");
        assertThat(config.getSentinelPath()).isEqualTo("/step/sentinel");
    }

    @Test
    void thresholdPairReachesTheConfiguration() {
        final SentinelReportStep step = new SentinelReportStep();
        step.setThreshold(80.0);
        step.setThresholdAction("unstable");

        final SentinelConfiguration config =
                step.toConfiguration(new EnvVars());

        assertThat(config.getThreshold()).isEqualTo(80.0);
        assertThat(config.getThresholdAction())
                .isEqualTo(ThresholdAction.UNSTABLE);
    }

    @Test
    void blankThresholdActionCountsAsUnset() {
        // The snippet generator offers an empty option; it must not throw.
        final SentinelReportStep step = new SentinelReportStep();
        step.setThresholdAction("");

        assertThat(step.toConfiguration(new EnvVars())
                .getThresholdAction()).isNull();
    }

    @Test
    void invalidThresholdActionFailsWhileBuildingTheConfiguration() {
        final SentinelReportStep step = new SentinelReportStep();
        step.setThresholdAction("UNSTBALE");
        final EnvVars env = new EnvVars();

        assertThatThrownBy(() -> step.toConfiguration(env))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UNSTBALE");
    }

    @Test
    void thresholdIsValidatedBeforeAnyWorkHappens() {
        // The whole point of routing the report step through
        // SentinelConfiguration: these checks used to be unreachable.
        final SentinelReportStep step = new SentinelReportStep();
        step.setThreshold(150.0);
        step.setThresholdAction("FAILURE");
        final SentinelConfiguration config =
                step.toConfiguration(new EnvVars());

        assertThatThrownBy(() -> SentinelConfigValidator.validate(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("threshold must be between");
    }

    @Test
    void thresholdWithoutAnActionIsRejected() {
        final SentinelReportStep step = new SentinelReportStep();
        step.setThreshold(80.0);
        final SentinelConfiguration config =
                step.toConfiguration(new EnvVars());

        assertThatThrownBy(() -> SentinelConfigValidator.validate(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("thresholdAction is required");
    }

    @Test
    void actionWithoutAThresholdIsRejected() {
        final SentinelReportStep step = new SentinelReportStep();
        step.setThresholdAction("FAILURE");
        final SentinelConfiguration config =
                step.toConfiguration(new EnvVars());

        assertThatThrownBy(() -> SentinelConfigValidator.validate(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("threshold is required");
    }

    @Test
    void nonPositivePartitionTotalIsRejected() {
        final SentinelReportStep step = new SentinelReportStep();
        step.setPartitionTotal(0);
        final SentinelConfiguration config =
                step.toConfiguration(new EnvVars());

        assertThatThrownBy(() -> SentinelConfigValidator.validate(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("partitionTotal must be a positive");
    }

    @Test
    void nonNumericPartitionTotalNamesTheVariable() {
        final EnvVars env = new EnvVars();
        env.put(SentinelEnvironment.PARTITION_TOTAL, "four");
        final SentinelReportStep step = new SentinelReportStep();

        assertThatThrownBy(() -> step.toConfiguration(env))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        SentinelEnvironment.PARTITION_TOTAL);
    }

    // --- partition count -------------------------------------------------

    @Test
    void partitionCountIsZeroWhenUnset() {
        assertThat(SentinelReportStep.partitionCount(
                new SentinelReportStep().toConfiguration(new EnvVars())))
                .isZero();
    }

    @Test
    void partitionCountReadsTheMergedConfiguration() {
        final EnvVars env = new EnvVars();
        env.put(SentinelEnvironment.PARTITION_TOTAL, "4");
        assertThat(SentinelReportStep.partitionCount(
                new SentinelReportStep().toConfiguration(env)))
                .isEqualTo(4);
    }

    @Test
    void blankPartitionTotalCountsAsUnset() {
        final EnvVars env = new EnvVars();
        env.put(SentinelEnvironment.PARTITION_TOTAL, "   ");
        assertThat(SentinelReportStep.partitionCount(
                new SentinelReportStep().toConfiguration(env)))
                .isZero();
    }

    // --- managed directories ---------------------------------------------

    @Test
    void managedOutputDirForCleanupUsesDefaultWhenUnset() {
        assertThat(new SentinelReportStep()
                .managedOutputDirForCleanup(new EnvVars()))
                .isEqualTo(SentinelEnvironment.DEFAULT_OUTPUT_DIR);
    }

    @Test
    void managedOutputDirForCleanupSkipsEnvOverride() {
        final EnvVars env = new EnvVars();
        env.put(SentinelEnvironment.OUTPUT_DIR, CUSTOM_REPORT);
        assertThat(new SentinelReportStep()
                .managedOutputDirForCleanup(env)).isNull();
    }

    @Test
    void managedOutputDirForCleanupTreatsBlankEnvAsUnset() {
        final EnvVars env = new EnvVars();
        env.put(SentinelEnvironment.OUTPUT_DIR, "");
        assertThat(new SentinelReportStep()
                .managedOutputDirForCleanup(env))
                .isEqualTo(SentinelEnvironment.DEFAULT_OUTPUT_DIR);
    }

    @Test
    void managedOutputDirForCleanupSkipsStepOverride() {
        final SentinelReportStep step = new SentinelReportStep();
        step.setOutputDir(CUSTOM_REPORT);
        assertThat(step.managedOutputDirForCleanup(new EnvVars())).isNull();
    }

    @Test
    void managedSingleWorkspaceUsesDefaultWhenUnset() {
        assertThat(SentinelReportStep.managedSingleWorkspaceForCleanup(
                new EnvVars()))
                .isEqualTo(SentinelEnvironment.DEFAULT_SINGLE_WORKSPACE);
    }

    @Test
    void managedSingleWorkspaceSkipsEnvOverride() {
        final EnvVars env = new EnvVars();
        env.put(SentinelEnvironment.WORKSPACE, CUSTOM_WORKSPACE);
        assertThat(SentinelReportStep.managedSingleWorkspaceForCleanup(env))
                .isNull();
    }

    @Test
    void prepareManagedOutputDirClearsDefaultDirectory(
            @TempDir final Path tempDir) throws Exception {
        final FilePath ws = new FilePath(tempDir.toFile());
        final FilePath output =
                ws.child(SentinelEnvironment.DEFAULT_OUTPUT_DIR);
        output.mkdirs();
        output.child(STALE_XML).write(OLD, UTF_8);

        new SentinelReportStep().prepareManagedOutputDir(
                ws, new EnvVars(), listener());

        assertThat(output.exists()).isTrue();
        assertThat(output.child(STALE_XML).exists()).isFalse();
    }

    @Test
    void prepareManagedOutputDirPreservesAUserChosenDirectory(
            @TempDir final Path tempDir) throws Exception {
        final SentinelReportStep step = new SentinelReportStep();
        step.setOutputDir(CUSTOM_REPORT);
        final FilePath ws = new FilePath(tempDir.toFile());
        final FilePath output = ws.child(CUSTOM_REPORT);
        output.mkdirs();
        output.child(STALE_XML).write(OLD, UTF_8);

        step.prepareManagedOutputDir(ws, new EnvVars(), listener());

        assertThat(output.child(STALE_XML).exists()).isTrue();
    }

    // --- unstash ---------------------------------------------------------

    @Test
    void unstashSingleTargetsTheDefaultWorkspace(
            @TempDir final Path tempDir) throws Exception {
        final FilePath ws = new FilePath(tempDir.toFile());
        final List<String> targets = new ArrayList<>();

        recordingUnstash(targets, () -> SentinelReportStep.unstashSingle(
                mock(Run.class), ws, mock(Launcher.class),
                new EnvVars(), listener(),
                new SentinelReportStep().toConfiguration(new EnvVars())));

        assertThat(targets).containsExactly(
                ws.child(SentinelEnvironment.DEFAULT_SINGLE_WORKSPACE)
                        .getRemote());
    }

    @Test
    void unstashSingleFollowsTheConfiguredWorkspace(
            @TempDir final Path tempDir) throws Exception {
        final EnvVars env = new EnvVars();
        env.put(SentinelEnvironment.WORKSPACE, CUSTOM_WORKSPACE);
        final FilePath ws = new FilePath(tempDir.toFile());
        final List<String> targets = new ArrayList<>();

        recordingUnstash(targets, () -> SentinelReportStep.unstashSingle(
                mock(Run.class), ws, mock(Launcher.class), env,
                listener(),
                new SentinelReportStep().toConfiguration(env)));

        assertThat(targets).containsExactly(
                ws.child(CUSTOM_WORKSPACE).getRemote());
    }

    @Test
    void unstashSingleTreatsABlankWorkspaceAsUnset(
            @TempDir final Path tempDir) throws Exception {
        // SENTINEL_WORKSPACE='' used to read as "set", sending the unstash
        // to a directory named "" while the run wrote elsewhere.
        final EnvVars env = new EnvVars();
        env.put(SentinelEnvironment.WORKSPACE, "");
        final FilePath ws = new FilePath(tempDir.toFile());
        final List<String> targets = new ArrayList<>();

        recordingUnstash(targets, () -> SentinelReportStep.unstashSingle(
                mock(Run.class), ws, mock(Launcher.class), env,
                listener(),
                new SentinelReportStep().toConfiguration(env)));

        assertThat(targets).containsExactly(
                ws.child(SentinelEnvironment.DEFAULT_SINGLE_WORKSPACE)
                        .getRemote());
    }

    @Test
    void unstashSingleClearsTheDefaultWorkspaceFirst(
            @TempDir final Path tempDir) throws Exception {
        final FilePath ws = new FilePath(tempDir.toFile());
        final FilePath managed = ws.child(
                SentinelEnvironment.DEFAULT_SINGLE_WORKSPACE);
        managed.mkdirs();
        managed.child(STALE_FILE).write(OLD, UTF_8);

        recordingUnstash(new ArrayList<>(),
                () -> SentinelReportStep.unstashSingle(
                        mock(Run.class), ws, mock(Launcher.class),
                        new EnvVars(), listener(),
                        new SentinelReportStep().toConfiguration(
                                new EnvVars())));

        assertThat(managed.exists()).isTrue();
        assertThat(managed.child(STALE_FILE).exists()).isFalse();
    }

    @Test
    void unstashSinglePreservesAUserChosenWorkspace(
            @TempDir final Path tempDir) throws Exception {
        final EnvVars env = new EnvVars();
        env.put(SentinelEnvironment.WORKSPACE, CUSTOM_WORKSPACE);
        final FilePath ws = new FilePath(tempDir.toFile());
        final FilePath custom = ws.child(CUSTOM_WORKSPACE);
        custom.mkdirs();
        custom.child(STALE_FILE).write(OLD, UTF_8);

        recordingUnstash(new ArrayList<>(),
                () -> SentinelReportStep.unstashSingle(
                        mock(Run.class), ws, mock(Launcher.class), env,
                        listener(),
                        new SentinelReportStep().toConfiguration(env)));

        assertThat(custom.child(STALE_FILE).exists()).isTrue();
    }

    @Test
    void unstashPartitionsTargetsPartitionSubdirectories(
            @TempDir final Path tempDir) throws Exception {
        final FilePath ws = new FilePath(tempDir.toFile());
        final List<String> targets = new ArrayList<>();

        recordingUnstash(targets,
                () -> SentinelReportStep.unstashPartitions(
                        mock(Run.class), ws, mock(Launcher.class),
                        new EnvVars(), listener(), 3));

        assertThat(targets).containsExactly(
                ws.child(PARTITION_ONE).getRemote(),
                ws.child(PARTITION_TWO).getRemote(),
                ws.child(PARTITION_THREE).getRemote());
    }

    @Test
    void unstashPartitionsClearsExistingPartitionDirectories(
            @TempDir final Path tempDir) throws Exception {
        final FilePath ws = new FilePath(tempDir.toFile());
        for (final String dir
                : List.of(PARTITION_ONE, PARTITION_TWO, PARTITION_THREE)) {
            ws.child(dir).child(STALE_FILE).write(OLD, UTF_8);
        }

        recordingUnstash(new ArrayList<>(),
                () -> SentinelReportStep.unstashPartitions(
                        mock(Run.class), ws, mock(Launcher.class),
                        new EnvVars(), listener(), 3));

        for (final String dir
                : List.of(PARTITION_ONE, PARTITION_TWO, PARTITION_THREE)) {
            assertThat(ws.child(dir).exists()).isTrue();
            assertThat(ws.child(dir).child(STALE_FILE).exists()).isFalse();
        }
    }

    @Test
    void unstashPartitionsFailsClearlyWhenPartitionMissing(
            @TempDir final Path tempDir) throws Exception {
        final FilePath ws = new FilePath(tempDir.toFile());
        final Run<?, ?> build = mock(Run.class);
        final Launcher launcher = mock(Launcher.class);
        final EnvVars env = new EnvVars();
        final TaskListener listener = listener();

        try (MockedStatic<StashManager> mocked =
                     Mockito.mockStatic(StashManager.class)) {
            mocked.when(() -> StashManager.unstash(
                    any(), ArgumentMatchers.anyString(),
                    any(FilePath.class), any(), any(), any()))
                    .thenAnswer(invocation -> {
                        final String name = invocation.getArgument(1);
                        if (name.equals(SentinelEnvironment.stashName(2))) {
                            throw new IOException(
                                    "No such saved stash '" + name + "'");
                        }
                        return null;
                    });

            assertThatThrownBy(() -> SentinelReportStep.unstashPartitions(
                    build, ws, launcher, env, listener, 3))
                    .isInstanceOf(AbortException.class)
                    .hasMessageContaining("partition 2 of 3");
        }
    }

    // --- descriptor ------------------------------------------------------

    @Test
    void descriptorFunctionName() {
        assertThat(new SentinelReportStep.DescriptorImpl()
                .getFunctionName()).isEqualTo("sentinelReport");
    }

    @Test
    void descriptorDisplayName() {
        assertThat(new SentinelReportStep.DescriptorImpl()
                .getDisplayName()).isNotBlank();
    }

    @Test
    void descriptorRequiresCorrectContext() {
        assertThat(new SentinelReportStep.DescriptorImpl()
                .getRequiredContext())
                .isEqualTo(Set.of(FilePath.class, Launcher.class,
                        TaskListener.class, EnvVars.class, Run.class));
    }

    @Test
    void descriptorFillsThresholdActionItems() {
        final ListBoxModel items = new SentinelReportStep.DescriptorImpl()
                .doFillThresholdActionItems();
        assertThat(items).hasSize(ThresholdAction.values().length + 1);
        assertThat(items.get(0).value).isEmpty();
    }

    @Test
    void startReturnsAnExecution() {
        assertThat(new SentinelReportStep()
                .start(mock(org.jenkinsci.plugins.workflow.steps
                        .StepContext.class))).isNotNull();
    }

    // --- helpers ---------------------------------------------------------

    private static TaskListener listener() {
        return loggingListener(new ByteArrayOutputStream());
    }

    /** Something under test that may throw. */
    @FunctionalInterface
    private interface Body {
        void run() throws Exception;
    }

    /**
     * Runs {@code body} with StashManager.unstash stubbed out, recording
     * each target directory it was pointed at.
     *
     * <p>A callback rather than a returned {@code MockedStatic} so the
     * static mock is always closed and no caller has to name a resource
     * it never touches.</p>
     */
    private static void recordingUnstash(final List<String> targets,
                                         final Body body) throws Exception {
        try (MockedStatic<StashManager> mocked =
                     Mockito.mockStatic(StashManager.class)) {
            mocked.when(() -> StashManager.unstash(
                    any(), ArgumentMatchers.anyString(),
                    any(FilePath.class), any(), any(), any()))
                    .thenAnswer(invocation -> {
                        final FilePath target = invocation.getArgument(2);
                        targets.add(target.getRemote());
                        return null;
                    });
            body.run();
        }
    }
}
