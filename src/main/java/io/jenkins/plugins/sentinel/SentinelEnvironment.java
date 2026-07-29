/*
 * Copyright (c) 2026 LG Electronics, Inc. Licensed under the MIT License.
 * SPDX-License-Identifier: MIT
 */

package io.jenkins.plugins.sentinel;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

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

    /** Common prefix for all sentinel environment variables. */
    static final String SENTINEL_PREFIX = "SENTINEL_";

    /** All environment variable names the plugin recognizes. */
    private static final Set<String> KNOWN_NAMES = Set.of(
            BUILD_COMMAND, TEST_COMMAND, TEST_RESULT_DIR, PARTITION_TOTAL,
            SEED, SOURCE_DIR, COMPILE_DB_DIR, TIMEOUT, FROM, UNCOMMITTED,
            PATTERNS, EXTENSIONS, GENERATOR, MUTANTS_PER_LINE, OPERATORS,
            LIMIT, LCOV_TRACEFILES, CONFIG, CLEAN, DRY_RUN, VERBOSE,
            WORKSPACE, OUTPUT_DIR, PATH);

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
    static final String DEFAULT_SINGLE_WORKSPACE = ".sentinel_workspace";
    /** Directory name under build root for archived reports. */
    static final String ARCHIVE_DIR = "sentinel-report";
    /** Sentinel XML result file name. */
    static final String MUTATIONS_XML = "mutations.xml";
    /** Sentinel HTML report file name. */
    static final String HTML_REPORT_FILE = "index.html";

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

        c.setBuildCommand(read(env, BUILD_COMMAND));
        c.setTestCommand(read(env, TEST_COMMAND));
        c.setTestResultDir(read(env, TEST_RESULT_DIR));

        c.setSourceDir(read(env, SOURCE_DIR));
        c.setCompileDbDir(read(env, COMPILE_DB_DIR));
        c.setFrom(read(env, FROM));
        c.setGenerator(read(env, GENERATOR));
        c.setConfig(read(env, CONFIG));
        c.setWorkspace(read(env, WORKSPACE));
        c.setOutputDir(read(env, OUTPUT_DIR));
        c.setSentinelPath(read(env, PATH));

        c.setTimeout(parseInteger(TIMEOUT, read(env, TIMEOUT)));
        c.setMutantsPerLine(
                parseInteger(MUTANTS_PER_LINE, read(env, MUTANTS_PER_LINE)));
        c.setLimit(parseInteger(LIMIT, read(env, LIMIT)));
        c.setPartitionTotal(
                parseInteger(PARTITION_TOTAL, read(env, PARTITION_TOTAL)));

        c.setSeed(parseLong(SEED, read(env, SEED)));

        c.setUncommitted(parseBoolean(read(env, UNCOMMITTED)));
        c.setClean(parseBoolean(read(env, CLEAN)));
        c.setDryRun(parseBoolean(read(env, DRY_RUN)));
        c.setVerbose(parseBoolean(read(env, VERBOSE)));

        c.setPatterns(parseList(read(env, PATTERNS)));
        c.setExtensions(parseList(read(env, EXTENSIONS)));
        c.setOperators(parseList(read(env, OPERATORS)));
        c.setLcovTracefiles(parseList(read(env, LCOV_TRACEFILES)));

        return c;
    }

    /**
     * Applies a step parameter over the value already read from the
     * environment, when the parameter was given.
     *
     * <p>This is the override half of the precedence rule that
     * {@link #toConfiguration} starts: environment first, then any
     * non-null step parameter wins. Both steps merge their parameters
     * through this method so the rule is stated once rather than as one
     * {@code if} per field.</p>
     *
     * @param value  step parameter value, null when not given
     * @param setter target configuration setter
     * @param <T>    the parameter type
     */
    public static <T> void override(final T value,
                                    final Consumer<T> setter) {
        if (value != null) {
            setter.accept(value);
        }
    }

    /**
     * Returns whether a value counts as configured.
     *
     * <p>This is the single definition of "set" for the whole plugin: a
     * variable that is absent, empty, or whitespace-only means the user
     * did not choose a value, so the plugin's own default applies. Without
     * one definition, {@code SENTINEL_WORKSPACE=''} reads as "set" in one
     * place and "unset" in another, and the two disagree about which
     * directory sentinel wrote to.</p>
     *
     * @param value the value to test, may be null
     * @return true if the value is present and not blank
     */
    public static boolean isSet(final String value) {
        return value != null && !value.isBlank();
    }

    /**
     * Returns {@code value} when it is set, otherwise {@code fallback}.
     *
     * @param value    candidate value, may be null or blank
     * @param fallback value to use when the candidate is not set
     * @return the effective value
     * @see #isSet(String)
     */
    public static String orDefault(final String value,
                                   final String fallback) {
        return isSet(value) ? value : fallback;
    }

    /**
     * Returns the plugin-managed default for a directory, or {@code null}
     * when the user chose the directory themselves.
     *
     * <p>The plugin recreates only the directories it assigned, so a
     * {@code null} result means "leave this alone".</p>
     *
     * @param stepValue    step parameter value, may be null
     * @param envValue     environment variable value, may be null or blank
     * @param managedValue the directory the plugin would assign
     * @return the managed directory, or null when either override is set
     */
    public static String managedDefault(final String stepValue,
                                        final String envValue,
                                        final String managedValue) {
        if (stepValue != null || isSet(envValue)) {
            return null;
        }
        return managedValue;
    }

    /**
     * Reads a variable, treating a blank value as absent.
     *
     * @param env  environment variable map
     * @param name variable name
     * @return the value, or null when absent or blank
     */
    private static String read(final Map<String, String> env,
                               final String name) {
        final String value = env.get(name);
        return isSet(value) ? value : null;
    }

    /**
     * Returns the source directory to report against.
     *
     * @param config merged configuration
     * @return the configured source directory, or {@value #DEFAULT_SOURCE_DIR}
     */
    public static String effectiveSourceDir(
            final SentinelConfiguration config) {
        return orDefault(config.getSourceDir(), DEFAULT_SOURCE_DIR);
    }

    /**
     * Returns the directory reports are written to.
     *
     * @param config merged configuration
     * @return the configured output directory, or
     *         {@value #DEFAULT_OUTPUT_DIR}
     */
    public static String effectiveOutputDir(
            final SentinelConfiguration config) {
        return orDefault(config.getOutputDir(), DEFAULT_OUTPUT_DIR);
    }

    /**
     * Returns the sentinel workspace for an unpartitioned run.
     *
     * <p>Both {@code sentinelRun}'s stash and {@code sentinelReport}'s
     * unstash resolve the directory through this method, so they cannot
     * disagree about where sentinel put its results.</p>
     *
     * @param config merged configuration
     * @return the configured workspace, or
     *         {@value #DEFAULT_SINGLE_WORKSPACE}
     */
    public static String effectiveSingleWorkspace(
            final SentinelConfiguration config) {
        return orDefault(config.getWorkspace(), DEFAULT_SINGLE_WORKSPACE);
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

    /**
     * Returns the {@code SENTINEL_*} environment variable names that the
     * plugin does not recognize (likely typos), sorted for stable output.
     *
     * @param env environment variable map
     * @return sorted list of unknown names, empty if none
     */
    public static List<String> unknownVariableNames(
            final Map<String, String> env) {
        final List<String> unknown = new ArrayList<>();
        for (final String key : env.keySet()) {
            if (key.startsWith(SENTINEL_PREFIX)
                    && !KNOWN_NAMES.contains(key)) {
                unknown.add(key);
            }
        }
        Collections.sort(unknown);
        return unknown;
    }

    /**
     * Logs a warning for each unrecognized {@code SENTINEL_*} variable.
     * Never fails the build — unknown variables are likely typos but may
     * also be unrelated, so they are reported and ignored.
     *
     * @param env    environment variable map
     * @param logger destination for warning lines
     */
    public static void warnUnknownVariables(
            final Map<String, String> env, final PrintStream logger) {
        for (final String name : unknownVariableNames(env)) {
            logger.println("[Sentinel] Ignoring unknown variable: " + name
                    + " (typo of a SENTINEL_* option?)");
        }
    }

    private static Integer parseInteger(final String name,
                                        final String value) {
        return parseNumber(name, value, Integer::parseInt);
    }

    private static Long parseLong(final String name, final String value) {
        return parseNumber(name, value, Long::parseLong);
    }

    /**
     * Parses a numeric variable. The value is trimmed first, so a variable
     * padded by a pipeline {@code environment {}} block still parses.
     *
     * @param name   variable name, used in the error message
     * @param value  non-blank value, or null when the variable is unset
     * @param parser parse function for the target type
     * @param <T>    numeric target type
     * @return the parsed value, or null when unset
     * @throws IllegalArgumentException if the value is not numeric
     */
    private static <T> T parseNumber(final String name, final String value,
                                     final Function<String, T> parser) {
        if (value == null) {
            return null;
        }
        try {
            return parser.apply(value.trim());
        } catch (final NumberFormatException e) {
            throw new IllegalArgumentException(
                    name + " must be an integer, got: '" + value + "'", e);
        }
    }

    private static boolean parseBoolean(final String value) {
        return value != null && "true".equalsIgnoreCase(value.trim());
    }

    /**
     * Splits a comma-separated variable, dropping blank entries so a
     * trailing comma does not produce an empty repeated CLI option.
     *
     * @param value non-blank value, or null when the variable is unset
     * @return the entries, empty when unset
     */
    private static List<String> parseList(final String value) {
        if (value == null) {
            return List.of();
        }
        final List<String> entries = new ArrayList<>();
        // The -1 limit stops split from silently dropping trailing empty
        // entries, so the blank filter below is the only thing deciding
        // what counts as an entry. It also keeps Error Prone's
        // StringSplitter check quiet, which flags the one-argument form.
        for (final String part : value.split(",", -1)) {
            final String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                entries.add(trimmed);
            }
        }
        return List.copyOf(entries);
    }
}
