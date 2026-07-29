/*
 * Copyright (c) 2026 LG Electronics, Inc. Licensed under the MIT License.
 * SPDX-License-Identifier: MIT
 */

package io.jenkins.plugins.sentinel;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class SentinelGlobalConfigurationTest {

    private static final String OVERRIDE = "/opt/custom/sentinel";
    private static final String GLOBAL = "/usr/local/bin/sentinel";

    @Test
    void defaultsToTheBareExecutableName(final JenkinsRule r) {
        assertThat(SentinelGlobalConfiguration.get().getSentinelPath())
                .isEqualTo(SentinelGlobalConfiguration.DEFAULT_PATH);
    }

    @Test
    void getReturnsTheRegisteredSingleton(final JenkinsRule r) {
        assertThat(SentinelGlobalConfiguration.get())
                .isNotNull()
                .isSameAs(SentinelGlobalConfiguration.get());
    }

    @Test
    void setterPersistsTheConfiguredPath(final JenkinsRule r)
            throws Exception {
        final SentinelGlobalConfiguration config =
                SentinelGlobalConfiguration.get();
        try {
            config.setSentinelPath(GLOBAL);
            assertThat(config.getSentinelPath()).isEqualTo(GLOBAL);
            // A fresh lookup sees the same value, so the setter's save()
            // reached the singleton rather than a copy.
            assertThat(SentinelGlobalConfiguration.get().getSentinelPath())
                    .isEqualTo(GLOBAL);
        } finally {
            config.setSentinelPath(
                    SentinelGlobalConfiguration.DEFAULT_PATH);
        }
    }

    @Test
    void jobOverrideWinsOverTheGlobalPath(final JenkinsRule r) {
        final SentinelGlobalConfiguration config =
                SentinelGlobalConfiguration.get();
        try {
            config.setSentinelPath(GLOBAL);
            assertThat(SentinelGlobalConfiguration
                    .getEffectivePath(OVERRIDE)).isEqualTo(OVERRIDE);
        } finally {
            config.setSentinelPath(
                    SentinelGlobalConfiguration.DEFAULT_PATH);
        }
    }

    @Test
    void globalPathAppliesWhenNoOverrideIsGiven(final JenkinsRule r) {
        final SentinelGlobalConfiguration config =
                SentinelGlobalConfiguration.get();
        try {
            config.setSentinelPath(GLOBAL);
            assertThat(SentinelGlobalConfiguration.getEffectivePath(null))
                    .isEqualTo(GLOBAL);
        } finally {
            config.setSentinelPath(
                    SentinelGlobalConfiguration.DEFAULT_PATH);
        }
    }

    @Test
    void blankOverrideFallsThroughToTheGlobalPath(final JenkinsRule r) {
        final SentinelGlobalConfiguration config =
                SentinelGlobalConfiguration.get();
        try {
            config.setSentinelPath(GLOBAL);
            assertThat(SentinelGlobalConfiguration.getEffectivePath(""))
                    .isEqualTo(GLOBAL);
            assertThat(SentinelGlobalConfiguration.getEffectivePath("  "))
                    .isEqualTo(GLOBAL);
        } finally {
            config.setSentinelPath(
                    SentinelGlobalConfiguration.DEFAULT_PATH);
        }
    }

    @Test
    void clearingTheGlobalPathStillYieldsAUsableExecutable(
            final JenkinsRule r) {
        // An administrator who empties the field leaves "" behind, and
        // launching a blank program name fails with an opaque error.
        final SentinelGlobalConfiguration config =
                SentinelGlobalConfiguration.get();
        try {
            config.setSentinelPath("");
            assertThat(SentinelGlobalConfiguration.getEffectivePath(null))
                    .isEqualTo(SentinelGlobalConfiguration.DEFAULT_PATH);
        } finally {
            config.setSentinelPath(
                    SentinelGlobalConfiguration.DEFAULT_PATH);
        }
    }
}
