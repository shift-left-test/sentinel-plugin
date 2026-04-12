package io.jenkins.plugins.sentinel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class SentinelMergeStepTest {

    private static final String SENTINEL_1 = ".sentinel-1";
    private static final String SENTINEL_2 = ".sentinel-2";

    @Test
    void requiredFieldsAreSet() {
        final SentinelMergeStep step = new SentinelMergeStep(
                List.of(SENTINEL_1, SENTINEL_2));
        assertThat(step.getPartitions()).containsExactly(
                SENTINEL_1, SENTINEL_2);
    }

    @Test
    void optionalFieldsHaveDefaults() {
        final SentinelMergeStep step = new SentinelMergeStep(
                List.of(SENTINEL_1));
        assertThat(step.getWorkspace()).isNull();
        assertThat(step.getSourceDir()).isNull();
        assertThat(step.getOutputDir()).isNull();
        assertThat(step.getThreshold()).isNull();
        assertThat(step.getThresholdAction()).isNull();
    }

    @Test
    void settersUpdateValues() {
        final SentinelMergeStep step = new SentinelMergeStep(
                List.of(SENTINEL_1));
        step.setWorkspace(".sentinel-merged");
        step.setSourceDir(".");
        step.setOutputDir("report");
        step.setThreshold(80.0);
        step.setThresholdAction("UNSTABLE");
        step.setSentinelPath("/usr/bin/sentinel");

        assertThat(step.getWorkspace())
                .isEqualTo(".sentinel-merged");
        assertThat(step.getSourceDir()).isEqualTo(".");
        assertThat(step.getOutputDir()).isEqualTo("report");
        assertThat(step.getThreshold()).isEqualTo(80.0);
        assertThat(step.getThresholdAction())
                .isEqualTo("UNSTABLE");
        assertThat(step.getSentinelPath())
                .isEqualTo("/usr/bin/sentinel");
    }
}
