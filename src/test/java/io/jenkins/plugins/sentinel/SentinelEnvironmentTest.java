/*
 * Copyright (c) 2026 LG Electronics, Inc. Licensed under the MIT License.
 * SPDX-License-Identifier: MIT
 */

package io.jenkins.plugins.sentinel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import io.jenkins.plugins.sentinel.config.SentinelConfiguration;
import org.junit.jupiter.api.Test;

class SentinelEnvironmentTest {

    private static final String TRUE_STR = "true";
    private static final long SEED_VALUE = 12_345L;

    @Test
    void readsRequiredFieldsFromEnv() {
        final Map<String, String> env = Map.of(
                "SENTINEL_BUILD_COMMAND", "make all",
                "SENTINEL_TEST_COMMAND", "make test",
                "SENTINEL_TEST_RESULT_DIR", "results/");

        final SentinelConfiguration config =
                SentinelEnvironment.toConfiguration(env);

        assertThat(config.getBuildCommand()).isEqualTo("make all");
        assertThat(config.getTestCommand()).isEqualTo("make test");
        assertThat(config.getTestResultDir()).isEqualTo("results/");
    }

    @Test
    void readsOptionalStringFieldsFromEnv() {
        final Map<String, String> env = new HashMap<>(requiredEnv());
        env.put("SENTINEL_SOURCE_DIR", "src");
        env.put("SENTINEL_COMPILE_DB_DIR", "build");
        env.put("SENTINEL_FROM", "HEAD~1");
        env.put("SENTINEL_GENERATOR", "random");
        env.put("SENTINEL_CONFIG", ".sentinel.yml");
        env.put("SENTINEL_WORKSPACE", ".sentinel-1");
        env.put("SENTINEL_OUTPUT_DIR", "report");
        env.put("SENTINEL_PATH", "/usr/bin/sentinel");

        final SentinelConfiguration config =
                SentinelEnvironment.toConfiguration(env);

        assertThat(config.getSourceDir()).isEqualTo("src");
        assertThat(config.getCompileDbDir()).isEqualTo("build");
        assertThat(config.getFrom()).isEqualTo("HEAD~1");
        assertThat(config.getGenerator()).isEqualTo("random");
        assertThat(config.getConfig()).isEqualTo(".sentinel.yml");
        assertThat(config.getWorkspace()).isEqualTo(".sentinel-1");
        assertThat(config.getOutputDir()).isEqualTo("report");
        assertThat(config.getSentinelPath())
                .isEqualTo("/usr/bin/sentinel");
    }

    @Test
    void readsIntegerFieldsFromEnv() {
        final Map<String, String> env = new HashMap<>(requiredEnv());
        env.put("SENTINEL_TIMEOUT", "300");
        env.put("SENTINEL_MUTANTS_PER_LINE", "5");
        env.put("SENTINEL_LIMIT", "1000");

        final SentinelConfiguration config =
                SentinelEnvironment.toConfiguration(env);

        assertThat(config.getTimeout()).isEqualTo(300);
        assertThat(config.getMutantsPerLine()).isEqualTo(5);
        assertThat(config.getLimit()).isEqualTo(1000);
    }

    @Test
    void readsSeedFromEnv() {
        final Map<String, String> env = new HashMap<>(requiredEnv());
        env.put("SENTINEL_SEED", String.valueOf(SEED_VALUE));

        final SentinelConfiguration config =
                SentinelEnvironment.toConfiguration(env);

        assertThat(config.getSeed()).isEqualTo(SEED_VALUE);
    }

    @Test
    void readsBooleanFlagsFromEnv() {
        final Map<String, String> env = new HashMap<>(requiredEnv());
        env.put("SENTINEL_UNCOMMITTED", TRUE_STR);
        env.put("SENTINEL_CLEAN", TRUE_STR);
        env.put("SENTINEL_DRY_RUN", TRUE_STR);
        env.put("SENTINEL_VERBOSE", TRUE_STR);

        final SentinelConfiguration config =
                SentinelEnvironment.toConfiguration(env);

        assertThat(config.isUncommitted()).isTrue();
        assertThat(config.isClean()).isTrue();
        assertThat(config.isDryRun()).isTrue();
        assertThat(config.isVerbose()).isTrue();
    }

    @Test
    void booleanFlagsDefaultToFalse() {
        final SentinelConfiguration config =
                SentinelEnvironment.toConfiguration(requiredEnv());

        assertThat(config.isUncommitted()).isFalse();
        assertThat(config.isClean()).isFalse();
        assertThat(config.isDryRun()).isFalse();
        assertThat(config.isVerbose()).isFalse();
    }

    @Test
    void readsCommaSeparatedListsFromEnv() {
        final Map<String, String> env = new HashMap<>(requiredEnv());
        env.put("SENTINEL_PATTERNS", "src/**,!src/gen/**");
        env.put("SENTINEL_EXTENSIONS", ".c,.h");
        env.put("SENTINEL_OPERATORS", "AOR,ROR,LCR");
        env.put("SENTINEL_LCOV_TRACEFILES", "cov1.info,cov2.info");

        final SentinelConfiguration config =
                SentinelEnvironment.toConfiguration(env);

        assertThat(config.getPatterns()).containsExactly(
                "src/**", "!src/gen/**");
        assertThat(config.getExtensions()).containsExactly(".c", ".h");
        assertThat(config.getOperators()).containsExactly(
                "AOR", "ROR", "LCR");
        assertThat(config.getLcovTracefiles()).containsExactly(
                "cov1.info", "cov2.info");
    }

    @Test
    void readsPartitionTotalFromEnv() {
        final Map<String, String> env = new HashMap<>(requiredEnv());
        env.put("SENTINEL_PARTITION_TOTAL", "4");

        final SentinelConfiguration config =
                SentinelEnvironment.toConfiguration(env);

        assertThat(config.getPartitionTotal()).isEqualTo(4);
    }

    @Test
    void missingEnvVarsLeaveFieldsNull() {
        final SentinelConfiguration config =
                SentinelEnvironment.toConfiguration(requiredEnv());

        assertThat(config.getSourceDir()).isNull();
        assertThat(config.getTimeout()).isNull();
        assertThat(config.getSeed()).isNull();
        assertThat(config.getPartitionTotal()).isNull();
    }

    @Test
    void stashNameGeneratesCorrectFormat() {
        assertThat(SentinelEnvironment.stashName(1))
                .isEqualTo("sentinel-partition-1");
        assertThat(SentinelEnvironment.stashName(4))
                .isEqualTo("sentinel-partition-4");
    }

    @Test
    void partitionWorkspaceGeneratesCorrectFormat() {
        assertThat(SentinelEnvironment.partitionWorkspace(1))
                .isEqualTo(".sentinel-1");
        assertThat(SentinelEnvironment.partitionWorkspace(4))
                .isEqualTo(".sentinel-4");
    }

    @Test
    void parseBooleanIsCaseInsensitive() {
        final Map<String, String> env = new HashMap<>(requiredEnv());
        env.put("SENTINEL_VERBOSE", "TRUE");
        env.put("SENTINEL_CLEAN", "True");

        final SentinelConfiguration config =
                SentinelEnvironment.toConfiguration(env);

        assertThat(config.isVerbose()).isTrue();
        assertThat(config.isClean()).isTrue();
    }

    @Test
    void emptyEnvVarLeavesListEmpty() {
        final Map<String, String> env = new HashMap<>(requiredEnv());
        env.put("SENTINEL_PATTERNS", "");

        final SentinelConfiguration config =
                SentinelEnvironment.toConfiguration(env);

        assertThat(config.getPatterns()).isEmpty();
    }

    @Test
    void singleItemList() {
        final Map<String, String> env = new HashMap<>(requiredEnv());
        env.put("SENTINEL_OPERATORS", "AOR");

        final SentinelConfiguration config =
                SentinelEnvironment.toConfiguration(env);

        assertThat(config.getOperators()).containsExactly("AOR");
    }

    @Test
    void emptyEnvMapProducesEmptyConfig() {
        final SentinelConfiguration config =
                SentinelEnvironment.toConfiguration(Map.of());

        assertThat(config.getBuildCommand()).isNull();
        assertThat(config.getTestCommand()).isNull();
        assertThat(config.getTestResultDir()).isNull();
        assertThat(config.getSeed()).isNull();
        assertThat(config.isVerbose()).isFalse();
        assertThat(config.getPatterns()).isEmpty();
    }

    @Test
    void defaultSingleWorkspaceIsSentinelWorkspace() {
        assertThat(SentinelEnvironment.DEFAULT_SINGLE_WORKSPACE)
                .isEqualTo(".sentinel_workspace");
    }

    private Map<String, String> requiredEnv() {
        return Map.of(
                "SENTINEL_BUILD_COMMAND", "make all",
                "SENTINEL_TEST_COMMAND", "make test",
                "SENTINEL_TEST_RESULT_DIR", "results/");
    }
}
