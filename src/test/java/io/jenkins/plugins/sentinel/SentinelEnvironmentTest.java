/*
 * Copyright (c) 2026 LG Electronics, Inc. Licensed under the MIT License.
 * SPDX-License-Identifier: MIT
 */

package io.jenkins.plugins.sentinel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import io.jenkins.plugins.sentinel.config.SentinelConfiguration;
import org.junit.jupiter.api.Test;

class SentinelEnvironmentTest {

    private static final String TRUE_STR = "true";
    private static final String FALLBACK = "fallback";
    private static final String MANAGED = "managed";
    private static final String ENV_SEED = "SENTINEL_SEED";
    private static final String SRC = "src";
    private static final long SEED_VALUE = 12_345L;
    private static final String ENV_TIMEOUT = "SENTINEL_TIMEOUT";
    private static final String ENV_TIMOUT_TYPO = "SENTINEL_TIMOUT";
    private static final String TIMEOUT_VALUE = "300";

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
        env.put("SENTINEL_SOURCE_DIR", SRC);
        env.put("SENTINEL_COMPILE_DB_DIR", "build");
        env.put("SENTINEL_FROM", "HEAD~1");
        env.put("SENTINEL_GENERATOR", "random");
        env.put("SENTINEL_CONFIG", ".sentinel.yml");
        env.put("SENTINEL_WORKSPACE", ".sentinel-1");
        env.put("SENTINEL_OUTPUT_DIR", "report");
        env.put("SENTINEL_PATH", "/usr/bin/sentinel");

        final SentinelConfiguration config =
                SentinelEnvironment.toConfiguration(env);

        assertThat(config.getSourceDir()).isEqualTo(SRC);
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
        env.put(ENV_TIMEOUT, TIMEOUT_VALUE);
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
        env.put(ENV_SEED, String.valueOf(SEED_VALUE));

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
    void invalidIntegerEnvVarFailsWithVariableName() {
        final Map<String, String> env = new HashMap<>(requiredEnv());
        env.put(ENV_TIMEOUT, "2h");

        assertThatThrownBy(() ->
                SentinelEnvironment.toConfiguration(env))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(ENV_TIMEOUT)
                .hasMessageContaining("2h");
    }

    @Test
    void invalidSeedEnvVarFailsWithVariableName() {
        final Map<String, String> env = new HashMap<>(requiredEnv());
        env.put(ENV_SEED, "xyz");

        assertThatThrownBy(() ->
                SentinelEnvironment.toConfiguration(env))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(ENV_SEED)
                .hasMessageContaining("xyz");
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

    @Test
    void unknownVariableNamesListsTyposSorted() {
        final Map<String, String> env = new HashMap<>(requiredEnv());
        env.put("SENTINEL_VERBOZE", "true");
        env.put(ENV_TIMOUT_TYPO, TIMEOUT_VALUE);

        assertThat(SentinelEnvironment.unknownVariableNames(env))
                .containsExactly(ENV_TIMOUT_TYPO, "SENTINEL_VERBOZE");
    }

    @Test
    void unknownVariableNamesIgnoresKnownAndNonSentinel() {
        final Map<String, String> env = new HashMap<>(requiredEnv());
        env.put(ENV_TIMEOUT, TIMEOUT_VALUE);
        env.put("PATH", "/usr/bin");
        env.put("JAVA_HOME", "/opt/java");

        assertThat(SentinelEnvironment.unknownVariableNames(env))
                .isEmpty();
    }

    @Test
    void warnUnknownVariablesLogsEachUnknown() {
        final Map<String, String> env = new HashMap<>(requiredEnv());
        env.put(ENV_TIMOUT_TYPO, TIMEOUT_VALUE);

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PrintStream ps =
                     new PrintStream(out, true, StandardCharsets.UTF_8)) {
            SentinelEnvironment.warnUnknownVariables(env, ps);
        }

        assertThat(out.toString(StandardCharsets.UTF_8))
                .contains(ENV_TIMOUT_TYPO)
                .contains("unknown variable");
    }

    // --- "set" is one definition, not six --------------------------------

    @Test
    void isSetRejectsNullEmptyAndWhitespace() {
        assertThat(SentinelEnvironment.isSet("value")).isTrue();
        assertThat(SentinelEnvironment.isSet(null)).isFalse();
        assertThat(SentinelEnvironment.isSet("")).isFalse();
        assertThat(SentinelEnvironment.isSet("   ")).isFalse();
    }

    @Test
    void orDefaultFallsBackForEveryUnsetForm() {
        assertThat(SentinelEnvironment.orDefault("value", FALLBACK))
                .isEqualTo("value");
        assertThat(SentinelEnvironment.orDefault(null, FALLBACK))
                .isEqualTo(FALLBACK);
        assertThat(SentinelEnvironment.orDefault("", FALLBACK))
                .isEqualTo(FALLBACK);
        assertThat(SentinelEnvironment.orDefault(" ", FALLBACK))
                .isEqualTo(FALLBACK);
    }

    @Test
    void managedDefaultYieldsTheDefaultOnlyWhenNothingIsOverridden() {
        assertThat(SentinelEnvironment.managedDefault(
                null, null, MANAGED)).isEqualTo(MANAGED);
        assertThat(SentinelEnvironment.managedDefault(
                null, "", MANAGED)).isEqualTo(MANAGED);
    }

    @Test
    void managedDefaultYieldsNullWhenTheUserChoseTheDirectory() {
        assertThat(SentinelEnvironment.managedDefault(
                "step", null, MANAGED)).isNull();
        assertThat(SentinelEnvironment.managedDefault(
                null, "env", MANAGED)).isNull();
        assertThat(SentinelEnvironment.managedDefault(
                "step", "env", MANAGED)).isNull();
    }

    @Test
    void overrideAppliesOnlyNonNullValues() {
        final SentinelConfiguration config = new SentinelConfiguration();
        config.setBuildCommand("from-env");

        SentinelEnvironment.override(null, config::setBuildCommand);
        assertThat(config.getBuildCommand()).isEqualTo("from-env");

        SentinelEnvironment.override("from-step", config::setBuildCommand);
        assertThat(config.getBuildCommand()).isEqualTo("from-step");
    }

    // --- blank values mean "unset" ---------------------------------------

    @Test
    void blankStringVariablesReadAsUnset() {
        final Map<String, String> env = new HashMap<>();
        env.put("SENTINEL_WORKSPACE", "");
        env.put("SENTINEL_SOURCE_DIR", "   ");
        env.put("SENTINEL_OUTPUT_DIR", "");

        final SentinelConfiguration config =
                SentinelEnvironment.toConfiguration(env);

        assertThat(config.getWorkspace()).isNull();
        assertThat(config.getSourceDir()).isNull();
        assertThat(config.getOutputDir()).isNull();
    }

    @Test
    void blankNumericVariablesReadAsUnset() {
        final Map<String, String> env = new HashMap<>();
        env.put(ENV_TIMEOUT, "   ");
        env.put(ENV_SEED, "");
        env.put("SENTINEL_PARTITION_TOTAL", " ");

        final SentinelConfiguration config =
                SentinelEnvironment.toConfiguration(env);

        assertThat(config.getTimeout()).isNull();
        assertThat(config.getSeed()).isNull();
        assertThat(config.getPartitionTotal()).isNull();
    }

    @Test
    void paddedNumericVariablesStillParse() {
        final Map<String, String> env = new HashMap<>();
        env.put(ENV_TIMEOUT, " 300 ");
        env.put(ENV_SEED, " 42 ");

        final SentinelConfiguration config =
                SentinelEnvironment.toConfiguration(env);

        assertThat(config.getTimeout()).isEqualTo(300);
        assertThat(config.getSeed()).isEqualTo(42L);
    }

    @Test
    void paddedBooleanVariablesStillParse() {
        final Map<String, String> env = new HashMap<>();
        env.put("SENTINEL_VERBOSE", " TRUE ");

        assertThat(SentinelEnvironment.toConfiguration(env).isVerbose())
                .isTrue();
    }

    @Test
    void listVariablesDropBlankEntries() {
        final Map<String, String> env = new HashMap<>();
        env.put("SENTINEL_PATTERNS", "src/*.cpp, ,lib/*.cpp,");

        assertThat(SentinelEnvironment.toConfiguration(env).getPatterns())
                .containsExactly("src/*.cpp", "lib/*.cpp");
    }

    // --- effective values ------------------------------------------------

    @Test
    void effectiveDirectoriesFallBackToTheDocumentedDefaults() {
        final SentinelConfiguration config = new SentinelConfiguration();

        assertThat(SentinelEnvironment.effectiveSourceDir(config))
                .isEqualTo(SentinelEnvironment.DEFAULT_SOURCE_DIR);
        assertThat(SentinelEnvironment.effectiveOutputDir(config))
                .isEqualTo(SentinelEnvironment.DEFAULT_OUTPUT_DIR);
        assertThat(SentinelEnvironment.effectiveSingleWorkspace(config))
                .isEqualTo(SentinelEnvironment.DEFAULT_SINGLE_WORKSPACE);
    }

    @Test
    void effectiveDirectoriesUseTheConfiguredValues() {
        final SentinelConfiguration config = new SentinelConfiguration();
        config.setSourceDir(SRC);
        config.setOutputDir("out");
        config.setWorkspace("ws");

        assertThat(SentinelEnvironment.effectiveSourceDir(config))
                .isEqualTo(SRC);
        assertThat(SentinelEnvironment.effectiveOutputDir(config))
                .isEqualTo("out");
        assertThat(SentinelEnvironment.effectiveSingleWorkspace(config))
                .isEqualTo("ws");
    }

    private Map<String, String> requiredEnv() {
        return Map.of(
                "SENTINEL_BUILD_COMMAND", "make all",
                "SENTINEL_TEST_COMMAND", "make test",
                "SENTINEL_TEST_RESULT_DIR", "results/");
    }
}
