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

    /** Executable name used when nothing else is configured. */
    static final String DEFAULT_PATH = "sentinel";

    private String sentinelPath = DEFAULT_PATH;

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
     * <p>Falls back to {@value #DEFAULT_PATH} whenever neither source
     * yields a usable value. An administrator who clears the global field
     * leaves it blank rather than null, and launching a blank program name
     * fails with an opaque error, so blank is treated as unset here.</p>
     *
     * @param jobOverride job-level override (may be null or blank)
     * @return effective sentinel executable path, never blank
     */
    public static String getEffectivePath(final String jobOverride) {
        if (SentinelEnvironment.isSet(jobOverride)) {
            return jobOverride;
        }
        final SentinelGlobalConfiguration global = get();
        final String configured =
                global != null ? global.getSentinelPath() : null;
        return SentinelEnvironment.orDefault(configured, DEFAULT_PATH);
    }
}
