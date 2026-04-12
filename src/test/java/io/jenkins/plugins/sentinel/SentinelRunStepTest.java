package io.jenkins.plugins.sentinel;

import static org.assertj.core.api.Assertions.assertThat;

import io.jenkins.plugins.sentinel.config.SentinelConfiguration;
import org.junit.jupiter.api.Test;

class SentinelRunStepTest {

    private static final String BUILD_CMD = "make all";
    private static final String TEST_CMD = "make test";
    private static final String TEST_RESULT = "test-results/";

    @Test
    void defaultValuesAreSet() {
        final SentinelRunStep step = new SentinelRunStep(
                BUILD_CMD, TEST_CMD, TEST_RESULT);
        assertThat(step.getBuildCommand()).isEqualTo(BUILD_CMD);
        assertThat(step.getTestCommand()).isEqualTo(TEST_CMD);
        assertThat(step.getTestResultDir()).isEqualTo(TEST_RESULT);
        assertThat(step.getPartition()).isNull();
        assertThat(step.getSeed()).isNull();
        assertThat(step.isVerbose()).isFalse();
        assertThat(step.isClean()).isFalse();
    }

    @Test
    void settersUpdateValues() {
        final SentinelRunStep step = new SentinelRunStep(
                BUILD_CMD, TEST_CMD, TEST_RESULT);
        step.setPartition("2/4");
        step.setSeed(12_345L);
        step.setVerbose(true);
        step.setSourceDir("src");
        step.setWorkspace(".sentinel-2");
        assertThat(step.getPartition()).isEqualTo("2/4");
        assertThat(step.getSeed()).isEqualTo(12_345L);
        assertThat(step.isVerbose()).isTrue();
        assertThat(step.getSourceDir()).isEqualTo("src");
        assertThat(step.getWorkspace()).isEqualTo(".sentinel-2");
    }

    @Test
    void toConfigurationMapsAllFields() {
        final SentinelRunStep step = new SentinelRunStep(
                BUILD_CMD, TEST_CMD, TEST_RESULT);
        step.setPartition("1/4");
        step.setSeed(99L);
        step.setFrom("HEAD~1");
        step.setVerbose(true);

        final SentinelConfiguration config = step.toConfiguration();
        assertThat(config.getBuildCommand()).isEqualTo(BUILD_CMD);
        assertThat(config.getTestCommand()).isEqualTo(TEST_CMD);
        assertThat(config.getTestResultDir()).isEqualTo(TEST_RESULT);
        assertThat(config.getPartition()).isEqualTo("1/4");
        assertThat(config.getSeed()).isEqualTo(99L);
        assertThat(config.getFrom()).isEqualTo("HEAD~1");
        assertThat(config.isVerbose()).isTrue();
    }
}
