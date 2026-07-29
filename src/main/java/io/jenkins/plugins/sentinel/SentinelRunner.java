/*
 * Copyright (c) 2026 LG Electronics, Inc. Licensed under the MIT License.
 * SPDX-License-Identifier: MIT
 */

package io.jenkins.plugins.sentinel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import hudson.AbortException;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Proc;
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
     * <p>Takes the executable separately from its arguments so the
     * {@link SentinelCommandBuilder} methods stay purely about arguments
     * and no caller has to prepend the executable itself.</p>
     *
     * <p>The started {@link Proc} is registered with {@code procHandle}
     * so the owning step's {@code stop()} can kill it on abort.</p>
     *
     * @param sentinelCmd path to the sentinel executable
     * @param args        arguments to pass to sentinel
     * @param env         environment variables
     * @param ws          working directory
     * @param launcher    Jenkins launcher
     * @param listener    task listener for log output
     * @param procHandle  handle that receives the started process
     * @throws AbortException if the process exits with a non-zero code
     * @throws Exception      if the process fails to launch
     */
    static void run(
            final String sentinelCmd,
            final List<String> args,
            final Map<String, String> env,
            final FilePath ws,
            final Launcher launcher,
            final TaskListener listener,
            final SentinelProcHandle procHandle) throws Exception {
        final List<String> command = new ArrayList<>();
        command.add(sentinelCmd);
        command.addAll(args);

        listener.getLogger().println(
                "[Sentinel] Running: " + String.join(" ", command));

        final Proc proc = launcher.launch()
                .cmds(command)
                .envs(env)
                .stdout(listener)
                .stderr(listener.getLogger())
                .pwd(ws)
                .start();
        procHandle.set(proc);

        final int exitCode;
        try {
            exitCode = proc.join();
        } finally {
            procHandle.clear();
        }

        if (exitCode != 0) {
            throw new AbortException(
                    "sentinel exited with code " + exitCode);
        }
    }
}
