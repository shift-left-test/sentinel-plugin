/*
 * Copyright (c) 2026 LG Electronics, Inc. Licensed under the MIT License.
 * SPDX-License-Identifier: MIT
 */

package io.jenkins.plugins.sentinel;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import hudson.Proc;
import org.junit.jupiter.api.Test;

class SentinelProcHandleTest {

    @Test
    void killQuietlyKillsRegisteredProcess() throws Exception {
        final SentinelProcHandle handle = new SentinelProcHandle();
        final Proc proc = mock(Proc.class);
        handle.set(proc);

        handle.killQuietly();

        verify(proc).kill();
    }

    @Test
    void killQuietlyIsNoOpWhenNoProcessRegistered() {
        final SentinelProcHandle handle = new SentinelProcHandle();
        assertThatCode(handle::killQuietly).doesNotThrowAnyException();
    }

    @Test
    void killQuietlySwallowsKillFailure() throws Exception {
        final SentinelProcHandle handle = new SentinelProcHandle();
        final Proc proc = mock(Proc.class);
        doThrow(new java.io.IOException("boom")).when(proc).kill();
        handle.set(proc);

        assertThatCode(handle::killQuietly).doesNotThrowAnyException();
        verify(proc).kill();
    }

    @Test
    void killQuietlyDoesNotKillAfterProcessCleared() throws Exception {
        final SentinelProcHandle handle = new SentinelProcHandle();
        final Proc proc = mock(Proc.class);
        handle.set(proc);
        handle.clear();

        handle.killQuietly();

        verify(proc, never()).kill();
    }
}
