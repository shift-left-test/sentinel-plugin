/*
 * Copyright (c) 2026 LG Electronics, Inc. Licensed under the MIT License.
 * SPDX-License-Identifier: MIT
 */

package io.jenkins.plugins.sentinel;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.sentinel.config.ThresholdAction;
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
     * Builds the full merge command including the sentinel executable.
     *
     * @param sentinelCmd     path to sentinel executable
     * @param partitionPaths  list of partition workspace paths
     * @param targetWorkspace destination workspace for merged result
     * @return full argument list
     */
    static List<String> buildMergeCommand(
            final String sentinelCmd,
            final List<String> partitionPaths,
            final String targetWorkspace) {
        final List<String> args = new ArrayList<>();
        args.add(sentinelCmd);
        args.addAll(SentinelCommandBuilder.buildMergeArgs(
                partitionPaths, targetWorkspace));
        return args;
    }

    /**
     * Builds the full report command including the sentinel executable.
     *
     * @param sentinelCmd sentinel executable path
     * @param workspace   sentinel workspace directory
     * @param sourceDir   source code directory
     * @param outputDir   output directory for reports
     * @return full argument list
     */
    static List<String> buildReportCommand(
            final String sentinelCmd,
            final String workspace,
            final String sourceDir,
            final String outputDir) {
        final List<String> args = new ArrayList<>();
        args.add(sentinelCmd);
        args.addAll(SentinelCommandBuilder.buildReportArgs(
                workspace, sourceDir, outputDir));
        return args;
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
     * @throws Exception if merge fails
     */
    static void merge(
            final String sentinelCmd,
            final List<String> partitionPaths,
            final String targetWorkspace,
            final Map<String, String> env,
            final FilePath ws,
            final Launcher launcher,
            final TaskListener listener) throws Exception {
        final List<String> args = buildMergeCommand(
                sentinelCmd, partitionPaths, targetWorkspace);
        SentinelRunner.run(args, env, ws, launcher, listener);
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
            final Run<?, ?> build) throws Exception {
        final List<String> reportArgs = buildReportCommand(
                sentinelCmd, workspace, sourceDir, outputDir);
        SentinelRunner.run(reportArgs, env, ws, launcher, listener);

        final FilePath xmlFile =
                ws.child(outputDir).child("mutations.xml");
        final SentinelResult result;
        try (InputStream in = xmlFile.read()) {
            result = SentinelResultParser.parse(in);
        }

        final SentinelBuildAction action =
                new SentinelBuildAction(result);
        action.setRun(build);
        build.addAction(action);

        listener.getLogger().printf(
                "[Sentinel] Score: %.1f%% "
                        + "(killed=%d, survived=%d, "
                        + "skipped=%d)%n",
                result.overallScore().score(),
                result.overallScore().killed(),
                result.overallScore().survived(),
                result.overallScore().skipped());

        applyThreshold(listener, build, result,
                threshold, thresholdAction);
    }

    private static void applyThreshold(
            final TaskListener listener,
            final Run<?, ?> build,
            final SentinelResult result,
            final Double threshold,
            final ThresholdAction thresholdAction) {
        if (threshold == null || thresholdAction == null) {
            return;
        }
        final double score = result.overallScore().score();
        if (score < threshold) {
            listener.getLogger().printf(
                    "[Sentinel] Score %.1f%% is below "
                            + "threshold %.1f%% -> %s%n",
                    score, threshold, thresholdAction);
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
