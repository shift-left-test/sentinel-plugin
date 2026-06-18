/*
 * Copyright (c) 2026 LG Electronics, Inc. Licensed under the MIT License.
 * SPDX-License-Identifier: MIT
 */

package io.jenkins.plugins.sentinel;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

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
        job.setDefinition(new CpsFlowDefinition(
                "node {\n"
                + "  withEnv(['SENTINEL_PATH=" + fake + "']) {\n"
                + "    sentinelRun(partitionIndex: 2, partitionTotal: 4)\n"
                + "  }\n"
                + "}\n", true));
        final WorkflowRun run = r.buildAndAssertSuccess(job);
        r.assertLogContains("--partition=2/4", run);
    }

    @Test
    void singleSourceParallelPassesPartitionArgs(final JenkinsRule r)
            throws Exception {
        final String fake = writeFakeSentinel(r);
        final WorkflowJob job =
                r.createProject(WorkflowJob.class, "range");
        job.setDefinition(new CpsFlowDefinition(
                "node {\n"
                + "  withEnv(['SENTINEL_PATH=" + fake + "']) {\n"
                + "    def n = 2\n"
                + "    def branches = [:]\n"
                + "    for (int i = 1; i <= n; i++) {\n"
                + "      int idx = i\n"
                + "      branches[\"P${idx}\"] = "
                + "{ sentinelRun(partitionIndex: idx, partitionTotal: n) }\n"
                + "    }\n"
                + "    parallel branches\n"
                + "  }\n"
                + "}\n", true));
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
                "node {\n"
                + "  withEnv(['SENTINEL_PATH=" + fake + "',"
                + " 'PARTITION=3']) {\n"
                + "    sentinelRun(partitionIndex: "
                + "env.PARTITION.toInteger(), partitionTotal: 4)\n"
                + "  }\n"
                + "}\n", true));
        final WorkflowRun run = r.buildAndAssertSuccess(job);
        r.assertLogContains("--partition=3/4", run);
    }

    private static String writeFakeSentinel(final JenkinsRule r)
            throws Exception {
        final File script =
                new File(r.jenkins.getRootDir(), "fake-sentinel.sh");
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
