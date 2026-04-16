/*
 * Copyright (c) 2026 LG Electronics, Inc. Licensed under the MIT License.
 * SPDX-License-Identifier: MIT
 */

package io.jenkins.plugins.sentinel.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SentinelConfigValidatorTest {

    @Test
    void validConfigPasses() {
        final SentinelConfiguration config = minimalConfig();
        SentinelConfigValidator.validate(config);
        assertThat(config.getBuildCommand()).isNotBlank();
    }

    @Test
    void nullBuildCommandPasses() {
        final SentinelConfiguration config = minimalConfig();
        config.setBuildCommand(null);
        SentinelConfigValidator.validate(config);
        assertThat(config.getBuildCommand()).isNull();
    }

    @Test
    void nullTestCommandPasses() {
        final SentinelConfiguration config = minimalConfig();
        config.setTestCommand(null);
        SentinelConfigValidator.validate(config);
        assertThat(config.getTestCommand()).isNull();
    }

    @Test
    void nullTestResultDirPasses() {
        final SentinelConfiguration config = minimalConfig();
        config.setTestResultDir(null);
        SentinelConfigValidator.validate(config);
        assertThat(config.getTestResultDir()).isNull();
    }

    @Test
    void emptyConfigPasses() {
        final SentinelConfiguration config = new SentinelConfiguration();
        SentinelConfigValidator.validate(config);
        assertThat(config.getBuildCommand()).isNull();
    }

    @Test
    void validConfigWithPartitionIndexPasses() {
        final SentinelConfiguration config = minimalConfig();
        config.setPartitionIndex(2);
        config.setPartitionTotal(4);
        SentinelConfigValidator.validate(config);
        assertThat(config.getPartitionIndex()).isEqualTo(2);
    }

    @Test
    void partitionIndexZeroThrows() {
        final SentinelConfiguration config = minimalConfig();
        config.setPartitionIndex(0);
        config.setPartitionTotal(4);
        assertThatThrownBy(
                () -> SentinelConfigValidator.validate(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("partitionIndex");
    }

    @Test
    void partitionIndexExceedsTotalThrows() {
        final SentinelConfiguration config = minimalConfig();
        config.setPartitionIndex(5);
        config.setPartitionTotal(4);
        assertThatThrownBy(
                () -> SentinelConfigValidator.validate(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("partitionIndex");
    }

    @Test
    void partitionIndexWithoutTotalThrows() {
        final SentinelConfiguration config = minimalConfig();
        config.setPartitionIndex(1);
        assertThatThrownBy(
                () -> SentinelConfigValidator.validate(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("partitionTotal");
    }

    @Test
    void partitionTotalWithoutIndexIsValid() {
        final SentinelConfiguration config = minimalConfig();
        config.setPartitionTotal(4);
        SentinelConfigValidator.validate(config);
        assertThat(config.getPartitionTotal()).isEqualTo(4);
    }

    @Test
    void thresholdOutOfRangeThrows() {
        final SentinelConfiguration config = minimalConfig();
        config.setThreshold(150.0);
        assertThatThrownBy(
                () -> SentinelConfigValidator.validate(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("threshold");
    }

    @Test
    void negativeThresholdThrows() {
        final SentinelConfiguration config = minimalConfig();
        config.setThreshold(-1.0);
        assertThatThrownBy(
                () -> SentinelConfigValidator.validate(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("threshold");
    }

    @Test
    void thresholdWithoutActionThrows() {
        final SentinelConfiguration config = minimalConfig();
        config.setThreshold(80.0);
        config.setThresholdAction(null);
        assertThatThrownBy(
                () -> SentinelConfigValidator.validate(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("thresholdAction");
    }

    @Test
    void nullThresholdSkipsValidation() {
        final SentinelConfiguration config = minimalConfig();
        config.setThreshold(null);
        config.setThresholdAction(null);
        SentinelConfigValidator.validate(config);
        assertThat(config.getThreshold()).isNull();
    }

    @Test
    void thresholdZeroIsValid() {
        final SentinelConfiguration config = minimalConfig();
        config.setThreshold(0.0);
        config.setThresholdAction(ThresholdAction.FAILURE);
        SentinelConfigValidator.validate(config);
        assertThat(config.getThreshold()).isEqualTo(0.0);
    }

    @Test
    void thresholdHundredIsValid() {
        final SentinelConfiguration config = minimalConfig();
        config.setThreshold(100.0);
        config.setThresholdAction(ThresholdAction.UNSTABLE);
        SentinelConfigValidator.validate(config);
        assertThat(config.getThreshold()).isEqualTo(100.0);
    }

    @Test
    void partitionIndexEqualsTotal() {
        final SentinelConfiguration config = minimalConfig();
        config.setPartitionIndex(4);
        config.setPartitionTotal(4);
        SentinelConfigValidator.validate(config);
        assertThat(config.getPartitionIndex()).isEqualTo(4);
    }

    @Test
    void negativePartitionIndexThrows() {
        final SentinelConfiguration config = minimalConfig();
        config.setPartitionIndex(-1);
        config.setPartitionTotal(4);
        assertThatThrownBy(
                () -> SentinelConfigValidator.validate(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("partitionIndex");
    }

    @Test
    void negativeSeedThrows() {
        final SentinelConfiguration config = minimalConfig();
        config.setSeed(-1L);
        assertThatThrownBy(
                () -> SentinelConfigValidator.validate(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("seed");
    }

    @Test
    void seedExceedsUnsignedIntMaxThrows() {
        final SentinelConfiguration config = minimalConfig();
        config.setSeed(4_294_967_296L);
        assertThatThrownBy(
                () -> SentinelConfigValidator.validate(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("seed");
    }

    @Test
    void seedAtUnsignedIntMaxPasses() {
        final SentinelConfiguration config = minimalConfig();
        config.setSeed(4_294_967_295L);
        SentinelConfigValidator.validate(config);
        assertThat(config.getSeed()).isEqualTo(4_294_967_295L);
    }

    @Test
    void seedZeroPasses() {
        final SentinelConfiguration config = minimalConfig();
        config.setSeed(0L);
        SentinelConfigValidator.validate(config);
        assertThat(config.getSeed()).isEqualTo(0L);
    }

    @Test
    void nullSeedPasses() {
        final SentinelConfiguration config = minimalConfig();
        config.setSeed(null);
        SentinelConfigValidator.validate(config);
        assertThat(config.getSeed()).isNull();
    }

    @Test
    void negativeTimeoutThrows() {
        final SentinelConfiguration config = minimalConfig();
        config.setTimeout(-1);
        assertThatThrownBy(
                () -> SentinelConfigValidator.validate(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timeout");
    }

    @Test
    void timeoutZeroPasses() {
        final SentinelConfiguration config = minimalConfig();
        config.setTimeout(0);
        SentinelConfigValidator.validate(config);
        assertThat(config.getTimeout()).isEqualTo(0);
    }

    @Test
    void nullTimeoutPasses() {
        final SentinelConfiguration config = minimalConfig();
        config.setTimeout(null);
        SentinelConfigValidator.validate(config);
        assertThat(config.getTimeout()).isNull();
    }

    @Test
    void negativeMutantsPerLineThrows() {
        final SentinelConfiguration config = minimalConfig();
        config.setMutantsPerLine(-1);
        assertThatThrownBy(
                () -> SentinelConfigValidator.validate(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mutantsPerLine");
    }

    @Test
    void mutantsPerLineZeroPasses() {
        final SentinelConfiguration config = minimalConfig();
        config.setMutantsPerLine(0);
        SentinelConfigValidator.validate(config);
        assertThat(config.getMutantsPerLine()).isEqualTo(0);
    }

    @Test
    void nullMutantsPerLinePasses() {
        final SentinelConfiguration config = minimalConfig();
        config.setMutantsPerLine(null);
        SentinelConfigValidator.validate(config);
        assertThat(config.getMutantsPerLine()).isNull();
    }

    @Test
    void negativeLimitThrows() {
        final SentinelConfiguration config = minimalConfig();
        config.setLimit(-1);
        assertThatThrownBy(
                () -> SentinelConfigValidator.validate(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit");
    }

    @Test
    void limitZeroPasses() {
        final SentinelConfiguration config = minimalConfig();
        config.setLimit(0);
        SentinelConfigValidator.validate(config);
        assertThat(config.getLimit()).isEqualTo(0);
    }

    @Test
    void nullLimitPasses() {
        final SentinelConfiguration config = minimalConfig();
        config.setLimit(null);
        SentinelConfigValidator.validate(config);
        assertThat(config.getLimit()).isNull();
    }

    private SentinelConfiguration minimalConfig() {
        final SentinelConfiguration config = new SentinelConfiguration();
        config.setBuildCommand("make all");
        config.setTestCommand("make test");
        config.setTestResultDir("test-results/");
        return config;
    }
}
