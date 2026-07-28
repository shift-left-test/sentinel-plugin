/*
 * Copyright (c) 2026 LG Electronics, Inc. Licensed under the MIT License.
 * SPDX-License-Identifier: MIT
 */

package io.jenkins.plugins.sentinel;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.plugins.structs.describable.DescribableModel;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class SentinelPartitionPipelineTest {

    @Test
    void partitionTotalIsABoundParameterOnRunStep(final JenkinsRule r)
            throws Exception {
        final DescribableModel<SentinelRunStep> model =
                new DescribableModel<>(SentinelRunStep.class);
        final SentinelRunStep step = model.instantiate(
                Map.of("partitionIndex", 2, "partitionTotal", 4));
        assertThat(step.getPartitionIndex()).isEqualTo(2);
        assertThat(step.getPartitionTotal()).isEqualTo(4);
    }

    @Test
    void partitionTotalIsABoundParameterOnReportStep(final JenkinsRule r)
            throws Exception {
        final DescribableModel<SentinelReportStep> model =
                new DescribableModel<>(SentinelReportStep.class);
        final SentinelReportStep step = model.instantiate(
                Map.of("partitionTotal", 4));
        assertThat(step.getPartitionTotal()).isEqualTo(4);
    }

    @Test
    void runStepPassesLiteralPartitionArgsToSentinel(final JenkinsRule r)
            throws Exception {
        final String fake = writeFakeSentinel(r);
        final WorkflowJob job =
                r.createProject(WorkflowJob.class, "literal");
        job.setDefinition(new CpsFlowDefinition(pipelineScript(fake, "",
                "    sentinelRun(partitionIndex: 2, partitionTotal: 4)\n"),
                true));
        final WorkflowRun run = r.buildAndAssertSuccess(job);
        r.assertLogContains("--partition=2/4", run);
    }

    @Test
    void singleSourceParallelPassesPartitionArgs(final JenkinsRule r)
            throws Exception {
        final String fake = writeFakeSentinel(r);
        final WorkflowJob job =
                r.createProject(WorkflowJob.class, "range");
        job.setDefinition(new CpsFlowDefinition(pipelineScript(fake, "",
                twoBranchParallelBody()), true));
        final WorkflowRun run = r.buildAndAssertSuccess(job);
        r.assertLogContains("--partition=1/2", run);
        r.assertLogContains("--partition=2/2", run);
    }

    @Test
    void matrixStringValueCastsToPartitionIndex(final JenkinsRule r)
            throws Exception {
        final String fake = writeFakeSentinel(r);
        final WorkflowJob job =
                r.createProject(WorkflowJob.class, "cast");
        job.setDefinition(new CpsFlowDefinition(
                pipelineScript(fake, ", 'PARTITION=3'",
                "    sentinelRun(partitionIndex: "
                + "env.PARTITION.toInteger(), partitionTotal: 4)\n"), true));
        final WorkflowRun run = r.buildAndAssertSuccess(job);
        r.assertLogContains("--partition=3/4", run);
    }

    @Test
    void runStepWarnsOnUnknownSentinelVariable(final JenkinsRule r)
            throws Exception {
        final String fake = writeFakeSentinel(r);
        final WorkflowJob job =
                r.createProject(WorkflowJob.class, "unknownvar");
        job.setDefinition(new CpsFlowDefinition(
                pipelineScript(fake, ", 'SENTINEL_TIMOUT=300'",
                "    sentinelRun()\n"), true));
        final WorkflowRun run = r.buildAndAssertSuccess(job);
        r.assertLogContains("SENTINEL_TIMOUT", run);
    }

    @Test
    void parallelPartitionsShareDerivedSeed(final JenkinsRule r)
            throws Exception {
        final String fake = writeFakeSentinel(r);
        final WorkflowJob job =
                r.createProject(WorkflowJob.class, "sharedseed");
        job.setDefinition(new CpsFlowDefinition(pipelineScript(fake, "",
                twoBranchParallelBody()), true));
        final WorkflowRun run = r.buildAndAssertSuccess(job);

        final long expected =
                SentinelSeed.deriveFrom(run.getExternalizableId());
        final String log = r.getLog(run);
        assertThat(StringUtils.countMatches(
                log, "Using generated seed: " + expected))
                .isEqualTo(2);

        final Matcher m = Pattern.compile("--seed=(\\d+)").matcher(log);
        final Set<String> seeds = new HashSet<>();
        int occurrences = 0;
        while (m.find()) {
            seeds.add(m.group(1));
            occurrences++;
        }
        assertThat(seeds)
                .containsExactly(String.valueOf(expected));
        assertThat(occurrences).isGreaterThanOrEqualTo(2);
    }

    @Test
    void explicitSeedSuppressesDerivedSeed(final JenkinsRule r)
            throws Exception {
        final String fake = writeFakeSentinel(r);
        final WorkflowJob job =
                r.createProject(WorkflowJob.class, "pinnedseed");
        job.setDefinition(new CpsFlowDefinition(
                pipelineScript(fake, ", 'SENTINEL_SEED=12345'",
                "    sentinelRun()\n"), true));
        final WorkflowRun run = r.buildAndAssertSuccess(job);
        r.assertLogContains("--seed=12345", run);
        r.assertLogNotContains("Using generated seed", run);
    }

    /**
     * Wraps a pipeline body in a {@code node { withEnv(...) { ... } }} block
     * with {@code SENTINEL_PATH} pointing at the fake sentinel, plus any
     * extra environment entries.
     *
     * @param fake     path to the fake sentinel executable
     * @param extraEnv extra withEnv entries (e.g. {@code ", 'PARTITION=3'"})
     *                 or an empty string
     * @param body     the pipeline steps to run inside withEnv
     * @return the full pipeline script
     */
    private static String pipelineScript(final String fake,
                                         final String extraEnv,
                                         final String body) {
        return "node {\n"
                + "  withEnv(['SENTINEL_PATH=" + fake + "'" + extraEnv
                + "]) {\n"
                + body
                + "  }\n"
                + "}\n";
    }

    /**
     * Two-branch parallel body using the C-style loop recipe from the
     * README (a Groovy IntRange cannot be serialized by the CPS
     * engine, so the recipe must not use ranges).
     *
     * @return pipeline body running sentinelRun in two parallel branches
     */
    private static String twoBranchParallelBody() {
        return "    def n = 2\n"
                + "    def branches = [:]\n"
                + "    for (int i = 1; i <= n; i++) {\n"
                + "      int idx = i\n"
                + "      branches[\"P${idx}\"] = "
                + "{ sentinelRun(partitionIndex: idx, partitionTotal: n) }\n"
                + "    }\n"
                + "    parallel branches\n";
    }

    private static String writeFakeSentinel(final JenkinsRule r)
            throws Exception {
        final File script = r.jenkins.getRootDir().toPath()
                .resolve("fake-sentinel.sh").toFile();
        Files.writeString(script.toPath(),
                "#!/bin/sh\n"
                + "for a in \"$@\"; do\n"
                + "  case \"$a\" in\n"
                + "    --workspace=*) mkdir -p \"${a#--workspace=}\" ;;\n"
                + "  esac\n"
                + "done\n"
                + "echo \"FAKE_SENTINEL $*\"\n"
                + "exit 0\n",
                StandardCharsets.UTF_8);
        if (!script.setExecutable(true)) {
            throw new IllegalStateException(
                    "could not mark fake sentinel executable");
        }
        return script.getAbsolutePath();
    }
}
