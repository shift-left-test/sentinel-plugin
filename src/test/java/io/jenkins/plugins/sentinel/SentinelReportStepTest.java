/*
 * Copyright (c) 2026 LG Electronics, Inc. Licensed under the MIT License.
 * SPDX-License-Identifier: MIT
 */

package io.jenkins.plugins.sentinel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.ListBoxModel;
import org.jenkinsci.plugins.workflow.flow.StashManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class SentinelReportStepTest {

    @Test
    void noRequiredConstructorParams() {
        final SentinelReportStep step = new SentinelReportStep();
        assertThat(step).isNotNull();
    }

    @Test
    void allFieldsNullByDefault() {
        final SentinelReportStep step = new SentinelReportStep();
        assertThat(step.getThreshold()).isNull();
        assertThat(step.getThresholdAction()).isNull();
        assertThat(step.getSourceDir()).isNull();
        assertThat(step.getOutputDir()).isNull();
        assertThat(step.getSentinelPath()).isNull();
    }

    @Test
    void thresholdSetterUpdatesValue() {
        final SentinelReportStep step = new SentinelReportStep();
        step.setThreshold(80.0);
        assertThat(step.getThreshold()).isEqualTo(80.0);
    }

    @Test
    void thresholdActionSetterUpdatesValue() {
        final SentinelReportStep step = new SentinelReportStep();
        step.setThresholdAction("UNSTABLE");
        assertThat(step.getThresholdAction())
                .isEqualTo("UNSTABLE");
    }

    @Test
    void sourceDirSetterUpdatesValue() {
        final SentinelReportStep step = new SentinelReportStep();
        step.setSourceDir("src/main");
        assertThat(step.getSourceDir()).isEqualTo("src/main");
    }

    @Test
    void outputDirSetterUpdatesValue() {
        final SentinelReportStep step = new SentinelReportStep();
        step.setOutputDir("my-report");
        assertThat(step.getOutputDir()).isEqualTo("my-report");
    }

    @Test
    void sentinelPathSetterUpdatesValue() {
        final SentinelReportStep step = new SentinelReportStep();
        step.setSentinelPath("/usr/bin/sentinel");
        assertThat(step.getSentinelPath())
                .isEqualTo("/usr/bin/sentinel");
    }

    @Test
    void descriptorFunctionName() {
        final SentinelReportStep.DescriptorImpl descriptor =
                new SentinelReportStep.DescriptorImpl();
        assertThat(descriptor.getFunctionName())
                .isEqualTo("sentinelReport");
    }

    @Test
    void descriptorDisplayName() {
        final SentinelReportStep.DescriptorImpl descriptor =
                new SentinelReportStep.DescriptorImpl();
        assertThat(descriptor.getDisplayName()).isNotBlank();
    }

    @Test
    void descriptorRequiresCorrectContext() {
        final SentinelReportStep.DescriptorImpl descriptor =
                new SentinelReportStep.DescriptorImpl();
        assertThat(descriptor.getRequiredContext())
                .isEqualTo(Set.of(FilePath.class,
                        Launcher.class,
                        TaskListener.class,
                        EnvVars.class,
                        Run.class));
    }

    @Test
    void descriptorFillsThresholdActionItems() {
        final SentinelReportStep.DescriptorImpl descriptor =
                new SentinelReportStep.DescriptorImpl();
        final ListBoxModel items =
                descriptor.doFillThresholdActionItems();
        assertThat(items).hasSizeGreaterThanOrEqualTo(3);
        assertThat(items.get(0).value).isEmpty();
    }

    @Test
    void unstashPartitionsTargetsPartitionSubdirectories(
            @TempDir final Path tempDir) throws Exception {
        final Run<?, ?> build = mock(Run.class);
        final Launcher launcher = mock(Launcher.class);
        final TaskListener listener = mock(TaskListener.class);
        final EnvVars env = new EnvVars();
        when(listener.getLogger())
                .thenReturn(System.out);

        final FilePath ws = new FilePath(tempDir.toFile());
        final List<String> targetPaths = new ArrayList<>();

        try (MockedStatic<StashManager> mocked =
                     Mockito.mockStatic(StashManager.class)) {
            mocked.when(() -> StashManager.unstash(
                    any(), ArgumentMatchers.anyString(),
                    any(FilePath.class),
                    any(), any(), any()))
                    .thenAnswer(invocation -> {
                        final FilePath target =
                                invocation.getArgument(2);
                        targetPaths.add(target.getRemote());
                        return null;
                    });

            SentinelReportStep.unstashPartitions(
                    build, ws, launcher, env, listener, 3);
        }

        assertThat(targetPaths).containsExactly(
                ws.child(".sentinel-1").getRemote(),
                ws.child(".sentinel-2").getRemote(),
                ws.child(".sentinel-3").getRemote());
    }

    @Test
    void unstashSingleTargetsWorkspaceSubdirectory(
            @TempDir final Path tempDir) throws Exception {
        final Run<?, ?> build = mock(Run.class);
        final Launcher launcher = mock(Launcher.class);
        final TaskListener listener = mock(TaskListener.class);
        final EnvVars env = new EnvVars();
        when(listener.getLogger())
                .thenReturn(System.out);

        final FilePath ws = new FilePath(tempDir.toFile());
        final List<String> targetPaths = new ArrayList<>();

        try (MockedStatic<StashManager> mocked =
                     Mockito.mockStatic(StashManager.class)) {
            mocked.when(() -> StashManager.unstash(
                    any(), ArgumentMatchers.anyString(),
                    any(FilePath.class),
                    any(), any(), any()))
                    .thenAnswer(invocation -> {
                        final FilePath target =
                                invocation.getArgument(2);
                        targetPaths.add(target.getRemote());
                        return null;
                    });

            SentinelReportStep.unstashSingle(
                    build, ws, launcher, env, listener);
        }

        assertThat(targetPaths).containsExactly(
                ws.child(
                        SentinelEnvironment
                                .DEFAULT_SINGLE_WORKSPACE)
                        .getRemote());
    }

    @Test
    void unstashSingleUsesEnvWorkspaceWhenSet(
            @TempDir final Path tempDir) throws Exception {
        final Run<?, ?> build = mock(Run.class);
        final Launcher launcher = mock(Launcher.class);
        final TaskListener listener = mock(TaskListener.class);
        final EnvVars env = new EnvVars();
        env.put("SENTINEL_WORKSPACE", "custom-ws");
        when(listener.getLogger())
                .thenReturn(System.out);

        final FilePath ws = new FilePath(tempDir.toFile());
        final List<String> targetPaths = new ArrayList<>();

        try (MockedStatic<StashManager> mocked =
                     Mockito.mockStatic(StashManager.class)) {
            mocked.when(() -> StashManager.unstash(
                    any(), ArgumentMatchers.anyString(),
                    any(FilePath.class),
                    any(), any(), any()))
                    .thenAnswer(invocation -> {
                        final FilePath target =
                                invocation.getArgument(2);
                        targetPaths.add(target.getRemote());
                        return null;
                    });

            SentinelReportStep.unstashSingle(
                    build, ws, launcher, env, listener);
        }

        assertThat(targetPaths).containsExactly(
                ws.child("custom-ws").getRemote());
    }
}
