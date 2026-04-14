/*
 * Copyright (c) 2026 LG Electronics, Inc. Licensed under the MIT License.
 * SPDX-License-Identifier: MIT
 */

package io.jenkins.plugins.sentinel;

import java.util.List;
import java.util.Map;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;

/**
 * Utility for executing sentinel CLI commands via the Jenkins launcher.
 */

final class SentinelRunner {

    private SentinelRunner() {
    }

    /**
     * Runs a sentinel command and throws if the exit code is non-zero.
     *
     * @param args     full argument list including the sentinel executable
     * @param env      environment variables
     * @param ws       working directory
     * @param launcher Jenkins launcher
     * @param listener task listener for log output
     * @throws Exception if the process exits with a non-zero code
     *                   or fails to launch
     */
    static void run(
            final List<String> args,
            final Map<String, String> env,
            final FilePath ws,
            final Launcher launcher,
            final TaskListener listener) throws Exception {
        listener.getLogger().println(
                "[Sentinel] Running: " + String.join(" ", args));

        final int exitCode = launcher.launch()
                .cmds(args)
                .envs(env)
                .stdout(listener)
                .stderr(listener.getLogger())
                .pwd(ws)
                .join();

        if (exitCode != 0) {
            throw new RuntimeException(
                    "sentinel exited with code " + exitCode);
        }
    }
}
