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
    void missingBuildCommandThrows() {
        final SentinelConfiguration config = minimalConfig();
        config.setBuildCommand(null);
        assertThatThrownBy(
                () -> SentinelConfigValidator.validate(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("buildCommand");
    }

    @Test
    void missingTestCommandThrows() {
        final SentinelConfiguration config = minimalConfig();
        config.setTestCommand(null);
        assertThatThrownBy(
                () -> SentinelConfigValidator.validate(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("testCommand");
    }

    @Test
    void missingTestResultDirThrows() {
        final SentinelConfiguration config = minimalConfig();
        config.setTestResultDir(null);
        assertThatThrownBy(
                () -> SentinelConfigValidator.validate(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("testResultDir");
    }

    @Test
    void emptyBuildCommandThrows() {
        final SentinelConfiguration config = minimalConfig();
        config.setBuildCommand("  ");
        assertThatThrownBy(
                () -> SentinelConfigValidator.validate(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("buildCommand");
    }

    @Test
    void negativePartitionsThrows() {
        final SentinelConfiguration config = minimalConfig();
        config.setPartitions(-1);
        assertThatThrownBy(
                () -> SentinelConfigValidator.validate(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("partitions");
    }

    @Test
    void zeroPartitionsThrows() {
        final SentinelConfiguration config = minimalConfig();
        config.setPartitions(0);
        assertThatThrownBy(
                () -> SentinelConfigValidator.validate(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("partitions");
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

    private SentinelConfiguration minimalConfig() {
        final SentinelConfiguration config = new SentinelConfiguration();
        config.setBuildCommand("make all");
        config.setTestCommand("make test");
        config.setTestResultDir("test-results/");
        return config;
    }
}
