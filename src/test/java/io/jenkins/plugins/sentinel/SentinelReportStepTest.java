/*
 * Copyright (c) 2026 LG Electronics, Inc. Licensed under the MIT License.
 * SPDX-License-Identifier: MIT
 */

package io.jenkins.plugins.sentinel;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SentinelReportStepTest {

    @Test
    void noRequiredConstructorParams() {
        final SentinelReportStep step = new SentinelReportStep();
        assertThat(step).isNotNull();
    }

    @Test
    void allFieldsNullByDefault() {
        final SentinelReportStep step = new SentinelReportStep();
        assertThat(step.getThreshold()).isNull();
        assertThat(step.getThresholdAction()).isNull();
        assertThat(step.getSourceDir()).isNull();
        assertThat(step.getOutputDir()).isNull();
        assertThat(step.getSentinelPath()).isNull();
    }

    @Test
    void thresholdSetterUpdatesValue() {
        final SentinelReportStep step = new SentinelReportStep();
        step.setThreshold(80.0);
        assertThat(step.getThreshold()).isEqualTo(80.0);
    }

    @Test
    void thresholdActionSetterUpdatesValue() {
        final SentinelReportStep step = new SentinelReportStep();
        step.setThresholdAction("UNSTABLE");
        assertThat(step.getThresholdAction())
                .isEqualTo("UNSTABLE");
    }

    @Test
    void sourceDirSetterUpdatesValue() {
        final SentinelReportStep step = new SentinelReportStep();
        step.setSourceDir("src/main");
        assertThat(step.getSourceDir()).isEqualTo("src/main");
    }

    @Test
    void outputDirSetterUpdatesValue() {
        final SentinelReportStep step = new SentinelReportStep();
        step.setOutputDir("my-report");
        assertThat(step.getOutputDir()).isEqualTo("my-report");
    }

    @Test
    void sentinelPathSetterUpdatesValue() {
        final SentinelReportStep step = new SentinelReportStep();
        step.setSentinelPath("/usr/bin/sentinel");
        assertThat(step.getSentinelPath())
                .isEqualTo("/usr/bin/sentinel");
    }
}
