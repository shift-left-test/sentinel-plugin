/*
 * Copyright (c) 2026 LG Electronics, Inc. Licensed under the MIT License.
 * SPDX-License-Identifier: MIT
 */

package io.jenkins.plugins.sentinel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Proc;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SentinelStepExecutionTest {

    /** Minimal concrete execution: the base class is abstract. */
    private static final class TestExecution
            extends SentinelStepExecution<Void> {

        private static final long serialVersionUID = 1L;

        TestExecution(final StepContext context) {
            super(context);
        }

        @Override
        protected Void run() {
            return null;
        }
    }

    @Test
    void stopKillsTheRegisteredProcess() throws Exception {
        final TestExecution execution =
                new TestExecution(mock(StepContext.class));
        final Proc proc = mock(Proc.class);
        execution.procHandle.set(proc);

        execution.stop(new InterruptedException("aborted"));

        verify(proc).kill();
    }

    @Test
    void stopIsHarmlessWhenNoProcessIsRunning() {
        // Abort before the first launch, or after the last one finished.
        final TestExecution execution =
                new TestExecution(mock(StepContext.class));

        assertThatCode(() ->
                execution.stop(new InterruptedException("aborted")))
                .doesNotThrowAnyException();
    }

    @Test
    void inputsReadsEveryDeclaredContextType(
            @TempDir final Path tempDir) throws Exception {
        final StepContext context = mock(StepContext.class);
        final FilePath ws = new FilePath(tempDir.toFile());
        final Launcher launcher = mock(Launcher.class);
        final TaskListener listener = SentinelTestSupport
                .loggingListener(new ByteArrayOutputStream());
        final EnvVars env = new EnvVars();
        final Run<?, ?> build = mock(Run.class);
        when(context.get(FilePath.class)).thenReturn(ws);
        when(context.get(Launcher.class)).thenReturn(launcher);
        when(context.get(TaskListener.class)).thenReturn(listener);
        when(context.get(EnvVars.class)).thenReturn(env);
        when(context.get(Run.class)).thenReturn((Run) build);

        final SentinelStepExecution.Inputs in =
                new TestExecution(context).inputs();

        assertThat(in.ws()).isSameAs(ws);
        assertThat(in.launcher()).isSameAs(launcher);
        assertThat(in.listener()).isSameAs(listener);
        assertThat(in.env()).isSameAs(env);
        assertThat(in.build()).isSameAs(build);
    }

    @Test
    void inputsWarnsAboutUnrecognizedSentinelVariables(
            @TempDir final Path tempDir) throws Exception {
        final ByteArrayOutputStream log = new ByteArrayOutputStream();
        final EnvVars env = new EnvVars();
        env.put("SENTINEL_TIMOUT", "300");
        // Build every stub before wiring the context: Mockito rejects
        // nested stubbing inside a when(...) argument.
        final Launcher launcher = mock(Launcher.class);
        final TaskListener listener =
                SentinelTestSupport.loggingListener(log);
        final Run<?, ?> build = mock(Run.class);
        final StepContext context = mock(StepContext.class);
        when(context.get(FilePath.class))
                .thenReturn(new FilePath(tempDir.toFile()));
        when(context.get(Launcher.class)).thenReturn(launcher);
        when(context.get(TaskListener.class)).thenReturn(listener);
        when(context.get(EnvVars.class)).thenReturn(env);
        when(context.get(Run.class)).thenReturn((Run) build);

        new TestExecution(context).inputs();

        assertThat(log.toString(StandardCharsets.UTF_8))
                .contains("SENTINEL_TIMOUT");
    }

    @Test
    void requiredContextMatchesWhatInputsReads() {
        // The descriptors publish this set; inputs() has to stay in step
        // with it or the step fails at runtime instead of at configuration
        // time.
        assertThat(SentinelStepExecution.REQUIRED_CONTEXT)
                .containsExactlyInAnyOrder(
                        FilePath.class, Launcher.class, TaskListener.class,
                        EnvVars.class, Run.class);
    }
}
