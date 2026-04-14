/*
 * Copyright (c) 2026 LG Electronics, Inc. Licensed under the MIT License.
 * SPDX-License-Identifier: MIT
 */

package io.jenkins.plugins.sentinel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import io.jenkins.plugins.sentinel.config.SentinelConfiguration;
import org.junit.jupiter.api.Test;

class SentinelCommandBuilderTest {

    @Test
    void minimalConfigBuildsCorrectCommand() {
        final SentinelConfiguration config = minimalConfig();
        final List<String> args = SentinelCommandBuilder.buildRunArgs(config);
        assertThat(args).contains(
                "--build-command=make all",
                "--test-command=make test",
                "--test-result-dir=test-results/");
    }

    @Test
    void includesSourceDir() {
        final SentinelConfiguration config = minimalConfig();
        config.setSourceDir("src");
        final List<String> args = SentinelCommandBuilder.buildRunArgs(config);
        assertThat(args).contains("--source-dir=src");
    }

    @Test
    void includesPartition() {
        final SentinelConfiguration config = minimalConfig();
        config.setPartition("2/4");
        final List<String> args = SentinelCommandBuilder.buildRunArgs(config);
        assertThat(args).contains("--partition=2/4");
    }

    @Test
    void includesSeed() {
        final SentinelConfiguration config = minimalConfig();
        config.setSeed(12_345L);
        final List<String> args = SentinelCommandBuilder.buildRunArgs(config);
        assertThat(args).contains("--seed=12345");
    }

    @Test
    void includesBooleanFlags() {
        final SentinelConfiguration config = minimalConfig();
        config.setClean(true);
        config.setVerbose(true);
        config.setDryRun(true);
        config.setUncommitted(true);
        final List<String> args = SentinelCommandBuilder.buildRunArgs(config);
        assertThat(args).contains(
                "--clean", "--verbose",
                "--dry-run", "--uncommitted");
    }

    @Test
    void includesListOptions() {
        final SentinelConfiguration config = minimalConfig();
        config.setPatterns(List.of("src/**", "!src/gen/**"));
        config.setOperators(List.of("AOR", "ROR"));
        config.setLcovTracefiles(List.of("cov1.info", "cov2.info"));
        final List<String> args = SentinelCommandBuilder.buildRunArgs(config);
        assertThat(args).contains(
                "--pattern=src/**",
                "--pattern=!src/gen/**",
                "--operator=AOR",
                "--operator=ROR",
                "--lcov-tracefile=cov1.info",
                "--lcov-tracefile=cov2.info");
    }

    @Test
    void neverIncludesThreshold() {
        final SentinelConfiguration config = minimalConfig();
        config.setThreshold(80.0);
        final List<String> args = SentinelCommandBuilder.buildRunArgs(config);
        assertThat(args).noneMatch(arg -> arg.contains("--threshold"));
    }

    @Test
    void omitsNullOptionalFields() {
        final SentinelConfiguration config = minimalConfig();
        final List<String> args = SentinelCommandBuilder.buildRunArgs(config);
        assertThat(args).noneMatch(arg -> arg.contains("--source-dir"));
        assertThat(args).noneMatch(arg -> arg.contains("--from"));
        assertThat(args).noneMatch(arg -> arg.contains("--seed"));
        assertThat(args).noneMatch(arg -> arg.contains("--timeout"));
    }

    @Test
    void buildsMergeArgs() {
        final List<String> partitions = List.of(
                ".sentinel-1", ".sentinel-2");
        final List<String> args = SentinelCommandBuilder.buildMergeArgs(
                partitions, ".sentinel-merged");
        assertThat(args).containsExactly(
                "--merge-partition=.sentinel-1",
                "--merge-partition=.sentinel-2",
                "--workspace=.sentinel-merged");
    }

    @Test
    void buildsReportArgs() {
        final List<String> args = SentinelCommandBuilder.buildReportArgs(
                ".sentinel-merged", ".", "sentinel-report");
        assertThat(args).containsExactly(
                "--workspace=.sentinel-merged",
                "--source-dir=.",
                "--output-dir=sentinel-report");
    }

    private SentinelConfiguration minimalConfig() {
        final SentinelConfiguration config = new SentinelConfiguration();
        config.setBuildCommand("make all");
        config.setTestCommand("make test");
        config.setTestResultDir("test-results/");
        return config;
    }
}
