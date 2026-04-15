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
        requireNonBlank(config.getBuildCommand(), "buildCommand");
        requireNonBlank(config.getTestCommand(), "testCommand");
        requireNonBlank(config.getTestResultDir(), "testResultDir");

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

    private static void requireNonBlank(
            final String value, final String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    name + " is required and must not be blank");
        }
    }
}
