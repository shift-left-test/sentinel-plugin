/*
 * Copyright (c) 2026 LG Electronics, Inc. Licensed under the MIT License.
 * SPDX-License-Identifier: MIT
 */

package io.jenkins.plugins.sentinel;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import hudson.model.Result;
import io.jenkins.plugins.sentinel.model.MutationScore;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * End-to-end coverage of {@code sentinelReport}: unstash, merge, report,
 * result parsing, build action, and threshold judgment.
 */
@WithJenkins
class SentinelReportPipelineTest {

    private static final String RUN_STEP = "    sentinelRun()\n";

    @Test
    void singleRunProducesABuildActionWithTheParsedScore(
            final JenkinsRule r) throws Exception {
        final WorkflowRun run = runPipeline(r, "single",
                RUN_STEP
                        + "    sentinelReport()\n");

        r.assertBuildStatus(Result.SUCCESS, run);
        final MutationScore score = scoreOf(run);
        assertThat(score.killed()).isEqualTo(3);
        assertThat(score.survived()).isEqualTo(1);
        assertThat(score.skipped()).isEqualTo(1);
        r.assertLogContains("Score: 75.0%", run);
    }

    @Test
    void archivedHtmlReportIsServableFromTheBuild(
            final JenkinsRule r) throws Exception {
        final WorkflowRun run = runPipeline(r, "html",
                RUN_STEP
                        + "    sentinelReport()\n");

        final SentinelBuildAction action =
                run.getAction(SentinelBuildAction.class);
        assertThat(action.hasHtmlReport()).isTrue();
        assertThat(action.getRun()).isSameAs(run);
    }

    @Test
    void partitionedRunMergesEveryPartition(final JenkinsRule r)
            throws Exception {
        final WorkflowRun run = runPipeline(r, "partitioned",
                "    def n = 2\n"
                        + "    def branches = [:]\n"
                        + "    for (int i = 1; i <= n; i++) {\n"
                        + "      int idx = i\n"
                        + "      branches[\"P${idx}\"] = "
                        + "{ sentinelRun(partitionIndex: idx,"
                        + " partitionTotal: n) }\n"
                        + "    }\n"
                        + "    parallel branches\n"
                        + "    sentinelReport(partitionTotal: n)\n");

        r.assertBuildStatus(Result.SUCCESS, run);
        r.assertLogContains("--merge-partition=.sentinel-1", run);
        r.assertLogContains("--merge-partition=.sentinel-2", run);
        r.assertLogContains("--workspace=.sentinel-merged", run);
        assertThat(run.getAction(SentinelBuildAction.class)).isNotNull();
    }

    @Test
    void scoreBelowThresholdMarksTheBuildUnstable(final JenkinsRule r)
            throws Exception {
        final WorkflowRun run = runPipeline(r, "unstable",
                RUN_STEP
                        + "    sentinelReport(threshold: 80.0,"
                        + " thresholdAction: 'UNSTABLE')\n",
                Result.UNSTABLE);

        r.assertLogContains("is below threshold 80.0% -> UNSTABLE", run);
    }

    @Test
    void scoreAboveThresholdLeavesTheBuildGreen(final JenkinsRule r)
            throws Exception {
        final WorkflowRun run = runPipeline(r, "green",
                RUN_STEP
                        + "    sentinelReport(threshold: 70.0,"
                        + " thresholdAction: 'FAILURE')\n");

        r.assertBuildStatus(Result.SUCCESS, run);
    }

    @Test
    void outOfRangeThresholdFailsBeforeAnyUnstashHappens(
            final JenkinsRule r) throws Exception {
        // Regression: the report step used to skip SentinelConfigValidator
        // entirely, so 150.0 was accepted and every build then "failed" the
        // gate. It must now fail up front, before the expensive collection.
        final WorkflowRun run = runFailingPipeline(r, "badthreshold",
                RUN_STEP
                        + "    sentinelReport(threshold: 150.0,"
                        + " thresholdAction: 'FAILURE')\n");

        r.assertLogContains("threshold must be between 0.0 and 100.0", run);
        r.assertLogNotContains("Unstashing sentinel-partition-single", run);
    }

    @Test
    void thresholdWithoutAnActionFailsInsteadOfSilentlyPassing(
            final JenkinsRule r) throws Exception {
        // Regression: this used to run to completion with the gate simply
        // never applied, so a failing project reported success.
        final WorkflowRun run = runFailingPipeline(r, "noaction",
                RUN_STEP
                        + "    sentinelReport(threshold: 80.0)\n");

        r.assertLogContains("thresholdAction is required", run);
    }

    @Test
    void misspelledThresholdActionFailsBeforeCollectingPartitions(
            final JenkinsRule r) throws Exception {
        final WorkflowRun run = runFailingPipeline(r, "typo",
                RUN_STEP
                        + "    sentinelReport(threshold: 80.0,"
                        + " thresholdAction: 'UNSTBALE')\n");

        r.assertLogContains("Invalid ThresholdAction", run);
        r.assertLogNotContains("Unstashing sentinel-partition-single", run);
    }

    @Test
    void missingPartitionNamesTheBranchThatDidNotRun(final JenkinsRule r)
            throws Exception {
        final WorkflowRun run = runFailingPipeline(r, "missingpart",
                "    sentinelRun(partitionIndex: 1, partitionTotal: 2)\n"
                        + "    sentinelReport(partitionTotal: 2)\n");

        r.assertLogContains("partition 2 of 2", run);
    }

    @Test
    void explicitWorkspaceIsStashedAndCollected(final JenkinsRule r)
            throws Exception {
        // Regression: sentinelRun stashed .sentinel-{idx} regardless of the
        // configured workspace, so a pinned workspace stashed nothing.
        final WorkflowRun run = runPipeline(r, "explicitws",
                "    sentinelRun(partitionIndex: 1, partitionTotal: 1,"
                        + " workspace: 'my-ws')\n"
                        + "    sentinelReport(partitionTotal: 1)\n");

        r.assertBuildStatus(Result.SUCCESS, run);
        r.assertLogContains("--workspace=my-ws", run);
        r.assertLogContains("from my-ws", run);
        assertThat(scoreOf(run).killed()).isEqualTo(3);
    }

    private static MutationScore scoreOf(final WorkflowRun run) {
        return run.getAction(SentinelBuildAction.class)
                .getResult().overallScore();
    }

    private static WorkflowRun runPipeline(final JenkinsRule r,
                                           final String name,
                                           final String body)
            throws Exception {
        return runPipeline(r, name, body, Result.SUCCESS);
    }

    private static WorkflowRun runPipeline(final JenkinsRule r,
                                           final String name,
                                           final String body,
                                           final Result expected)
            throws Exception {
        return r.assertBuildStatus(expected,
                job(r, name, body).scheduleBuild2(0));
    }

    private static WorkflowRun runFailingPipeline(final JenkinsRule r,
                                                  final String name,
                                                  final String body)
            throws Exception {
        return runPipeline(r, name, body, Result.FAILURE);
    }

    private static WorkflowJob job(final JenkinsRule r,
                                   final String name,
                                   final String body) throws Exception {
        final WorkflowJob job = r.createProject(WorkflowJob.class, name);
        job.setDefinition(new CpsFlowDefinition(
                "node {\n"
                        // Stands in for the checkout scm a real pipeline
                        // does: without it the node workspace directory
                        // does not exist yet and no process can be
                        // launched with it as its working directory.
                        + "  writeFile file: '.gitkeep', text: ''\n"
                        + "  withEnv(['SENTINEL_PATH="
                        + fakeSentinel(r) + "']) {\n"
                        + body
                        + "  }\n"
                        + "}\n", true));
        return job;
    }

    /**
     * Writes a fake sentinel that honours {@code --workspace} and
     * {@code --output-dir} and emits a fixed mutations.xml plus an HTML
     * report, so the report step has real files to collect and parse.
     */
    private static String fakeSentinel(final JenkinsRule r)
            throws Exception {
        final File script = r.jenkins.getRootDir().toPath()
                .resolve("fake-sentinel-report.sh").toFile();
        Files.writeString(script.toPath(),
                "#!/bin/sh\n"
                + "out=\"\"\n"
                + "for a in \"$@\"; do\n"
                + "  case \"$a\" in\n"
                + "    --workspace=*) mkdir -p \"${a#--workspace=}\" ;;\n"
                + "    --output-dir=*) out=\"${a#--output-dir=}\" ;;\n"
                + "  esac\n"
                + "done\n"
                + "echo \"FAKE_SENTINEL $*\"\n"
                + "if [ -n \"$out\" ]; then\n"
                + "  mkdir -p \"$out\"\n"
                + "  echo '<html>report</html>' > \"$out/index.html\"\n"
                + "  cat > \"$out/mutations.xml\" <<'XML'\n"
                + mutationsXml()
                + "XML\n"
                + "fi\n"
                + "exit 0\n",
                StandardCharsets.UTF_8);
        if (!script.setExecutable(true)) {
            throw new IllegalStateException(
                    "could not mark fake sentinel executable");
        }
        return script.getAbsolutePath();
    }

    /** 3 killed, 1 survived, 1 skipped: a 75.0% score. */
    private static String mutationsXml() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <mutations>
                  <mutation detected="true">
                    <sourceFile>foo.cpp</sourceFile>
                    <sourceFilePath>src/foo.cpp</sourceFilePath>
                    <mutatedClass>Foo</mutatedClass>
                    <mutatedMethod>add</mutatedMethod>
                    <lineNumber>10</lineNumber>
                    <mutator>AOR</mutator>
                    <killingTest>FooTest</killingTest>
                  </mutation>
                  <mutation detected="true">
                    <sourceFile>foo.cpp</sourceFile>
                    <sourceFilePath>src/foo.cpp</sourceFilePath>
                    <mutatedClass>Foo</mutatedClass>
                    <mutatedMethod>sub</mutatedMethod>
                    <lineNumber>20</lineNumber>
                    <mutator>AOR</mutator>
                    <killingTest>FooTest</killingTest>
                  </mutation>
                  <mutation detected="true">
                    <sourceFile>bar.cpp</sourceFile>
                    <sourceFilePath>src/bar.cpp</sourceFilePath>
                    <mutatedClass>Bar</mutatedClass>
                    <mutatedMethod>run</mutatedMethod>
                    <lineNumber>5</lineNumber>
                    <mutator>ROR</mutator>
                    <killingTest>BarTest</killingTest>
                  </mutation>
                  <mutation detected="false">
                    <sourceFile>foo.cpp</sourceFile>
                    <sourceFilePath>src/foo.cpp</sourceFilePath>
                    <mutatedClass>Foo</mutatedClass>
                    <mutatedMethod>multiply</mutatedMethod>
                    <lineNumber>30</lineNumber>
                    <mutator>AOR</mutator>
                    <killingTest></killingTest>
                  </mutation>
                  <mutation detected="skip">
                    <sourceFile>bar.cpp</sourceFile>
                    <sourceFilePath>src/bar.cpp</sourceFilePath>
                    <mutatedClass>Bar</mutatedClass>
                    <mutatedMethod>init</mutatedMethod>
                    <lineNumber>1</lineNumber>
                    <mutator>LCR</mutator>
                  </mutation>
                </mutations>
                """;
    }
}
