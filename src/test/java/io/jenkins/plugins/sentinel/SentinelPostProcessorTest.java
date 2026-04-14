/*
 * Copyright (c) 2026 LG Electronics, Inc. Licensed under the MIT License.
 * SPDX-License-Identifier: MIT
 */

package io.jenkins.plugins.sentinel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class SentinelPostProcessorTest {

    private static final String SENTINEL_CMD = "sentinel";

    @Test
    void buildsMergeCommandForMultiplePartitions() {
        final List<String> args = SentinelPostProcessor.buildMergeCommand(
                SENTINEL_CMD,
                List.of(".sentinel-1", ".sentinel-2", ".sentinel-3"),
                ".sentinel-merged");

        assertThat(args).containsExactly(
                SENTINEL_CMD,
                "--merge-partition=.sentinel-1",
                "--merge-partition=.sentinel-2",
                "--merge-partition=.sentinel-3",
                "--workspace=.sentinel-merged");
    }

    @Test
    void buildsReportCommand() {
        final List<String> args = SentinelPostProcessor.buildReportCommand(
                SENTINEL_CMD, ".sentinel-merged", ".", "sentinel-report");

        assertThat(args).containsExactly(
                SENTINEL_CMD,
                "--workspace=.sentinel-merged",
                "--source-dir=.",
                "--output-dir=sentinel-report");
    }

    @Test
    void partitionPathsGeneratesCorrectPaths() {
        final List<String> paths = SentinelPostProcessor.partitionPaths(4);

        assertThat(paths).containsExactly(
                ".sentinel-1", ".sentinel-2",
                ".sentinel-3", ".sentinel-4");
    }

    @Test
    void partitionPathsSinglePartition() {
        final List<String> paths = SentinelPostProcessor.partitionPaths(1);

        assertThat(paths).containsExactly(".sentinel-1");
    }
}
