/*
 * Copyright (c) 2026 LG Electronics, Inc. Licensed under the MIT License.
 * SPDX-License-Identifier: MIT
 */

package io.jenkins.plugins.sentinel.config;

/**
 * Validates a SentinelConfiguration for required fields and value ranges.
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

        if (config.getPartitionIndex() != null) {
            if (config.getPartitionTotal() == null) {
                throw new IllegalArgumentException(
                        "partitionTotal is required when partitionIndex is set");
            }
            if (config.getPartitionIndex() < 1
                    || config.getPartitionIndex() > config.getPartitionTotal()) {
                throw new IllegalArgumentException(
                        "partitionIndex must be between 1 and partitionTotal ("
                                + config.getPartitionTotal() + "), got: "
                                + config.getPartitionIndex());
            }
        }

        final Double threshold = config.getThreshold();
        if (threshold != null) {
            if (threshold < 0.0 || threshold > MAX_THRESHOLD) {
                throw new IllegalArgumentException(
                        "threshold must be between 0.0 and 100.0, got: "
                                + threshold);
            }
            if (config.getThresholdAction() == null) {
                throw new IllegalArgumentException(
                        "thresholdAction is required when threshold is set");
            }
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
