/*
 * Copyright (c) 2026 LG Electronics, Inc. Licensed under the MIT License.
 * SPDX-License-Identifier: MIT
 */

package io.jenkins.plugins.sentinel;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import hudson.AbortException;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.sentinel.config.ThresholdAction;
import io.jenkins.plugins.sentinel.model.MutationScore;
import io.jenkins.plugins.sentinel.model.SentinelResult;

/**
 * Shared post-processing logic for sentinel pipeline steps:
 * merge partitions, generate reports, parse results, apply threshold.
 */

final class SentinelPostProcessor {

    private SentinelPostProcessor() {
    }

    /**
     * Generates partition workspace paths for N partitions.
     *
     * @param count number of partitions
     * @return list of paths like ".sentinel-1", ".sentinel-2", ...
     */
    static List<String> partitionPaths(final int count) {
        final List<String> paths = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            paths.add(SentinelEnvironment.partitionWorkspace(i));
        }
        return paths;
    }

    /**
     * Merges partition results into a single workspace.
     *
     * @param sentinelCmd     sentinel executable path
     * @param partitionPaths  partition workspace paths
     * @param targetWorkspace merge target workspace
     * @param env             environment variables
     * @param ws              working directory
     * @param launcher        Jenkins launcher
     * @param listener        task listener
     * @param procHandle      handle that receives the started process
     * @throws Exception if merge fails
     */
    static void merge(
            final String sentinelCmd,
            final List<String> partitionPaths,
            final String targetWorkspace,
            final Map<String, String> env,
            final FilePath ws,
            final Launcher launcher,
            final TaskListener listener,
            final SentinelProcHandle procHandle) throws Exception {
        SentinelRunner.run(
                sentinelCmd,
                SentinelCommandBuilder.buildMergeArgs(
                        partitionPaths, targetWorkspace),
                env, ws, launcher, listener, procHandle);
    }

    /**
     * Generates report, parses results, attaches build action,
     * and applies threshold judgment.
     *
     * @param sentinelCmd     sentinel executable path
     * @param workspace       sentinel workspace to report on
     * @param sourceDir       source code directory
     * @param outputDir       report output directory
     * @param threshold       score threshold (nullable)
     * @param thresholdAction action when below threshold (nullable)
     * @param env             environment variables
     * @param ws              working directory
     * @param launcher        Jenkins launcher
     * @param listener        task listener
     * @param build           Jenkins build run
     * @param procHandle      handle that receives the started process
     * @throws Exception if report generation or parsing fails
     */
    @SuppressWarnings("PMD.ExcessiveParameterList")
    static void reportAndJudge(
            final String sentinelCmd,
            final String workspace,
            final String sourceDir,
            final String outputDir,
            final Double threshold,
            final ThresholdAction thresholdAction,
            final Map<String, String> env,
            final FilePath ws,
            final Launcher launcher,
            final TaskListener listener,
            final Run<?, ?> build,
            final SentinelProcHandle procHandle) throws Exception {
        SentinelRunner.run(
                sentinelCmd,
                SentinelCommandBuilder.buildReportArgs(
                        workspace, sourceDir, outputDir),
                env, ws, launcher, listener, procHandle);

        final Path archiveDir = build.getRootDir().toPath()
                .resolve(SentinelEnvironment.ARCHIVE_DIR);
        final FilePath remoteOutput = ws.child(outputDir);
        final FilePath localArchive =
                new FilePath(archiveDir.toFile());
        remoteOutput.copyRecursiveTo(localArchive);

        final SentinelResult result = parseArchivedResult(archiveDir);

        final SentinelBuildAction action =
                new SentinelBuildAction(result);
        action.setRun(build);
        build.addAction(action);

        final MutationScore score = result.overallScore();
        listener.getLogger().printf(
                "[Sentinel] Score: %s%% "
                        + "(killed=%d, survived=%d, skipped=%d)%n",
                score.formattedScore(), score.killed(),
                score.survived(), score.skipped());
        warnIfNothingEvaluated(listener, score);

        applyThreshold(listener, build, score,
                threshold, thresholdAction);
    }

    /**
     * Reads the archived mutations.xml, failing with the path when sentinel
     * produced no result file rather than surfacing a bare
     * NoSuchFileException.
     */
    private static SentinelResult parseArchivedResult(final Path archiveDir)
            throws Exception {
        final Path xmlFile = archiveDir.resolve(
                SentinelEnvironment.MUTATIONS_XML);
        try (InputStream in = Files.newInputStream(xmlFile)) {
            return SentinelResultParser.parse(in);
        } catch (final NoSuchFileException e) {
            final AbortException error = new AbortException(
                    "sentinel produced no "
                            + SentinelEnvironment.MUTATIONS_XML + " ("
                            + xmlFile + "). The report step ran, but its"
                            + " output directory holds no results - check"
                            + " that the unstashed workspace actually"
                            + " contains a completed sentinel run.");
            error.initCause(e);
            throw error;
        }
    }

    /**
     * Warns when every mutant was skipped, so a resulting threshold
     * failure is not misread as a test gap.
     *
     * <p>{@link MutationScore#score()} is 0.0 when nothing was evaluated,
     * which trips any threshold. The cause is a broken build, a timeout,
     * or a runtime error - not tests that failed to kill mutants - and the
     * log has to say so or the user chases the wrong problem.</p>
     */
    private static void warnIfNothingEvaluated(
            final TaskListener listener, final MutationScore score) {
        if (score.total() == 0 && score.skipped() > 0) {
            listener.getLogger().printf(
                    "[Sentinel] WARNING: all %d mutants were skipped, so no"
                            + " mutant was actually evaluated. The score is"
                            + " 0.0%% by definition here, not a test gap -"
                            + " check the build/test commands for failures,"
                            + " timeouts, or runtime errors.%n",
                    score.skipped());
        }
    }

    private static void applyThreshold(
            final TaskListener listener,
            final Run<?, ?> build,
            final MutationScore score,
            final Double threshold,
            final ThresholdAction thresholdAction) {
        if (threshold == null || thresholdAction == null) {
            return;
        }
        if (score.score() < threshold) {
            listener.getLogger().printf(
                    "[Sentinel] Score %s%% is below "
                            + "threshold %.1f%% -> %s%n",
                    score.formattedScore(), threshold, thresholdAction);
            switch (thresholdAction) {
                case FAILURE ->
                        build.setResult(Result.FAILURE);
                case UNSTABLE ->
                        build.setResult(Result.UNSTABLE);
                default ->
                        throw new IllegalStateException(
                                "Unexpected action: "
                                        + thresholdAction);
            }
        }
    }
}
