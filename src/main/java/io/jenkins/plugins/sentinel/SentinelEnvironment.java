/*
 * Copyright (c) 2026 LG Electronics, Inc. Licensed under the MIT License.
 * SPDX-License-Identifier: MIT
 */

package io.jenkins.plugins.sentinel;

import java.util.List;
import java.util.Map;

import io.jenkins.plugins.sentinel.config.SentinelConfiguration;

/**
 * Reads SENTINEL_* environment variables into a SentinelConfiguration.
 *
 * <p>This is the single source of truth for environment variable names
 * and their mapping to SentinelConfiguration fields.</p>
 */
public final class SentinelEnvironment {

    /** Environment variable for build command. */
    static final String BUILD_COMMAND = "SENTINEL_BUILD_COMMAND";
    /** Environment variable for test command. */
    static final String TEST_COMMAND = "SENTINEL_TEST_COMMAND";
    /** Environment variable for test result directory. */
    static final String TEST_RESULT_DIR = "SENTINEL_TEST_RESULT_DIR";
    /** Environment variable for partition total count. */
    static final String PARTITION_TOTAL = "SENTINEL_PARTITION_TOTAL";
    /** Environment variable for random seed. */
    static final String SEED = "SENTINEL_SEED";
    /** Environment variable for source directory. */
    static final String SOURCE_DIR = "SENTINEL_SOURCE_DIR";
    /** Environment variable for compile database directory. */
    static final String COMPILE_DB_DIR = "SENTINEL_COMPILE_DB_DIR";
    /** Environment variable for timeout. */
    static final String TIMEOUT = "SENTINEL_TIMEOUT";
    /** Environment variable for from commit reference. */
    static final String FROM = "SENTINEL_FROM";
    /** Environment variable for uncommitted flag. */
    static final String UNCOMMITTED = "SENTINEL_UNCOMMITTED";
    /** Environment variable for file patterns. */
    static final String PATTERNS = "SENTINEL_PATTERNS";
    /** Environment variable for file extensions. */
    static final String EXTENSIONS = "SENTINEL_EXTENSIONS";
    /** Environment variable for mutation generator. */
    static final String GENERATOR = "SENTINEL_GENERATOR";
    /** Environment variable for mutants per line. */
    static final String MUTANTS_PER_LINE = "SENTINEL_MUTANTS_PER_LINE";
    /** Environment variable for mutation operators. */
    static final String OPERATORS = "SENTINEL_OPERATORS";
    /** Environment variable for mutant limit. */
    static final String LIMIT = "SENTINEL_LIMIT";
    /** Environment variable for lcov tracefiles. */
    static final String LCOV_TRACEFILES = "SENTINEL_LCOV_TRACEFILES";
    /** Environment variable for config file path. */
    static final String CONFIG = "SENTINEL_CONFIG";
    /** Environment variable for clean flag. */
    static final String CLEAN = "SENTINEL_CLEAN";
    /** Environment variable for dry-run flag. */
    static final String DRY_RUN = "SENTINEL_DRY_RUN";
    /** Environment variable for verbose flag. */
    static final String VERBOSE = "SENTINEL_VERBOSE";
    /** Environment variable for workspace directory. */
    static final String WORKSPACE = "SENTINEL_WORKSPACE";
    /** Environment variable for output directory. */
    static final String OUTPUT_DIR = "SENTINEL_OUTPUT_DIR";
    /** Environment variable for sentinel executable path. */
    static final String PATH = "SENTINEL_PATH";

    /** Prefix for partition workspace directories. */
    static final String PARTITION_PREFIX = ".sentinel-";
    /** Default workspace for merged partition results. */
    static final String MERGED_WORKSPACE = ".sentinel-merged";
    /** Prefix for stash names. */
    static final String STASH_PREFIX = "sentinel-partition-";
    /** Default source directory. */
    static final String DEFAULT_SOURCE_DIR = ".";
    /** Default output directory. */
    static final String DEFAULT_OUTPUT_DIR = "sentinel-report";
    /** Stash name for single (non-partitioned) results. */
    static final String SINGLE_STASH_NAME = "sentinel-partition-single";
    /** Default sentinel workspace for single mode. */
    static final String DEFAULT_SINGLE_WORKSPACE = ".sentinel";

    private SentinelEnvironment() {
    }

    /**
     * Reads SENTINEL_* environment variables into a new
     * SentinelConfiguration.
     *
     * @param env environment variable map
     * @return populated SentinelConfiguration
     */
    public static SentinelConfiguration toConfiguration(
            final Map<String, String> env) {
        final SentinelConfiguration c = new SentinelConfiguration();

        c.setBuildCommand(env.get(BUILD_COMMAND));
        c.setTestCommand(env.get(TEST_COMMAND));
        c.setTestResultDir(env.get(TEST_RESULT_DIR));

        c.setSourceDir(env.get(SOURCE_DIR));
        c.setCompileDbDir(env.get(COMPILE_DB_DIR));
        c.setFrom(env.get(FROM));
        c.setGenerator(env.get(GENERATOR));
        c.setConfig(env.get(CONFIG));
        c.setWorkspace(env.get(WORKSPACE));
        c.setOutputDir(env.get(OUTPUT_DIR));
        c.setSentinelPath(env.get(PATH));

        c.setTimeout(parseInteger(env.get(TIMEOUT)));
        c.setMutantsPerLine(parseInteger(env.get(MUTANTS_PER_LINE)));
        c.setLimit(parseInteger(env.get(LIMIT)));
        c.setPartitionTotal(parseInteger(env.get(PARTITION_TOTAL)));

        c.setSeed(parseLong(env.get(SEED)));

        c.setUncommitted(parseBoolean(env.get(UNCOMMITTED)));
        c.setClean(parseBoolean(env.get(CLEAN)));
        c.setDryRun(parseBoolean(env.get(DRY_RUN)));
        c.setVerbose(parseBoolean(env.get(VERBOSE)));

        c.setPatterns(parseList(env.get(PATTERNS)));
        c.setExtensions(parseList(env.get(EXTENSIONS)));
        c.setOperators(parseList(env.get(OPERATORS)));
        c.setLcovTracefiles(parseList(env.get(LCOV_TRACEFILES)));

        return c;
    }

    /**
     * Generates the stash name for a partition index.
     *
     * @param index 1-based partition index
     * @return stash name like "sentinel-partition-1"
     */
    public static String stashName(final int index) {
        return STASH_PREFIX + index;
    }

    /**
     * Generates the workspace path for a partition index.
     *
     * @param index 1-based partition index
     * @return workspace path like ".sentinel-1"
     */
    public static String partitionWorkspace(final int index) {
        return PARTITION_PREFIX + index;
    }

    private static Integer parseInteger(final String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        return Integer.parseInt(value);
    }

    private static Long parseLong(final String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        return Long.parseLong(value);
    }

    private static boolean parseBoolean(final String value) {
        return "true".equalsIgnoreCase(value);
    }

    private static List<String> parseList(final String value) {
        if (value == null || value.isEmpty()) {
            return List.of();
        }
        return List.of(value.split(","));
    }
}
