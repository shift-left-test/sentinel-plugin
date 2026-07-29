/*
 * Copyright (c) 2026 LG Electronics, Inc. Licensed under the MIT License.
 * SPDX-License-Identifier: MIT
 */

package io.jenkins.plugins.sentinel;

import static io.jenkins.plugins.sentinel.SentinelTestSupport.commands;
import static io.jenkins.plugins.sentinel.SentinelTestSupport.loggingListener;
import static io.jenkins.plugins.sentinel.SentinelTestSupport.mockLauncher;
import static io.jenkins.plugins.sentinel.SentinelTestSupport.mutationsXml;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import hudson.AbortException;
import hudson.FilePath;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.sentinel.config.ThresholdAction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

class SentinelPostProcessorTest {

    private static final String SENTINEL_CMD = "sentinel";
    private static final String SENTINEL_1 = ".sentinel-1";
    private static final String MERGED_WS = ".sentinel-merged";
    private static final String OUT_DIR = "sentinel-report";
    private static final String SRC_DIR = ".";

    @Test
    void partitionPathsGeneratesCorrectPaths() {
        assertThat(SentinelPostProcessor.partitionPaths(4))
                .containsExactly(SENTINEL_1, ".sentinel-2",
                        ".sentinel-3", ".sentinel-4");
    }

    @Test
    void partitionPathsSinglePartition() {
        assertThat(SentinelPostProcessor.partitionPaths(1))
                .containsExactly(SENTINEL_1);
    }

    @Test
    void partitionPathsZeroReturnsEmpty() {
        assertThat(SentinelPostProcessor.partitionPaths(0)).isEmpty();
    }

    @Test
    void mergeRunsSentinelWithEveryPartition(
            @TempDir final Path tempDir) throws Exception {
        final SentinelTestSupport.LauncherStub stub = mockLauncher(0);

        SentinelPostProcessor.merge(SENTINEL_CMD,
                List.of(SENTINEL_1, ".sentinel-2"), MERGED_WS,
                Map.of(), new FilePath(tempDir.toFile()),
                stub.launcher(),
                loggingListener(new ByteArrayOutputStream()),
                new SentinelProcHandle());

        assertThat(commands(stub)).singleElement()
                .asInstanceOf(
                        org.assertj.core.api.InstanceOfAssertFactories.LIST)
                .containsExactly(SENTINEL_CMD,
                        "--merge-partition=.sentinel-1",
                        "--merge-partition=.sentinel-2",
                        "--workspace=.sentinel-merged");
    }

    @Test
    void mergeFailsWhenSentinelExitsNonZero(
            @TempDir final Path tempDir) throws Exception {
        final SentinelTestSupport.LauncherStub stub = mockLauncher(3);
        final FilePath ws = new FilePath(tempDir.toFile());
        final TaskListener listener =
                loggingListener(new ByteArrayOutputStream());

        assertThatThrownBy(() -> SentinelPostProcessor.merge(
                SENTINEL_CMD, List.of(SENTINEL_1), MERGED_WS, Map.of(),
                ws, stub.launcher(), listener, new SentinelProcHandle()))
                .isInstanceOf(AbortException.class)
                .hasMessageContaining("exited with code 3");
    }

    @Test
    void reportAndJudgeRunsReportArchivesAndAttachesTheAction(
            @TempDir final Path workspace,
            @TempDir final Path buildRoot) throws Exception {
        final SentinelTestSupport.LauncherStub stub = mockLauncher(0);
        writeReport(workspace, mutationsXml(3, 1, 0));
        final Run<?, ?> build = mockBuild(buildRoot);
        final ByteArrayOutputStream log = new ByteArrayOutputStream();

        reportAndJudge(stub, workspace, build, log, null, null);

        // The report command carries the workspace, source and output dirs.
        assertThat(commands(stub)).singleElement()
                .asInstanceOf(
                        org.assertj.core.api.InstanceOfAssertFactories.LIST)
                .containsExactly(SENTINEL_CMD,
                        "--workspace=" + MERGED_WS,
                        "--source-dir=" + SRC_DIR,
                        "--output-dir=" + OUT_DIR);

        // mutations.xml is copied into the build directory so the report
        // survives workspace cleanup.
        assertThat(buildRoot.resolve(SentinelEnvironment.ARCHIVE_DIR)
                .resolve(SentinelEnvironment.MUTATIONS_XML)).exists();

        final SentinelBuildAction action = attachedAction(build);
        assertThat(action.getResult().overallScore().killed()).isEqualTo(3);
        assertThat(action.getResult().overallScore().survived())
                .isEqualTo(1);
        assertThat(action.getRun()).isSameAs(build);
        assertThat(log.toString(StandardCharsets.UTF_8))
                .contains("Score: 75.0%")
                .contains("killed=3, survived=1, skipped=0");
        verify(build, never()).setResult(Result.FAILURE);
        verify(build, never()).setResult(Result.UNSTABLE);
    }

    @Test
    void reportAndJudgeFailsTheBuildBelowThreshold(
            @TempDir final Path workspace,
            @TempDir final Path buildRoot) throws Exception {
        final SentinelTestSupport.LauncherStub stub = mockLauncher(0);
        writeReport(workspace, mutationsXml(1, 3, 0));
        final Run<?, ?> build = mockBuild(buildRoot);
        final ByteArrayOutputStream log = new ByteArrayOutputStream();

        reportAndJudge(stub, workspace, build, log,
                80.0, ThresholdAction.FAILURE);

        verify(build).setResult(Result.FAILURE);
        assertThat(log.toString(StandardCharsets.UTF_8))
                .contains("25.0% is below threshold 80.0% -> FAILURE");
    }

    @Test
    void reportAndJudgeMarksUnstableBelowThreshold(
            @TempDir final Path workspace,
            @TempDir final Path buildRoot) throws Exception {
        final SentinelTestSupport.LauncherStub stub = mockLauncher(0);
        writeReport(workspace, mutationsXml(1, 3, 0));
        final Run<?, ?> build = mockBuild(buildRoot);

        reportAndJudge(stub, workspace, build,
                new ByteArrayOutputStream(), 80.0,
                ThresholdAction.UNSTABLE);

        verify(build).setResult(Result.UNSTABLE);
    }

    @Test
    void reportAndJudgeLeavesTheBuildAloneAtOrAboveThreshold(
            @TempDir final Path workspace,
            @TempDir final Path buildRoot) throws Exception {
        final SentinelTestSupport.LauncherStub stub = mockLauncher(0);
        // 4 killed of 5 actionable = exactly 80.0
        writeReport(workspace, mutationsXml(4, 1, 0));
        final Run<?, ?> build = mockBuild(buildRoot);

        reportAndJudge(stub, workspace, build,
                new ByteArrayOutputStream(), 80.0,
                ThresholdAction.FAILURE);

        verify(build, never()).setResult(Result.FAILURE);
    }

    @Test
    void reportAndJudgeIgnoresThresholdWithoutAnAction(
            @TempDir final Path workspace,
            @TempDir final Path buildRoot) throws Exception {
        final SentinelTestSupport.LauncherStub stub = mockLauncher(0);
        writeReport(workspace, mutationsXml(0, 4, 0));
        final Run<?, ?> build = mockBuild(buildRoot);

        reportAndJudge(stub, workspace, build,
                new ByteArrayOutputStream(), 80.0, null);

        verify(build, never()).setResult(Result.FAILURE);
        verify(build, never()).setResult(Result.UNSTABLE);
    }

    @Test
    void reportAndJudgeWarnsWhenEveryMutantWasSkipped(
            @TempDir final Path workspace,
            @TempDir final Path buildRoot) throws Exception {
        final SentinelTestSupport.LauncherStub stub = mockLauncher(0);
        writeReport(workspace, mutationsXml(0, 0, 5));
        final Run<?, ?> build = mockBuild(buildRoot);
        final ByteArrayOutputStream log = new ByteArrayOutputStream();

        reportAndJudge(stub, workspace, build, log,
                80.0, ThresholdAction.FAILURE);

        // The gate still fires, but the log must say why the score is 0.
        verify(build).setResult(Result.FAILURE);
        assertThat(log.toString(StandardCharsets.UTF_8))
                .contains("all 5 mutants were skipped")
                .contains("not a test gap");
    }

    @Test
    void reportAndJudgeDoesNotWarnWhenSomethingWasEvaluated(
            @TempDir final Path workspace,
            @TempDir final Path buildRoot) throws Exception {
        final SentinelTestSupport.LauncherStub stub = mockLauncher(0);
        writeReport(workspace, mutationsXml(1, 1, 5));
        final Run<?, ?> build = mockBuild(buildRoot);
        final ByteArrayOutputStream log = new ByteArrayOutputStream();

        reportAndJudge(stub, workspace, build, log, null, null);

        assertThat(log.toString(StandardCharsets.UTF_8))
                .doesNotContain("mutants were skipped");
    }

    @Test
    void reportAndJudgeFailsClearlyWhenSentinelWroteNoResults(
            @TempDir final Path workspace,
            @TempDir final Path buildRoot) throws Exception {
        final SentinelTestSupport.LauncherStub stub = mockLauncher(0);
        // Output directory exists but holds no mutations.xml.
        new FilePath(workspace.toFile()).child(OUT_DIR).mkdirs();
        final Run<?, ?> build = mockBuild(buildRoot);

        assertThatThrownBy(() -> reportAndJudge(stub, workspace, build,
                new ByteArrayOutputStream(), null, null))
                .isInstanceOf(AbortException.class)
                .hasMessageContaining("produced no mutations.xml");
    }

    @Test
    void reportAndJudgeFailsWhenTheReportCommandFails(
            @TempDir final Path workspace,
            @TempDir final Path buildRoot) throws Exception {
        final SentinelTestSupport.LauncherStub stub = mockLauncher(1);
        writeReport(workspace, mutationsXml(1, 0, 0));
        final Run<?, ?> build = mockBuild(buildRoot);

        assertThatThrownBy(() -> reportAndJudge(stub, workspace, build,
                new ByteArrayOutputStream(), null, null))
                .isInstanceOf(AbortException.class)
                .hasMessageContaining("exited with code 1");
    }

    private static void reportAndJudge(
            final SentinelTestSupport.LauncherStub stub,
            final Path workspace,
            final Run<?, ?> build,
            final ByteArrayOutputStream log,
            final Double threshold,
            final ThresholdAction action) throws Exception {
        SentinelPostProcessor.reportAndJudge(
                SENTINEL_CMD, MERGED_WS, SRC_DIR, OUT_DIR,
                threshold, action, Map.of(),
                new FilePath(workspace.toFile()), stub.launcher(),
                loggingListener(log), build, new SentinelProcHandle());
    }

    /** Pretends sentinel wrote its report into the workspace output dir. */
    private static void writeReport(final Path workspace, final String xml)
            throws Exception {
        final Path out = workspace.resolve(OUT_DIR);
        Files.createDirectories(out);
        Files.writeString(out.resolve(SentinelEnvironment.MUTATIONS_XML),
                xml, StandardCharsets.UTF_8);
        Files.writeString(out.resolve(
                        SentinelEnvironment.HTML_REPORT_FILE),
                "<html>report</html>", StandardCharsets.UTF_8);
    }

    private static Run<?, ?> mockBuild(final Path buildRoot) {
        final Run<?, ?> build = mock(Run.class);
        when(build.getRootDir()).thenReturn(buildRoot.toFile());
        return build;
    }

    private static SentinelBuildAction attachedAction(
            final Run<?, ?> build) {
        final ArgumentCaptor<SentinelBuildAction> captor =
                ArgumentCaptor.forClass(SentinelBuildAction.class);
        verify(build).addAction(captor.capture());
        return captor.getValue();
    }
}
