/*
 * Copyright (c) 2026 LG Electronics, Inc. Licensed under the MIT License.
 * SPDX-License-Identifier: MIT
 */

package io.jenkins.plugins.sentinel;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import hudson.FilePath;
import hudson.Launcher;
import hudson.Proc;
import hudson.model.TaskListener;
import io.jenkins.plugins.sentinel.model.MutationScore;
import io.jenkins.plugins.sentinel.model.SentinelResult;
import org.mockito.ArgumentCaptor;

/**
 * Shared fixtures for the sentinel tests.
 *
 * <p>Exists because the launcher stub, the logging listener and the
 * "build action from three counts" construction were each copied into
 * three or more test classes, so a change to any of them meant editing
 * every copy.</p>
 */
final class SentinelTestSupport {

    private SentinelTestSupport() {
    }

    /** A stubbed launcher together with the parts tests assert against. */
    record LauncherStub(Launcher launcher,
                        Launcher.ProcStarter starter,
                        Proc proc) {
    }

    /**
     * Returns a listener that writes to {@code out}.
     *
     * @param out destination for the build log
     * @return the listener
     */
    static TaskListener loggingListener(final OutputStream out) {
        final TaskListener listener = mock(TaskListener.class);
        when(listener.getLogger()).thenReturn(
                new PrintStream(out, true, StandardCharsets.UTF_8));
        return listener;
    }

    /**
     * Returns a launcher whose every launch exits with {@code exitCode}.
     *
     * <p>The same starter is returned from each {@code launch()}, so
     * {@link #commands(LauncherStub)} sees every command in order.</p>
     *
     * @param exitCode exit code every launched process reports
     * @return the stub
     * @throws Exception if stubbing fails
     */
    static LauncherStub mockLauncher(final int exitCode) throws Exception {
        final Launcher launcher = mock(Launcher.class);
        final Launcher.ProcStarter starter =
                mock(Launcher.ProcStarter.class);
        final Proc proc = mock(Proc.class);
        when(launcher.launch()).thenReturn(starter);
        when(starter.cmds(anyList())).thenReturn(starter);
        when(starter.envs(anyMap())).thenReturn(starter);
        when(starter.stdout(any(TaskListener.class))).thenReturn(starter);
        when(starter.stderr(any(OutputStream.class))).thenReturn(starter);
        when(starter.pwd(any(FilePath.class))).thenReturn(starter);
        when(starter.start()).thenReturn(proc);
        when(proc.join()).thenReturn(exitCode);
        return new LauncherStub(launcher, starter, proc);
    }

    /**
     * Returns every command line the launcher was asked to run, in order.
     *
     * @param stub the launcher stub
     * @return the captured command lines
     */
    static List<List<String>> commands(final LauncherStub stub) {
        @SuppressWarnings("unchecked")
        final ArgumentCaptor<List<String>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(stub.starter(), atLeastOnce()).cmds(captor.capture());
        return captor.getAllValues();
    }

    /**
     * Returns a build action carrying only an overall score.
     *
     * @param killed   killed count
     * @param survived survived count
     * @param skipped  skipped count
     * @return the action
     */
    static SentinelBuildAction buildAction(final int killed,
                                           final int survived,
                                           final int skipped) {
        return new SentinelBuildAction(new SentinelResult(
                new MutationScore(killed, survived, skipped),
                List.of(), List.of()));
    }

    /**
     * Returns a minimal mutations.xml holding one mutation per status.
     *
     * @param killed   killed mutations to emit
     * @param survived survived mutations to emit
     * @param skipped  skipped mutations to emit
     * @return the XML document
     */
    static String mutationsXml(final int killed,
                               final int survived,
                               final int skipped) {
        final StringBuilder xml = new StringBuilder(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<mutations>\n");
        appendMutations(xml, killed, "true", "AOR", "FooTest");
        appendMutations(xml, survived, "false", "ROR", null);
        appendMutations(xml, skipped, "skip", "LCR", null);
        return xml.append("</mutations>\n").toString();
    }

    private static void appendMutations(final StringBuilder xml,
                                        final int count,
                                        final String detected,
                                        final String mutator,
                                        final String killingTest) {
        for (int i = 0; i < count; i++) {
            xml.append("  <mutation detected=\"").append(detected)
                    .append("\">\n")
                    .append("    <sourceFile>foo.cpp</sourceFile>\n")
                    .append("    <sourceFilePath>src/foo.cpp"
                            + "</sourceFilePath>\n")
                    .append("    <mutatedClass>Foo</mutatedClass>\n")
                    .append("    <mutatedMethod>bar</mutatedMethod>\n")
                    .append("    <lineNumber>").append(i + 1)
                    .append("</lineNumber>\n")
                    .append("    <mutator>").append(mutator)
                    .append("</mutator>\n");
            if (killingTest != null) {
                xml.append("    <killingTest>").append(killingTest)
                        .append("</killingTest>\n");
            }
            xml.append("  </mutation>\n");
        }
    }
}
