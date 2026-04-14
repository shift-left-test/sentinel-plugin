/*
 * Copyright (c) 2026 LG Electronics, Inc. Licensed under the MIT License.
 * SPDX-License-Identifier: MIT
 */

package io.jenkins.plugins.sentinel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import io.jenkins.plugins.sentinel.config.SentinelConfiguration;
import org.junit.jupiter.api.Test;

class SentinelRunStepTest {

    private static final String BUILD_CMD = "make all";
    private static final String TEST_CMD = "make test";
    private static final String TEST_RESULT = "test-results/";
    private static final String ENV_INDEX =
            "SENTINEL_PARTITION_INDEX";
    private static final String ENV_TOTAL =
            "SENTINEL_PARTITION_TOTAL";
    private static final String ENV_SEED = "SENTINEL_SEED";
    private static final long SEED_VALUE = 12_345L;

    @Test
    void defaultValuesAreSet() {
        final SentinelRunStep step = new SentinelRunStep(
                BUILD_CMD, TEST_CMD, TEST_RESULT);
        assertThat(step.getBuildCommand()).isEqualTo(BUILD_CMD);
        assertThat(step.getTestCommand()).isEqualTo(TEST_CMD);
        assertThat(step.getTestResultDir()).isEqualTo(TEST_RESULT);
        assertThat(step.getPartition()).isNull();
        assertThat(step.getSeed()).isNull();
        assertThat(step.isVerbose()).isFalse();
        assertThat(step.isClean()).isFalse();
    }

    @Test
    void settersUpdateValues() {
        final SentinelRunStep step = new SentinelRunStep(
                BUILD_CMD, TEST_CMD, TEST_RESULT);
        step.setPartition("2/4");
        step.setSeed(12_345L);
        step.setVerbose(true);
        step.setSourceDir("src");
        step.setWorkspace(".sentinel-2");
        assertThat(step.getPartition()).isEqualTo("2/4");
        assertThat(step.getSeed()).isEqualTo(12_345L);
        assertThat(step.isVerbose()).isTrue();
        assertThat(step.getSourceDir()).isEqualTo("src");
        assertThat(step.getWorkspace()).isEqualTo(".sentinel-2");
    }

    @Test
    void toConfigurationMapsAllFields() {
        final SentinelRunStep step = new SentinelRunStep(
                BUILD_CMD, TEST_CMD, TEST_RESULT);
        step.setPartition("1/4");
        step.setSeed(99L);
        step.setFrom("HEAD~1");
        step.setVerbose(true);

        final SentinelConfiguration config = step.toConfiguration();
        assertThat(config.getBuildCommand()).isEqualTo(BUILD_CMD);
        assertThat(config.getTestCommand()).isEqualTo(TEST_CMD);
        assertThat(config.getTestResultDir()).isEqualTo(TEST_RESULT);
        assertThat(config.getPartition()).isEqualTo("1/4");
        assertThat(config.getSeed()).isEqualTo(99L);
        assertThat(config.getFrom()).isEqualTo("HEAD~1");
        assertThat(config.isVerbose()).isTrue();
    }

    @Test
    void toConfigurationAutoDetectsPartitionFromEnvVars() {
        final SentinelRunStep step = new SentinelRunStep(
                BUILD_CMD, TEST_CMD, TEST_RESULT);

        final Map<String, String> env = Map.of(
                ENV_INDEX, "2",
                ENV_TOTAL, "4");

        final SentinelConfiguration config =
                step.toConfiguration(env);
        assertThat(config.getPartition()).isEqualTo("2/4");
    }

    @Test
    void toConfigurationAutoDetectsWorkspaceFromEnvVars() {
        final SentinelRunStep step = new SentinelRunStep(
                BUILD_CMD, TEST_CMD, TEST_RESULT);

        final Map<String, String> env = Map.of(
                ENV_INDEX, "3",
                ENV_TOTAL, "4");

        final SentinelConfiguration config =
                step.toConfiguration(env);
        assertThat(config.getWorkspace()).isEqualTo(".sentinel-3");
    }

    @Test
    void toConfigurationExplicitPartitionOverridesEnvVars() {
        final SentinelRunStep step = new SentinelRunStep(
                BUILD_CMD, TEST_CMD, TEST_RESULT);
        step.setPartition("1/2");
        step.setWorkspace(".my-workspace");

        final Map<String, String> env = Map.of(
                ENV_INDEX, "3",
                ENV_TOTAL, "4");

        final SentinelConfiguration config =
                step.toConfiguration(env);
        assertThat(config.getPartition()).isEqualTo("1/2");
        assertThat(config.getWorkspace()).isEqualTo(".my-workspace");
    }

    @Test
    void toConfigurationNoEnvVarsLeavesPartitionNull() {
        final SentinelRunStep step = new SentinelRunStep(
                BUILD_CMD, TEST_CMD, TEST_RESULT);

        final Map<String, String> env = Map.of();

        final SentinelConfiguration config =
                step.toConfiguration(env);
        assertThat(config.getPartition()).isNull();
    }

    @Test
    void toConfigurationAutoDetectsSeedFromEnvVar() {
        final SentinelRunStep step = new SentinelRunStep(
                BUILD_CMD, TEST_CMD, TEST_RESULT);

        final Map<String, String> env = Map.of(
                ENV_INDEX, "1",
                ENV_TOTAL, "2",
                ENV_SEED, String.valueOf(SEED_VALUE));

        final SentinelConfiguration config =
                step.toConfiguration(env);
        assertThat(config.getSeed()).isEqualTo(SEED_VALUE);
    }

    @Test
    void toConfigurationExplicitSeedOverridesEnvVar() {
        final SentinelRunStep step = new SentinelRunStep(
                BUILD_CMD, TEST_CMD, TEST_RESULT);
        step.setSeed(99L);

        final Map<String, String> env = Map.of(
                ENV_SEED, String.valueOf(SEED_VALUE));

        final SentinelConfiguration config =
                step.toConfiguration(env);
        assertThat(config.getSeed()).isEqualTo(99L);
    }
}
