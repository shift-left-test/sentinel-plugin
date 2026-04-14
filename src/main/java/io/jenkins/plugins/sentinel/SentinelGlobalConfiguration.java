/*
 * Copyright (c) 2026 LG Electronics, Inc. Licensed under the MIT License.
 * SPDX-License-Identifier: MIT
 */

package io.jenkins.plugins.sentinel;

import hudson.Extension;
import jenkins.model.GlobalConfiguration;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Global configuration for the Sentinel plugin.
 * Configurable via Manage Jenkins > System Configuration.
 */

@Extension
public class SentinelGlobalConfiguration extends GlobalConfiguration {

    private String sentinelPath = "sentinel";

    /**
     * Loads the saved configuration.
     */
    public SentinelGlobalConfiguration() {
        super();
        load();
    }

    /**
     * Returns the singleton instance of this configuration.
     *
     * @return the global configuration instance
     */
    public static SentinelGlobalConfiguration get() {
        return all().get(SentinelGlobalConfiguration.class);
    }

    /**
     * Returns the configured path to the sentinel executable.
     *
     * @return sentinel executable path
     */
    public String getSentinelPath() {
        return sentinelPath;
    }

    /**
     * Sets the path to the sentinel executable.
     *
     * @param sentinelPath path to sentinel
     */
    @DataBoundSetter
    public void setSentinelPath(final String sentinelPath) {
        this.sentinelPath = sentinelPath;
        save();
    }

    /**
     * Returns the effective sentinel path, considering
     * a per-job override.
     *
     * @param jobOverride job-level override (may be null or empty)
     * @return effective sentinel executable path
     */
    public static String getEffectivePath(final String jobOverride) {
        if (jobOverride != null && !jobOverride.isEmpty()) {
            return jobOverride;
        }
        final SentinelGlobalConfiguration global = get();
        if (global != null) {
            return global.getSentinelPath();
        }
        return "sentinel";
    }
}
