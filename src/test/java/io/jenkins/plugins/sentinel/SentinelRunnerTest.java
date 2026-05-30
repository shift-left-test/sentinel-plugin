/*
 * Copyright (c) 2026 LG Electronics, Inc. Licensed under the MIT License.
 * SPDX-License-Identifier: MIT
 */

package io.jenkins.plugins.sentinel;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import hudson.AbortException;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Proc;
import hudson.model.TaskListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SentinelRunnerTest {

    private Launcher.ProcStarter mockStarter(final Launcher launcher,
                                             final Proc proc)
            throws Exception {
        final Launcher.ProcStarter starter =
                mock(Launcher.ProcStarter.class);
        when(launcher.launch()).thenReturn(starter);
        when(starter.cmds(anyList())).thenReturn(starter);
        when(starter.envs(anyMap())).thenReturn(starter);
        when(starter.stdout(any(TaskListener.class)))
                .thenReturn(starter);
        when(starter.stderr(any(OutputStream.class)))
                .thenReturn(starter);
        when(starter.pwd(any(FilePath.class))).thenReturn(starter);
        when(starter.start()).thenReturn(proc);
        return starter;
    }

    @Test
    void throwsAbortExceptionOnNonZeroExit(
            @TempDir final Path tempDir) throws Exception {
        final Launcher launcher = mock(Launcher.class);
        final Proc proc = mock(Proc.class);
        mockStarter(launcher, proc);
        when(proc.join()).thenReturn(1);

        final TaskListener listener = mock(TaskListener.class);
        when(listener.getLogger()).thenReturn(System.out);
        final FilePath ws = new FilePath(tempDir.toFile());

        assertThatThrownBy(() -> SentinelRunner.run(
                List.of("sentinel"), Map.of(), ws, launcher, listener,
                new SentinelProcHandle()))
                .isInstanceOf(AbortException.class)
                .hasMessageContaining("exited with code 1");
    }

    @Test
    void registeredProcessIsKillableWhileRunningThenClearedAfter(
            @TempDir final Path tempDir) throws Exception {
        final Launcher launcher = mock(Launcher.class);
        final Proc proc = mock(Proc.class);
        mockStarter(launcher, proc);

        final SentinelProcHandle handle = new SentinelProcHandle();
        // Simulate an abort arriving while the process is running: at that
        // point the handle must hold the started proc so killQuietly() kills it.
        when(proc.join()).thenAnswer(inv -> {
            handle.killQuietly();
            return 0;
        });

        final TaskListener listener = mock(TaskListener.class);
        when(listener.getLogger()).thenReturn(System.out);
        final FilePath ws = new FilePath(tempDir.toFile());

        SentinelRunner.run(List.of("sentinel"), Map.of(), ws,
                launcher, listener, handle);

        // A late abort after completion must NOT kill anything: the handle
        // was cleared, so kill() was invoked exactly once (during the run).
        handle.killQuietly();
        verify(proc, times(1)).kill();
    }
}
