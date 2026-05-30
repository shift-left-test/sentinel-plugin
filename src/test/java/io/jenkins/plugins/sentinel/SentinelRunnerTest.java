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
import static org.mockito.Mockito.when;

import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import hudson.AbortException;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SentinelRunnerTest {

    @Test
    void throwsAbortExceptionOnNonZeroExit(
            @TempDir final Path tempDir) throws Exception {
        final Launcher launcher = mock(Launcher.class);
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
        when(starter.join()).thenReturn(1);

        final TaskListener listener = mock(TaskListener.class);
        when(listener.getLogger()).thenReturn(System.out);

        final FilePath ws = new FilePath(tempDir.toFile());

        assertThatThrownBy(() -> SentinelRunner.run(
                List.of("sentinel"), Map.of(), ws, launcher, listener))
                .isInstanceOf(AbortException.class)
                .hasMessageContaining("exited with code 1");
    }
}
