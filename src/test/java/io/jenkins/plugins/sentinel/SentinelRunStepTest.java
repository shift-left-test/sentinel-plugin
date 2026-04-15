/*
 * Copyright (c) 2026 LG Electronics, Inc. Licensed under the MIT License.
 * SPDX-License-Identifier: MIT
 */

package io.jenkins.plugins.sentinel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.sentinel.config.SentinelConfiguration;
import org.junit.jupiter.api.Test;

class SentinelRunStepTest {

    private static final String BUILD_CMD = "make all";
    private static final String TEST_CMD = "make test";
    private static final String ENV_PARTITION_TOTAL =
            "SENTINEL_PARTITION_TOTAL";
    private static final String TEST_RESULT = "test-results/";
    private static final long SEED_VALUE = 12_345L;

    @Test
    void noRequiredConstructorParams() {
        final SentinelRunStep step = new SentinelRunStep();
        assertThat(step.getBuildCommand()).isNull();
        assertThat(step.getTestCommand()).isNull();
        assertThat(step.getTestResultDir()).isNull();
        assertThat(step.getPartitionIndex()).isNull();
        assertThat(step.getSeed()).isNull();
        assertThat(step.isVerbose()).isNull();
        assertThat(step.getWorkspace()).isNull();
        assertThat(step.getSourceDir()).isNull();
        assertThat(step.getSentinelPath()).isNull();
    }

    @Test
    void partitionIndexSetter() {
        final SentinelRunStep step = new SentinelRunStep();
        step.setPartitionIndex(3);
        assertThat(step.getPartitionIndex()).isEqualTo(3);
    }

    @Test
    void allStepParamsOverrideEnvVars() {
        final SentinelRunStep step = new SentinelRunStep();
        step.setBuildCommand("step-build");
        step.setTestCommand("step-test");
        step.setTestResultDir("step-results/");
        step.setSourceDir("step-src");
        step.setSeed(99L);
        step.setVerbose(true);
        step.setWorkspace(".my-ws");
        step.setSentinelPath("/opt/sentinel");

        final Map<String, String> env = new HashMap<>();
        env.put("SENTINEL_BUILD_COMMAND", "env-build");
        env.put("SENTINEL_TEST_COMMAND", "env-test");
        env.put("SENTINEL_TEST_RESULT_DIR", "env-results/");
        env.put("SENTINEL_SOURCE_DIR", "env-src");
        env.put("SENTINEL_SEED", "111");
        env.put("SENTINEL_VERBOSE", "false");
        env.put("SENTINEL_WORKSPACE", ".env-ws");
        env.put("SENTINEL_PATH", "/usr/bin/sentinel");

        final SentinelConfiguration config =
                step.toConfiguration(env);
        assertThat(config.getBuildCommand())
                .isEqualTo("step-build");
        assertThat(config.getTestCommand())
                .isEqualTo("step-test");
        assertThat(config.getTestResultDir())
                .isEqualTo("step-results/");
        assertThat(config.getSourceDir())
                .isEqualTo("step-src");
        assertThat(config.getSeed()).isEqualTo(99L);
        assertThat(config.isVerbose()).isTrue();
        assertThat(config.getWorkspace()).isEqualTo(".my-ws");
        assertThat(config.getSentinelPath())
                .isEqualTo("/opt/sentinel");
    }

    @Test
    void toConfigurationReadsFromEnvVars() {
        final SentinelRunStep step = new SentinelRunStep();

        final Map<String, String> env = new HashMap<>();
        env.put("SENTINEL_BUILD_COMMAND", BUILD_CMD);
        env.put("SENTINEL_TEST_COMMAND", TEST_CMD);
        env.put("SENTINEL_TEST_RESULT_DIR", TEST_RESULT);
        env.put("SENTINEL_SOURCE_DIR", "src");
        env.put("SENTINEL_SEED", String.valueOf(SEED_VALUE));
        env.put(ENV_PARTITION_TOTAL, "4");

        final SentinelConfiguration config =
                step.toConfiguration(env);
        assertThat(config.getBuildCommand()).isEqualTo(BUILD_CMD);
        assertThat(config.getTestCommand()).isEqualTo(TEST_CMD);
        assertThat(config.getTestResultDir())
                .isEqualTo(TEST_RESULT);
        assertThat(config.getSourceDir()).isEqualTo("src");
        assertThat(config.getSeed()).isEqualTo(SEED_VALUE);
        assertThat(config.getPartitionTotal()).isEqualTo(4);
    }

    @Test
    void toConfigurationStepParamsOverrideEnvVars() {
        final SentinelRunStep step = new SentinelRunStep();
        step.setBuildCommand("override-build");
        step.setSeed(99L);

        final Map<String, String> env = new HashMap<>();
        env.put("SENTINEL_BUILD_COMMAND", BUILD_CMD);
        env.put("SENTINEL_SEED", String.valueOf(SEED_VALUE));

        final SentinelConfiguration config =
                step.toConfiguration(env);
        assertThat(config.getBuildCommand())
                .isEqualTo("override-build");
        assertThat(config.getSeed()).isEqualTo(99L);
    }

    @Test
    void partitionIndexAutoAssignsWorkspace() {
        final SentinelRunStep step = new SentinelRunStep();
        step.setPartitionIndex(3);

        final Map<String, String> env = new HashMap<>();
        env.put(ENV_PARTITION_TOTAL, "4");

        final SentinelConfiguration config =
                step.toConfiguration(env);
        assertThat(config.getWorkspace()).isEqualTo(".sentinel-3");
        assertThat(config.getPartitionIndex()).isEqualTo(3);
        assertThat(config.getPartitionTotal()).isEqualTo(4);
        assertThat(config.getPartitionSpec()).isEqualTo("3/4");
    }

    @Test
    void partitionIndexFromStepOverridesDefault() {
        final SentinelRunStep step = new SentinelRunStep();
        step.setPartitionIndex(1);

        final Map<String, String> env = new HashMap<>();
        env.put(ENV_PARTITION_TOTAL, "4");

        final SentinelConfiguration config =
                step.toConfiguration(env);
        assertThat(config.getPartitionIndex()).isEqualTo(1);
        assertThat(config.getPartitionSpec()).isEqualTo("1/4");
        assertThat(config.getWorkspace()).isEqualTo(".sentinel-1");
    }

    @Test
    void explicitWorkspaceOverridesAutoAssignment() {
        final SentinelRunStep step = new SentinelRunStep();
        step.setPartitionIndex(2);
        step.setWorkspace(".custom-ws");

        final Map<String, String> env = new HashMap<>();
        env.put(ENV_PARTITION_TOTAL, "4");

        final SentinelConfiguration config =
                step.toConfiguration(env);
        assertThat(config.getWorkspace()).isEqualTo(".custom-ws");
    }

    @Test
    void toConfigurationWithoutEnvVarsLeavesFieldsNull() {
        final SentinelRunStep step = new SentinelRunStep();

        final SentinelConfiguration config =
                step.toConfiguration(Map.of());
        assertThat(config.getBuildCommand()).isNull();
        assertThat(config.getTestCommand()).isNull();
        assertThat(config.getTestResultDir()).isNull();
        assertThat(config.getPartitionIndex()).isNull();
        assertThat(config.getSeed()).isNull();
        assertThat(config.getWorkspace()).isNull();
    }

    @Test
    void descriptorFunctionName() {
        final SentinelRunStep.DescriptorImpl descriptor =
                new SentinelRunStep.DescriptorImpl();
        assertThat(descriptor.getFunctionName())
                .isEqualTo("sentinelRun");
    }

    @Test
    void descriptorDisplayName() {
        final SentinelRunStep.DescriptorImpl descriptor =
                new SentinelRunStep.DescriptorImpl();
        assertThat(descriptor.getDisplayName()).isNotBlank();
    }

    @Test
    void descriptorRequiresCorrectContext() {
        final SentinelRunStep.DescriptorImpl descriptor =
                new SentinelRunStep.DescriptorImpl();
        assertThat(descriptor.getRequiredContext())
                .isEqualTo(Set.of(
                        FilePath.class,
                        Launcher.class,
                        TaskListener.class,
                        EnvVars.class,
                        Run.class));
    }
}
