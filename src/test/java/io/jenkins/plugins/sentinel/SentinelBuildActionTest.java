/*
 * Copyright (c) 2026 LG Electronics, Inc. Licensed under the MIT License.
 * SPDX-License-Identifier: MIT
 */

package io.jenkins.plugins.sentinel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import hudson.model.Run;
import io.jenkins.plugins.sentinel.model.FileMutationResult;
import io.jenkins.plugins.sentinel.model.MutationEntry;
import io.jenkins.plugins.sentinel.model.MutationScore;
import io.jenkins.plugins.sentinel.model.SentinelResult;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.mockito.ArgumentCaptor;

class SentinelBuildActionTest {

    private static final int KILLED_80 = 80;
    private static final int SURVIVED_20 = 20;
    private static final int SKIPPED_5 = 5;
    private static final int LINE_10 = 10;
    private static final int LINE_20 = 20;
    private static final int LINE_30 = 30;
    private static final String MUTATOR_AOR = "AOR";
    private static final String MUTATOR_ROR = "ROR";
    private static final String CSP_HEADER = "Content-Security-Policy";
    private static final String CSP_PROPERTY =
            "io.jenkins.plugins.sentinel.reportCsp";
    private static final String JENKINS_CSP_PROPERTY =
            "hudson.model.DirectoryBrowserSupport.CSP";
    private static final String CUSTOM_CSP = "default-src 'none'";

    @Test
    void formattedScoreReturnsOneDecimalPlace() {
        final SentinelBuildAction action = createAction(KILLED_80, SURVIVED_20, 0);
        assertThat(action.getFormattedScore()).isEqualTo("80.0");
    }

    @Test
    void formattedScoreWithFractionalValue() {
        final SentinelBuildAction action = createAction(2, 1, 0);
        assertThat(action.getFormattedScore()).isEqualTo("66.7");
    }

    @Test
    void scoreColorGreenWhenAbove80() {
        final SentinelBuildAction action = createAction(KILLED_80, SURVIVED_20, 0);
        assertThat(action.getScoreColor()).isEqualTo("#1ea64b");
    }

    @Test
    void scoreColorGreenWhenExactly80() {
        final SentinelBuildAction action = createAction(4, 1, 0);
        assertThat(action.getScoreColor()).isEqualTo("#1ea64b");
    }

    @Test
    void scoreColorOrangeWhenAbove50() {
        final SentinelBuildAction action = createAction(3, 2, 0);
        assertThat(action.getScoreColor()).isEqualTo("#fe820a");
    }

    @Test
    void scoreColorOrangeWhenExactly50() {
        final SentinelBuildAction action = createAction(1, 1, 0);
        assertThat(action.getScoreColor()).isEqualTo("#fe820a");
    }

    @Test
    void scoreColorRedWhenBelow50() {
        final SentinelBuildAction action = createAction(1, 3, 0);
        assertThat(action.getScoreColor()).isEqualTo("#e6001f");
    }

    @Test
    void scoreColorRedWhenZero() {
        final SentinelBuildAction action = createAction(0, 0, 0);
        assertThat(action.getScoreColor()).isEqualTo("#e6001f");
    }

    @Test
    void totalWithSkippedIncludesAllThreeCounts() {
        final SentinelBuildAction action = createAction(KILLED_80, SURVIVED_20, SKIPPED_5);
        assertThat(action.getTotalWithSkipped())
                .isEqualTo(KILLED_80 + SURVIVED_20 + SKIPPED_5);
    }

    @Test
    void killedPercentCalculatesCorrectly() {
        final SentinelBuildAction action = createAction(KILLED_80, SURVIVED_20, 0);
        assertThat(action.getKilledPercent()).isEqualTo(KILLED_80);
    }

    @Test
    void survivedPercentCalculatesCorrectly() {
        final SentinelBuildAction action = createAction(KILLED_80, SURVIVED_20, 0);
        assertThat(action.getSurvivedPercent()).isEqualTo(SURVIVED_20);
    }

    @Test
    void skippedPercentCalculatesCorrectly() {
        final SentinelBuildAction action = createAction(KILLED_80, 0, SURVIVED_20);
        assertThat(action.getSkippedPercent()).isEqualTo(SURVIVED_20);
    }

    @Test
    void percentsReturnZeroWhenTotalIsZero() {
        final SentinelBuildAction action = createAction(0, 0, 0);
        assertThat(action.getKilledPercent()).isZero();
        assertThat(action.getSurvivedPercent()).isZero();
        assertThat(action.getSkippedPercent()).isZero();
    }

    @Test
    void percentsWithSkippedIncluded() {
        final SentinelBuildAction action = createAction(KILLED_80, SURVIVED_20, SKIPPED_5);
        final int total = KILLED_80 + SURVIVED_20 + SKIPPED_5;
        assertThat(action.getKilledPercent())
                .isCloseTo(KILLED_80 * 100 / total, within(1));
        assertThat(action.getSurvivedPercent())
                .isCloseTo(SURVIVED_20 * 100 / total, within(1));
        assertThat(action.getSkippedPercent())
                .isCloseTo(SKIPPED_5 * 100 / total, within(1));
    }

    @Test
    void mutatorDistributionCountsByMutator() {
        final SentinelBuildAction action = createActionWithEntries(List.of(
                entry(MUTATOR_AOR, LINE_10),
                entry(MUTATOR_AOR, LINE_20),
                entry(MUTATOR_ROR, LINE_30)));
        final Map<String, Integer> dist = action.getMutatorDistribution();
        assertThat(dist).containsEntry(MUTATOR_AOR, 2);
        assertThat(dist).containsEntry(MUTATOR_ROR, 1);
    }

    @Test
    void mutatorDistributionEmptyWhenNoEntries() {
        final SentinelBuildAction action = createActionWithEntries(List.of());
        assertThat(action.getMutatorDistribution()).isEmpty();
    }

    @Test
    void mutatorDistributionJsonFormatsCorrectly() {
        final SentinelBuildAction action = createActionWithEntries(List.of(
                entry(MUTATOR_AOR, LINE_10),
                entry(MUTATOR_AOR, LINE_20),
                entry(MUTATOR_ROR, LINE_30)));
        final String json = action.getMutatorDistributionJson();
        assertThat(json).contains("\"name\"");
        assertThat(json).contains("\"value\"");
        assertThat(json).contains(MUTATOR_AOR);
        assertThat(json).contains(MUTATOR_ROR);
    }

    @Test
    void scoreDistributionJsonContainsAllStatuses() {
        final SentinelBuildAction action = createAction(
                KILLED_80, SURVIVED_20, SKIPPED_5);
        final String json = action.getScoreDistributionJson();
        assertThat(json).contains("\"Killed\"").contains("80");
        assertThat(json).contains("\"Survived\"").contains("20");
        assertThat(json).contains("\"Skipped\"").contains("5");
    }

    @Test
    void fileCountReturnsNumberOfFileResults() {
        final List<FileMutationResult> files = List.of(
                new FileMutationResult("Foo.java", new MutationScore(1, 0, 0)),
                new FileMutationResult("Bar.java", new MutationScore(1, 0, 0)));
        final SentinelResult result = new SentinelResult(
                new MutationScore(2, 0, 0), files, List.of());
        final SentinelBuildAction action = new SentinelBuildAction(result);
        assertThat(action.getFileCount()).isEqualTo(2);
    }

    @Test
    void entryCountReturnsNumberOfEntries() {
        final SentinelBuildAction action = createActionWithEntries(List.of(
                entry(MUTATOR_AOR, LINE_10),
                entry(MUTATOR_ROR, LINE_20)));
        assertThat(action.getEntryCount()).isEqualTo(2);
    }

    @Test
    void iconFileNameReturnsGraphPng() {
        final SentinelBuildAction action = createAction(1, 0, 0);
        assertThat(action.getIconFileName()).isEqualTo("graph.png");
    }

    @Test
    void displayNameReturnsMutationReport() {
        final SentinelBuildAction action = createAction(1, 0, 0);
        assertThat(action.getDisplayName()).isEqualTo("Sentinel Report");
    }

    @Test
    void urlNameReturnsSentinel() {
        final SentinelBuildAction action = createAction(1, 0, 0);
        assertThat(action.getUrlName()).isEqualTo("sentinel");
    }

    @Test
    void doDynamicServesArchivedReport(
            @TempDir final Path tempDir) throws Exception {
        final SentinelBuildAction action =
                actionWithArchivedReport(tempDir);
        final ByteArrayOutputStream body = new ByteArrayOutputStream();
        final StaplerResponse2 rsp = mockResponse(body);

        action.doDynamic(mock(StaplerRequest2.class), rsp);

        verify(rsp).setHeader(eq(CSP_HEADER), anyString());
        assertThat(body.toString(StandardCharsets.UTF_8))
                .contains("report");
    }

    @Test
    void doDynamicDefaultCspSandboxesWithoutSameOrigin(
            @TempDir final Path tempDir) throws Exception {
        final SentinelBuildAction action =
                actionWithArchivedReport(tempDir);
        final StaplerResponse2 rsp =
                mockResponse(new ByteArrayOutputStream());

        action.doDynamic(mock(StaplerRequest2.class), rsp);

        // The report renders itself with inline JS, so scripts must
        // run - but only in an opaque origin, isolated from Jenkins.
        assertThat(capturedCsp(rsp))
                .contains("allow-scripts")
                .contains("script-src 'unsafe-inline'")
                .contains("style-src 'unsafe-inline'")
                .doesNotContain("allow-same-origin");
    }

    @Test
    void doDynamicHonorsPluginCspProperty(
            @TempDir final Path tempDir) throws Exception {
        System.setProperty(CSP_PROPERTY, CUSTOM_CSP);
        try {
            final SentinelBuildAction action =
                    actionWithArchivedReport(tempDir);
            final StaplerResponse2 rsp =
                    mockResponse(new ByteArrayOutputStream());

            action.doDynamic(mock(StaplerRequest2.class), rsp);

            assertThat(capturedCsp(rsp)).isEqualTo(CUSTOM_CSP);
        } finally {
            System.clearProperty(CSP_PROPERTY);
        }
    }

    @Test
    void doDynamicSends404WhenReportMissing(
            @TempDir final Path tempDir) throws Exception {
        // run set, but no archived report file exists
        final SentinelBuildAction action = actionForRootDir(tempDir);
        final StaplerResponse2 rsp =
                mockResponse(new ByteArrayOutputStream());

        action.doDynamic(mock(StaplerRequest2.class), rsp);

        verify(rsp).setHeader(eq(CSP_HEADER), anyString());
        verify(rsp).sendError(
                eq(StaplerResponse2.SC_NOT_FOUND), anyString());
    }

    @Test
    void doDynamicSends404WhenRunNotAssociated() throws Exception {
        // run is null (action not attached to a build)
        final SentinelBuildAction action = createAction(1, 0, 0);

        final StaplerRequest2 req = mock(StaplerRequest2.class);
        final StaplerResponse2 rsp = mock(StaplerResponse2.class);

        action.doDynamic(req, rsp);

        verify(rsp).sendError(
                eq(StaplerResponse2.SC_NOT_FOUND), anyString());
    }

    @Test
    void onAttachedBindsTheRun() {
        final SentinelBuildAction action = createAction(1, 0, 0);
        final Run<?, ?> run = mock(Run.class);

        action.onAttached(run);

        assertThat(action.getRun()).isSameAs(run);
    }

    @Test
    void onLoadRebindsTheRunAfterAControllerRestart() {
        final SentinelBuildAction action = createAction(1, 0, 0);
        final Run<?, ?> run = mock(Run.class);

        action.onLoad(run);

        assertThat(action.getRun()).isSameAs(run);
    }

    @Test
    void getRunIsNullBeforeTheActionIsAttached() {
        assertThat(createAction(1, 0, 0).getRun()).isNull();
    }

    @Test
    void hasHtmlReportIsFalseWithoutARun() {
        assertThat(createAction(1, 0, 0).hasHtmlReport()).isFalse();
    }

    @Test
    void hasHtmlReportIsFalseWhenNothingWasArchived(
            @TempDir final Path tempDir) {
        assertThat(actionForRootDir(tempDir).hasHtmlReport()).isFalse();
    }

    @Test
    void hasHtmlReportIsTrueOnceArchived(@TempDir final Path tempDir)
            throws Exception {
        assertThat(actionWithArchivedReport(tempDir).hasHtmlReport())
                .isTrue();
    }

    @Test
    void emptyCspPropertyDisablesTheHeaderEntirely(
            @TempDir final Path tempDir) throws Exception {
        System.setProperty(CSP_PROPERTY, "");
        try {
            final SentinelBuildAction action =
                    actionWithArchivedReport(tempDir);
            final StaplerResponse2 rsp =
                    mockResponse(new ByteArrayOutputStream());

            action.doDynamic(mock(StaplerRequest2.class), rsp);

            verify(rsp, never()).setHeader(eq(CSP_HEADER), anyString());
        } finally {
            System.clearProperty(CSP_PROPERTY);
        }
    }

    @Test
    void jenkinsWideCspPropertyAppliesWhenNoPluginPropertyIsSet(
            @TempDir final Path tempDir) throws Exception {
        System.setProperty(JENKINS_CSP_PROPERTY, CUSTOM_CSP);
        try {
            final SentinelBuildAction action =
                    actionWithArchivedReport(tempDir);
            final StaplerResponse2 rsp =
                    mockResponse(new ByteArrayOutputStream());

            action.doDynamic(mock(StaplerRequest2.class), rsp);

            assertThat(capturedCsp(rsp)).isEqualTo(CUSTOM_CSP);
        } finally {
            System.clearProperty(JENKINS_CSP_PROPERTY);
        }
    }

    @Test
    void pluginCspPropertyBeatsTheJenkinsWideOne(
            @TempDir final Path tempDir) throws Exception {
        System.setProperty(JENKINS_CSP_PROPERTY, "default-src 'self'");
        System.setProperty(CSP_PROPERTY, CUSTOM_CSP);
        try {
            final SentinelBuildAction action =
                    actionWithArchivedReport(tempDir);
            final StaplerResponse2 rsp =
                    mockResponse(new ByteArrayOutputStream());

            action.doDynamic(mock(StaplerRequest2.class), rsp);

            assertThat(capturedCsp(rsp)).isEqualTo(CUSTOM_CSP);
        } finally {
            System.clearProperty(CSP_PROPERTY);
            System.clearProperty(JENKINS_CSP_PROPERTY);
        }
    }

    /** Action whose build root dir holds an archived HTML report. */
    private SentinelBuildAction actionWithArchivedReport(final Path tempDir)
            throws IOException {
        final Path archive = tempDir.resolve(
                SentinelEnvironment.ARCHIVE_DIR);
        Files.createDirectories(archive);
        Files.writeString(
                archive.resolve(SentinelEnvironment.HTML_REPORT_FILE),
                "<html>report</html>");
        return actionForRootDir(tempDir);
    }

    /** Action attached to a build whose root dir is {@code tempDir}. */
    private SentinelBuildAction actionForRootDir(final Path tempDir) {
        final SentinelBuildAction action = createAction(1, 0, 0);
        final Run<?, ?> run = mock(Run.class);
        when(run.getRootDir()).thenReturn(tempDir.toFile());
        action.setRun(run);
        return action;
    }

    private static StaplerResponse2 mockResponse(final OutputStream body)
            throws IOException {
        final StaplerResponse2 rsp = mock(StaplerResponse2.class);
        when(rsp.getOutputStream())
                .thenReturn(new CapturingServletOutputStream(body));
        return rsp;
    }

    private static String capturedCsp(final StaplerResponse2 rsp) {
        final ArgumentCaptor<String> csp =
                ArgumentCaptor.forClass(String.class);
        verify(rsp).setHeader(eq(CSP_HEADER), csp.capture());
        return csp.getValue();
    }

    private static final class CapturingServletOutputStream
            extends ServletOutputStream {
        private final OutputStream delegate;

        CapturingServletOutputStream(final OutputStream out) {
            super();
            this.delegate = out;
        }

        @Override
        public void write(final int b) throws IOException {
            delegate.write(b);
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setWriteListener(final WriteListener listener) {
            // no-op for test
        }
    }

    private SentinelBuildAction createAction(
            final int killed, final int survived, final int skipped) {
        final MutationScore score = new MutationScore(killed, survived, skipped);
        final SentinelResult result = new SentinelResult(
                score, List.of(), List.of());
        return new SentinelBuildAction(result);
    }

    private SentinelBuildAction createActionWithEntries(
            final List<MutationEntry> entries) {
        final MutationScore score = new MutationScore(
                entries.size(), 0, 0);
        final SentinelResult result = new SentinelResult(
                score, List.of(), entries);
        return new SentinelBuildAction(result);
    }

    private MutationEntry entry(final String mutator, final int line) {
        return new MutationEntry(
                "Foo.java", "src/Foo.java",
                "Foo", "bar", line, mutator,
                true, false, "FooTest::testBar");
    }
}
