/*
 * Copyright (c) 2026 LG Electronics, Inc. Licensed under the MIT License.
 * SPDX-License-Identifier: MIT
 */

package io.jenkins.plugins.sentinel;

import java.util.ArrayList;
import java.util.List;

import io.jenkins.plugins.sentinel.config.SentinelConfiguration;

/**
 * Builds sentinel CLI argument lists from configuration.
 * Never includes --threshold (plugin handles threshold judgment).
 */

public final class SentinelCommandBuilder {

    private SentinelCommandBuilder() {
    }

    /**
     * Builds arguments for a sentinel run (partition execution).
     *
     * @param config the sentinel configuration
     * @return list of CLI arguments
     */
    public static List<String> buildRunArgs(
            final SentinelConfiguration config) {
        final List<String> args = new ArrayList<>();

        // Build/test fields
        addIfPresent(args, "--build-command",
                config.getBuildCommand());
        addIfPresent(args, "--test-command",
                config.getTestCommand());
        addIfPresent(args, "--test-result-dir",
                config.getTestResultDir());

        // Optional string fields
        addIfPresent(args, "--source-dir", config.getSourceDir());
        addIfPresent(args, "--compiledb-dir", config.getCompileDbDir());
        addIfPresent(args, "--from", config.getFrom());
        addIfPresent(args, "--generator", config.getGenerator());
        addIfPresent(args, "--config", config.getConfig());
        addIfPresent(args, "--workspace", config.getWorkspace());
        addIfPresent(args, "--output-dir", config.getOutputDir());
        addIfPresent(args, "--partition", config.getPartitionSpec());

        // Optional integer fields
        addIfPresent(args, "--timeout", config.getTimeout());
        addIfPresent(args, "--mutants-per-line",
                config.getMutantsPerLine());
        addIfPresent(args, "--limit", config.getLimit());

        // Optional long fields
        addIfPresent(args, "--seed", config.getSeed());

        // Boolean flags
        if (config.isClean()) {
            args.add("--clean");
        }
        if (config.isVerbose()) {
            args.add("--verbose");
        }
        if (config.isDryRun()) {
            args.add("--dry-run");
        }
        if (config.isUncommitted()) {
            args.add("--uncommitted");
        }

        // List fields (repeated options)
        addRepeated(args, "--pattern", config.getPatterns());
        addRepeated(args, "--extension", config.getExtensions());
        addRepeated(args, "--operator", config.getOperators());
        addRepeated(args, "--lcov-tracefile",
                config.getLcovTracefiles());

        return args;
    }

    /**
     * Builds arguments for sentinel --merge-partition.
     *
     * @param partitionPaths  list of partition workspace paths to merge
     * @param targetWorkspace destination workspace for merged result
     * @return list of CLI arguments
     */
    public static List<String> buildMergeArgs(
            final List<String> partitionPaths,
            final String targetWorkspace) {
        final List<String> args = new ArrayList<>();
        for (final String path : partitionPaths) {
            args.add("--merge-partition=" + path);
        }
        args.add("--workspace=" + targetWorkspace);
        return args;
    }

    /**
     * Builds arguments for sentinel report generation
     * (--workspace + --source-dir + --output-dir).
     *
     * @param workspace  sentinel workspace directory
     * @param sourceDir  source code directory
     * @param outputDir  output directory for generated report
     * @return list of CLI arguments
     */
    public static List<String> buildReportArgs(
            final String workspace,
            final String sourceDir,
            final String outputDir) {
        final List<String> args = new ArrayList<>();
        args.add("--workspace=" + workspace);
        args.add("--source-dir=" + sourceDir);
        args.add("--output-dir=" + outputDir);
        return args;
    }

    private static void addIfPresent(
            final List<String> args,
            final String flag,
            final String value) {
        if (value != null && !value.isEmpty()) {
            args.add(flag + "=" + value);
        }
    }

    private static void addIfPresent(
            final List<String> args,
            final String flag,
            final Integer value) {
        if (value != null) {
            args.add(flag + "=" + value);
        }
    }

    private static void addIfPresent(
            final List<String> args,
            final String flag,
            final Long value) {
        if (value != null) {
            args.add(flag + "=" + value);
        }
    }

    private static void addRepeated(
            final List<String> args,
            final String flag,
            final List<String> values) {
        if (values != null) {
            for (final String value : values) {
                args.add(flag + "=" + value);
            }
        }
    }
}
