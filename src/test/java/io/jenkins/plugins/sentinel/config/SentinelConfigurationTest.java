/*
 * Copyright (c) 2026 LG Electronics, Inc. Licensed under the MIT License.
 * SPDX-License-Identifier: MIT
 */

package io.jenkins.plugins.sentinel.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class SentinelConfigurationTest {

    @Test
    void nullListSettersFallBackToEmptyLists() {
        final SentinelConfiguration config = new SentinelConfiguration();
        config.setPatterns(null);
        config.setExtensions(null);
        config.setOperators(null);
        config.setLcovTracefiles(null);

        assertThat(config.getPatterns()).isEmpty();
        assertThat(config.getExtensions()).isEmpty();
        assertThat(config.getOperators()).isEmpty();
        assertThat(config.getLcovTracefiles()).isEmpty();
    }

    @Test
    void listSettersCopyTheirInput() {
        final SentinelConfiguration config = new SentinelConfiguration();
        final List<String> source = new ArrayList<>(List.of("a"));
        config.setPatterns(source);
        source.add("b");

        assertThat(config.getPatterns()).containsExactly("a");
    }

    @Test
    void partitionSpecReturnsNullWhenIndexNull() {
        final SentinelConfiguration config = new SentinelConfiguration();
        config.setPartitionTotal(4);
        assertThat(config.getPartitionSpec()).isNull();
    }

    @Test
    void partitionSpecReturnsNullWhenTotalNull() {
        final SentinelConfiguration config = new SentinelConfiguration();
        config.setPartitionIndex(2);
        assertThat(config.getPartitionSpec()).isNull();
    }

    @Test
    void partitionSpecReturnsNullWhenBothNull() {
        final SentinelConfiguration config = new SentinelConfiguration();
        assertThat(config.getPartitionSpec()).isNull();
    }

    @Test
    void partitionSpecFormatsCorrectly() {
        final SentinelConfiguration config = new SentinelConfiguration();
        config.setPartitionIndex(3);
        config.setPartitionTotal(8);
        assertThat(config.getPartitionSpec()).isEqualTo("3/8");
    }

    @Test
    void listSetterDefensivelyCopies() {
        final List<String> original = new ArrayList<>();
        original.add("AOR");

        final SentinelConfiguration config = new SentinelConfiguration();
        config.setPatterns(original);

        original.add("ROR");
        assertThat(config.getPatterns()).containsExactly("AOR");
    }

    @Test
    void listSetterHandlesNull() {
        final SentinelConfiguration config = new SentinelConfiguration();
        config.setPatterns(null);
        assertThat(config.getPatterns()).isEmpty();
    }

    @Test
    void defaultValuesAreCorrect() {
        final SentinelConfiguration config = new SentinelConfiguration();
        assertThat(config.getBuildCommand()).isNull();
        assertThat(config.isVerbose()).isFalse();
        assertThat(config.isClean()).isFalse();
        assertThat(config.isDryRun()).isFalse();
        assertThat(config.isUncommitted()).isFalse();
        assertThat(config.getPartitionIndex()).isNull();
        assertThat(config.getPartitionTotal()).isNull();
        assertThat(config.getThreshold()).isNull();
        assertThat(config.getOperators()).isEmpty();
    }
}
