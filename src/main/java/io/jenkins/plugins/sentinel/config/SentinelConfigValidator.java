/*
 * Copyright (c) 2026 LG Electronics, Inc. Licensed under the MIT License.
 * SPDX-License-Identifier: MIT
 */

package io.jenkins.plugins.sentinel.config;

/**
 * Validates a SentinelConfiguration for required fields and value ranges.
 *
 * <p>Both {@code sentinelRun} and {@code sentinelReport} run this against
 * their merged configuration before doing any work, so a bad parameter
 * fails the build immediately rather than after an expensive mutation run
 * or partition merge.</p>
 */

public final class SentinelConfigValidator {

    private static final double MAX_THRESHOLD = 100.0;
    private static final long MAX_UNSIGNED_INT = 4_294_967_295L;

    private SentinelConfigValidator() {
    }

    /**
     * Validates the configuration. Throws IllegalArgumentException
     * if any required field is missing or any value is out of range.
     *
     * @param config the configuration to validate
     * @throws IllegalArgumentException if validation fails
     */
    public static void validate(final SentinelConfiguration config) {
        validateNonNegative(config.getTimeout(), "timeout");
        validateNonNegative(config.getMutantsPerLine(), "mutantsPerLine");
        validateNonNegative(config.getLimit(), "limit");
        validateSeed(config.getSeed());
        validatePartition(config);
        validateThreshold(config);
    }

    private static void validatePartition(
            final SentinelConfiguration config) {
        final Integer total = config.getPartitionTotal();
        if (total != null && total <= 0) {
            throw new IllegalArgumentException(
                    "partitionTotal must be a positive integer, got: "
                            + total);
        }
        final Integer index = config.getPartitionIndex();
        if (index == null) {
            return;
        }
        if (total == null) {
            throw new IllegalArgumentException(
                    "partitionTotal is required when partitionIndex is set");
        }
        if (index < 1 || index > total) {
            throw new IllegalArgumentException(
                    "partitionIndex must be between 1 and partitionTotal ("
                            + total + "), got: " + index);
        }
    }

    /**
     * Requires threshold and thresholdAction to be set together.
     *
     * <p>Either one alone is a quality gate that silently does nothing:
     * a threshold with no action is never acted on, and an action with no
     * threshold has nothing to compare against.</p>
     */
    private static void validateThreshold(
            final SentinelConfiguration config) {
        final Double threshold = config.getThreshold();
        final ThresholdAction action = config.getThresholdAction();
        if (threshold == null) {
            if (action != null) {
                throw new IllegalArgumentException(
                        "threshold is required when thresholdAction is set");
            }
            return;
        }
        if (threshold < 0.0 || threshold > MAX_THRESHOLD) {
            throw new IllegalArgumentException(
                    "threshold must be between 0.0 and 100.0, got: "
                            + threshold);
        }
        if (action == null) {
            throw new IllegalArgumentException(
                    "thresholdAction is required when threshold is set");
        }
    }

    private static void validateNonNegative(final Integer value,
                                            final String name) {
        if (value != null && value < 0) {
            throw new IllegalArgumentException(
                    name + " must not be negative, got: " + value);
        }
    }

    private static void validateSeed(final Long seed) {
        if (seed != null && (seed < 0 || seed > MAX_UNSIGNED_INT)) {
            throw new IllegalArgumentException(
                    "seed must be between 0 and "
                            + MAX_UNSIGNED_INT + ", got: " + seed);
        }
    }
}
